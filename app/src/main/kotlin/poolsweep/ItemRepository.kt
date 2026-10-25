package poolsweep

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

// 세 프로파일이 공유하는 유일한 DB 접근 경로다. 여기를 바꾸면 모든 처리 모델에 똑같이 적용된다(CLAUDE.md 2장).
// 블로킹 호출이며, 어느 스레드에서 부를지는 각 프로파일의 컨트롤러가 정한다.
@Repository
class ItemRepository(private val jdbcTemplate: JdbcTemplate) {
    fun find(type: QueryType, id: Long): Item? =
        jdbcTemplate.query(type.sql, { rs, _ -> Item(rs.getLong("id"), rs.getString("payload")) }, id)
            .firstOrNull()
}
