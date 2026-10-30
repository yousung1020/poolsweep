// E1(열린 모델) 최소 스크립트: 고정 도착률로 /query를 호출하고, /ping 기준선을 부하 전과 부하 중에 함께 잰다.
// 모든 값은 -e 인자로 받고 기본값을 두지 않는다. 실행 스크립트가 값을 빠뜨리면 init에서 실패해 엉뚱한 설정으로 측정되지 않는다.
// 처리 시간대: [0, PING_PRE) ping_pre만, [PING_PRE, PING_PRE + DURATION) query와 ping_load.
import http from 'k6/http';
import exec from 'k6/execution';
import { Trend, Rate, Counter } from 'k6/metrics';

const QUERY_TYPES = ['pk', 'sleep10', 'sleep50'];
const ID_MAX = 10000; // infra/db/init.sql의 시드 범위(1~10000)

function required(name) {
  const v = __ENV[name];
  if (v === undefined || v === '') throw new Error(`필수 인자 ${name}이(가) 없습니다 (-e ${name}=...)`);
  return v;
}

function positiveInt(name) {
  const v = required(name);
  if (!/^[1-9][0-9]*$/.test(v)) throw new Error(`${name}은(는) 양의 정수여야 합니다: ${v}`);
  return Number(v);
}

const cfg = {
  baseUrl: required('BASE_URL').replace(/\/$/, ''),
  queryType: required('QUERY_TYPE'),
  rate: positiveInt('RATE'), // /query 도착률(건/초)
  durationS: positiveInt('DURATION_S'), // /query 부하 구간 길이
  preAllocatedVUs: positiveInt('PRE_VUS'),
  maxVUs: positiveInt('MAX_VUS'),
  reqTimeoutS: positiveInt('REQ_TIMEOUT_S'), // 요청 타임아웃. /query와 /ping이 같은 값을 쓴다
  seed: positiveInt('SEED'),
  pingRate: positiveInt('PING_RATE'), // /ping 도착률(건/초). 부하 전과 부하 중에 같은 값을 쓴다
  pingPreS: positiveInt('PING_PRE_S'), // 부하 전 /ping 구간 길이
  summaryOut: required('SUMMARY_OUT'),
};
if (!QUERY_TYPES.includes(cfg.queryType)) {
  throw new Error(`QUERY_TYPE은 ${QUERY_TYPES.join('|')} 중 하나여야 합니다: ${cfg.queryType}`);
}
if (cfg.maxVUs < cfg.preAllocatedVUs) throw new Error(`MAX_VUS(${cfg.maxVUs})가 PRE_VUS(${cfg.preAllocatedVUs})보다 작습니다`);
// /ping의 VU 수는 D1(RESEARCH.md 6.5)의 기준인 도착률 × 요청 타임아웃 이상으로 정한다.
// 부하 중에 /ping이 느려져도 VU가 모자라 드롭되지 않게 해서, /ping 지연 증가를 드롭과 섞이지 않게 보려는 것이다.
// 타임아웃된 반복은 타임아웃보다 조금 더 걸려서 딱 맞추면 드롭이 났다(2026-10-29 로컬 확인). 그래서 1초분을 더한다.
cfg.pingVUs = cfg.pingRate * (cfg.reqTimeoutS + 1);

export const options = {
  scenarios: {
    ping_pre: {
      executor: 'constant-arrival-rate',
      exec: 'ping',
      rate: cfg.pingRate,
      timeUnit: '1s',
      duration: `${cfg.pingPreS}s`,
      preAllocatedVUs: cfg.pingVUs,
      maxVUs: cfg.pingVUs,
    },
    query: {
      executor: 'constant-arrival-rate',
      exec: 'query',
      startTime: `${cfg.pingPreS}s`,
      rate: cfg.rate,
      timeUnit: '1s',
      duration: `${cfg.durationS}s`,
      preAllocatedVUs: cfg.preAllocatedVUs,
      maxVUs: cfg.maxVUs,
    },
    ping_load: {
      executor: 'constant-arrival-rate',
      exec: 'ping',
      startTime: `${cfg.pingPreS}s`,
      rate: cfg.pingRate,
      timeUnit: '1s',
      duration: `${cfg.durationS}s`,
      preAllocatedVUs: cfg.pingVUs,
      maxVUs: cfg.pingVUs,
    },
  },
  // 응답 본문은 상태 코드만 보면 되므로 생성기 메모리를 아끼려고 버린다. 서버가 보내는 양은 그대로다.
  discardResponseBodies: true,
  summaryTrendStats: ['count', 'avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)', 'max'],
  // 시나리오별 dropped_iterations와 타임아웃 수가 0일 때도 요약에 0으로 남게 한다. 판정용이 아니므로 항상 통과하는 조건이다.
  thresholds: {
    'dropped_iterations{scenario:ping_pre}': ['count>=0'],
    'dropped_iterations{scenario:query}': ['count>=0'],
    'dropped_iterations{scenario:ping_load}': ['count>=0'],
    query_timeouts: ['count>=0'],
  },
};

