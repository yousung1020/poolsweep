-- PostgreSQL 컨테이너가 처음 기동할 때 한 번 실행된다(/docker-entrypoint-initdb.d).
-- 1만 행, 행당 payload 100바이트로 약 1MB다. DB 메모리에 모두 올라가 디스크 I/O가 측정에서 빠진다(RESEARCH.md 6.2).
-- payload는 id로부터 결정적으로 만들어 실행마다 같은 데이터가 되게 한다.
CREATE TABLE item (
    id      bigint PRIMARY KEY,
    payload text   NOT NULL
);

INSERT INTO item (id, payload)
SELECT g, rpad(md5(g::text), 100, 'x')
FROM generate_series(1, 10000) AS g;

ANALYZE item;
