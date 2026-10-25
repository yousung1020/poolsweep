package poolsweep

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

// 세 유형 모두 같은 PK 조회를 하고 DB 점유 시간만 다르게 한다. 쿼리 지연(블로킹 비율)만 변수로 남기기 위해서다(RESEARCH.md 6.3).
// pg_sleep을 FROM 절에 두면 id가 있을 때 요청당 정확히 한 번 실행되고, id가 없으면 실행되지 않는다(EXPLAIN ANALYZE로 확인).
// 따라서 부하 스크립트는 시드 범위(infra/db/init.sql, 1~10000)의 id만 보내야 한다.
enum class QueryType(val param: String, val sql: String) {
    PK("pk", "SELECT id, payload FROM item WHERE id = ?"),
    SLEEP10("sleep10", "SELECT id, payload FROM item, pg_sleep(0.010) WHERE id = ?"),
    SLEEP50("sleep50", "SELECT id, payload FROM item, pg_sleep(0.050) WHERE id = ?");

    companion object {
        fun fromParam(value: String): QueryType =
            entries.firstOrNull { it.param == value }
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown query type: $value")
    }
}

data class Item(val id: Long, val payload: String)
