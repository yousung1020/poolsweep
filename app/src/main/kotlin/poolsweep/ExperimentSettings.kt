package poolsweep

import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

val PROCESSING_MODELS = listOf("platform", "virtual", "coroutine")

// 처리 모델 프로파일이 없거나 둘 이상이면 /query 컨트롤러가 없거나 겹친다. 잘못된 실행을 측정하지 않도록 기동을 막는다.
@Component
class ProcessingModelGuard(environment: Environment) {
    val model: String = environment.activeProfiles.filter { it in PROCESSING_MODELS }.let { active ->
        check(active.size == 1) {
            "exactly one processing model profile must be active (one of $PROCESSING_MODELS), but was $active"
        }
        active.single()
    }
}

// 실행 스크립트가 결과와 함께 저장할 실제 적용 설정을 /actuator/info로 노출한다.
// 환경변수가 아니라 앱이 실제로 쓰는 값을 읽어서, 설정 누락이나 오타가 기록에 드러나게 한다.
@Component
class ExperimentSettingsInfo(
    private val guard: ProcessingModelGuard,
    private val dataSource: HikariDataSource,
    private val environment: Environment,
) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "experiment",
            mapOf(
                "processingModel" to guard.model,
                "poolSize" to dataSource.maximumPoolSize,
                "connectionTimeoutMs" to dataSource.connectionTimeout,
                "minimumIdle" to dataSource.minimumIdle,
                "virtualThreadsEnabled" to environment.getProperty("spring.threads.virtual.enabled", Boolean::class.java, false),
                "tomcatMaxThreads" to environment.getProperty("server.tomcat.threads.max", Int::class.java, 200),
                "ioParallelism" to (environment.getProperty("poolsweep.io-parallelism") ?: "n/a"),
                "javaVersion" to System.getProperty("java.version"),
                "javaVendor" to System.getProperty("java.vendor"),
                "availableProcessors" to Runtime.getRuntime().availableProcessors(),
            ),
        )
    }
}