// 지연은 http_req_duration(송신 + 대기 + 수신)이다. 대기에는 톰캣 큐와 풀 대기가 들어간다(RESEARCH.md 6.4).
const queryDuration = new Trend('query_duration', true);
const pingPreDuration = new Trend('ping_pre_duration', true);
const pingLoadDuration = new Trend('ping_load_duration', true);
const queryFailed = new Rate('query_failed');
const pingFailed = new Rate('ping_failed');
const queryTimeouts = new Counter('query_timeouts');

const TIMEOUT_ERROR_CODE = 1050; // k6의 request timeout 오류 코드

// id는 (시드, 시나리오 안의 반복 번호)만으로 정한다. 어느 VU가 반복을 맡든 k번째 요청의 id가 같아서 요청 순서가 재현된다.
// lowbias32 정수 해시이다. 2^32를 10000으로 나눈 나머지 편향은 무시할 만하다.
function idFor(seed, iteration) {
  let x = (seed ^ Math.imul(iteration + 1, 0x9e3779b9)) >>> 0;
  x ^= x >>> 16;
  x = Math.imul(x, 0x7feb352d) >>> 0;
  x ^= x >>> 15;
  x = Math.imul(x, 0x846ca68b) >>> 0;
  x ^= x >>> 16;
  return (x >>> 0) % ID_MAX + 1;
}

const params = (name) => ({ timeout: `${cfg.reqTimeoutS}s`, tags: { name } });

export function query() {
  const id = idFor(cfg.seed, exec.scenario.iterationInTest);
  const res = http.get(`${cfg.baseUrl}/query?type=${cfg.queryType}&id=${id}`, params('query'));
  queryDuration.add(res.timings.duration);
  queryFailed.add(res.status !== 200);
  if (res.error_code === TIMEOUT_ERROR_CODE) queryTimeouts.add(1);
}

export function ping() {
  const res = http.get(`${cfg.baseUrl}/ping`, params('ping'));
  if (exec.scenario.name === 'ping_pre') pingPreDuration.add(res.timings.duration);
  else pingLoadDuration.add(res.timings.duration);
  pingFailed.add(res.status !== 200);
}

// 결과 파일에는 받은 설정과 k6가 집계한 지표 전체를 그대로 남긴다. 고르고 가공하는 일은 분석 스크립트가 원자료를 읽어서 한다.
export function handleSummary(data) {
  const m = data.metrics;
  const dropped = (s) => m[`dropped_iterations{scenario:${s}}`]?.values.count ?? 0;
  const p = (name, stat) => m[name]?.values[stat]?.toFixed(2) ?? 'n/a';
  const lines = [
    `query  ${cfg.queryType} ${cfg.rate}/s ${cfg.durationS}s: count=${p('query_duration', 'count')} p50=${p('query_duration', 'med')} p99=${p('query_duration', 'p(99)')} ms, failed=${p('query_failed', 'rate')}, timeouts=${m.query_timeouts?.values.count ?? 0}`,
    `ping   pre p50=${p('ping_pre_duration', 'med')} p99=${p('ping_pre_duration', 'p(99)')} ms, load p50=${p('ping_load_duration', 'med')} p99=${p('ping_load_duration', 'p(99)')} ms`,
    `dropped_iterations ping_pre=${dropped('ping_pre')} query=${dropped('query')} ping_load=${dropped('ping_load')}`,
    `summary -> ${cfg.summaryOut}`,
  ];
  return {
    stdout: lines.join('\n') + '\n',
    [cfg.summaryOut]: JSON.stringify({ config: cfg, state: data.state, metrics: m }, null, 2),
  };
}
