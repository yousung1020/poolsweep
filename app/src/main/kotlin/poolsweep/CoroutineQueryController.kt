package poolsweep

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// suspend 핸들러는 MVC 비동기 처리를 거친다. 톰캣 스레드는 withContext에서 반납되고, JDBC 호출은 IO 디스패처 스레드가
// DB 대기 동안 붙잡는다. 이 차이는 코루틴 모델의 본질로 보고 그대로 둔다(RESEARCH.md 6.1, D3).
@RestController
@Profile("coroutine")
class CoroutineQueryController(
    private val repository: ItemRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @GetMapping("/query")
    suspend fun query(@RequestParam type: String, @RequestParam id: Long): ResponseEntity<Item> {
        val queryType = QueryType.fromParam(type)
        val item = withContext(ioDispatcher) { repository.find(queryType, id) }
        return ResponseEntity.ofNullable(item)
    }
}

@Configuration
@Profile("coroutine")
class IoDispatcherConfig {
    // IO 병렬도는 실험 변수다(RESEARCH.md 6.3). limitedParallelism(n) 뷰는 IO의 기본 상한 64에 묶이지 않으므로
    // n이 그대로 동시 JDBC 호출 수의 상한이 된다(RESEARCH.md 10.1 (3), 로컬 확인).
    @Bean
    fun ioDispatcher(@Value("\${poolsweep.io-parallelism}") parallelism: String): CoroutineDispatcher =
        if (parallelism == "default") Dispatchers.IO else Dispatchers.IO.limitedParallelism(parallelism.toInt())
}
