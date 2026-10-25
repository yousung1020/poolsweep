package poolsweep

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// DB 없이 검증할 수 있는 실험 타당성 조건을 고정한다. HikariCP는 첫 getConnection 전까지 DB에 연결하지 않으므로
// /query의 실제 조회는 여기서 다루지 않는다(#3에서 로컬 PostgreSQL로 확인).
class ProcessingModelProfileTest {
    private val baseProperties = mapOf<String, Any>(
        "server.port" to 0,
        "DB_URL" to "jdbc:postgresql://localhost:1/unused",
        "DB_USER" to "unused",
        "DB_PASSWORD" to "unused",
        "POOL_SIZE" to 4,
    )

    private fun start(vararg profiles: String, properties: Map<String, Any> = baseProperties): ConfigurableApplicationContext =
        SpringApplicationBuilder(PoolsweepApplication::class.java)
            .profiles(*profiles)
            .properties(properties)
            .run()

    private fun get(context: ConfigurableApplicationContext, path: String): HttpResponse<String> {
        val port = context.environment.getProperty("local.server.port")
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["platform", "virtual", "coroutine"])
    fun `each profile registers only its own query controller and records its settings`(profile: String) {
        start(profile).use { context ->
            val blocking = context.getBeansOfType(BlockingQueryController::class.java).size
            val coroutine = context.getBeansOfType(CoroutineQueryController::class.java).size
            assertEquals(if (profile == "coroutine") 0 to 1 else 1 to 0, blocking to coroutine)

            val info = get(context, "/actuator/info").body()
            assertTrue(""""processingModel":"$profile"""" in info, info)
            assertTrue(""""poolSize":4""" in info, info)
            assertTrue(""""virtualThreadsEnabled":${profile == "virtual"}""" in info, info)

            assertEquals("pong", get(context, "/ping").body())
            assertEquals(400, get(context, "/query?type=unknown&id=1").statusCode())
        }
    }

    @Test
    fun `coroutine profile uses Dispatchers IO by default and a limited view when parallelism is given`() {
        start("coroutine").use { context ->
            assertEquals(Dispatchers.IO, context.getBean(CoroutineDispatcher::class.java))
        }
        start("coroutine", properties = baseProperties + ("IO_PARALLELISM" to 128)).use { context ->
            assertNotEquals(Dispatchers.IO, context.getBean(CoroutineDispatcher::class.java))
            assertTrue(""""ioParallelism":"128"""" in get(context, "/actuator/info").body())
        }
    }

    @Test
    fun `startup fails without exactly one processing model profile`() {
        assertFailsWith<Exception> { start().close() }
        assertFailsWith<Exception> { start("platform", "virtual").close() }
    }

    @Test
    fun `startup fails when pool size is not given`() {
        assertFailsWith<Exception> { start("platform", properties = baseProperties - "POOL_SIZE").close() }
    }
}
