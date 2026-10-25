package poolsweep

import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// platform과 virtual은 같은 핸들러를 쓴다. 둘의 차이는 요청 스레드가 플랫폼 스레드인지 가상 스레드인지뿐이며,
// 그것은 spring.threads.virtual.enabled 설정(프로파일별 application-*.yaml)으로만 바꾼다(RESEARCH.md 7장).
@RestController
@Profile("platform", "virtual")
class BlockingQueryController(private val repository: ItemRepository) {
    @GetMapping("/query")
    fun query(@RequestParam type: String, @RequestParam id: Long): ResponseEntity<Item> {
        val queryType = QueryType.fromParam(type)
        return ResponseEntity.ofNullable(repository.find(queryType, id))
    }
}
