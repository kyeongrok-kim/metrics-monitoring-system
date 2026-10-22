package study.metrics.source

import study.metrics.Metric
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * 지표 출처(metrics source): 운영 지표를 만들어 내는 서버 한 대를 흉내 낸다.
 * 책 161p 예시 지표: CPU 사용률, 요청 수, 메모리 사용량, 메시지 큐 내 메시지 수
 *
 * overloadWindow 구간에는 CPU 부하가 0.85~0.98로 치솟아 경보를 유발한다.
 */
class ServerSimulator(
    private val host: String,
    private val pool: String,
    private val region: String,
    private val overloadWindow: ClosedRange<Duration>? = null,
) {
    private val startedAt = Instant.now()

    fun scrape(now: Instant = Instant.now()): List<Metric> {
        val labels = mapOf("host" to this.host, "pool" to this.pool, "region" to this.region)
        val cpu = if (isOverloaded(now)) Random.nextDouble(0.85, 0.98) else Random.nextDouble(0.20, 0.60)

        return listOf(
            Metric("cpu.load", labels, cpu, now),
            Metric("memory.usage", labels, Random.nextDouble(0.40, 0.75), now),
            Metric("requests.per_second", labels, Random.nextDouble(80.0, 120.0) * (1 + cpu), now),
            Metric("mq.message_count", labels, Random.nextInt(0, 50).toDouble(), now),
        )
    }

    private fun isOverloaded(now: Instant): Boolean {
        val window = this.overloadWindow ?: return false
        return Duration.between(this.startedAt, now) in window
    }

    companion object {
        /** 책 기준(풀 1,000개 × 100대)을 학습용으로 축소: 풀 2개 × 3대 */
        fun fleet(): List<ServerSimulator> = listOf(
            ServerSimulator("web01", "web", "us-west", overloadWindow = Duration.ofSeconds(20)..Duration.ofSeconds(80)),
            ServerSimulator("web02", "web", "us-west"),
            ServerSimulator("web03", "web", "us-east"),
            ServerSimulator("api01", "api", "us-west"),
            ServerSimulator("api02", "api", "us-east"),
            ServerSimulator("api03", "api", "us-east"),
        )
    }
}
