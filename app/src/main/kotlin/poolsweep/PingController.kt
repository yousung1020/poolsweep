package poolsweep

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// 네트워크와 톰캣·MVC 기본 지연의 기준선이다(RESEARCH.md 6.2). 세 프로파일에서 같은 일반 핸들러로 두어,
// 프로파일 간 /ping 차이는 처리 모델이 아니라 스레드 종류(플랫폼/가상)에서만 나오게 한다.
@RestController
class PingController {
    @GetMapping("/ping")
    fun ping(): String = "pong"
}
