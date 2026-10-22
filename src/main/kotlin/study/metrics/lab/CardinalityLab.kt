package study.metrics.lab

import study.metrics.InfluxConfig
import study.metrics.Metric
import study.metrics.log
import study.metrics.query.QueryService
import study.metrics.storage.MetricsRepository
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * 책 168p: "각 레이블이 가질 수 있는 값의 가짓수(cardinality)가 낮아야 한다"
 *
 * 같은 요청 1,000건을 두 방식으로 기록해 고유 시계열 개수를 비교한다.
 * - good: 레이블 = host(6가지)          → 시계열 6개
 * - bad : 레이블 = host + requestId(UUID) → 요청마다 새 시계열 = 1,000개
 */
class CardinalityLab(
    private val repository: MetricsRepository,
    private val queryService: QueryService,
) {
    suspend fun run(requests: Int = 1_000) {
        val hosts = listOf("web01", "web02", "web03", "api01", "api02", "api03")
        val now = Instant.now()

        val good = (0 until requests).map { i ->
            Metric("http.latency.good", mapOf("host" to hosts[i % hosts.size]),
                Random.nextDouble(10.0, 200.0), now.minusMillis(i.toLong()))
        }
        val bad = (0 until requests).map { i ->
            Metric("http.latency.bad", mapOf("host" to hosts[i % hosts.size], "requestId" to UUID.randomUUID().toString()),
                Random.nextDouble(10.0, 200.0), now.minusMillis(i.toLong()))
        }

        this.repository.write(good, InfluxConfig.BUCKET_CARDINALITY_LAB)
        this.repository.write(bad, InfluxConfig.BUCKET_CARDINALITY_LAB)

        val goodSeries = this.queryService.seriesCardinality(InfluxConfig.BUCKET_CARDINALITY_LAB, "http.latency.good")
        val badSeries = this.queryService.seriesCardinality(InfluxConfig.BUCKET_CARDINALITY_LAB, "http.latency.bad")

        log("lab", "요청 ${requests}건 기록 결과 (누적 기준)")
        log("lab", "  good (host만 레이블)          → 고유 시계열 ${goodSeries}개")
        log("lab", "  bad  (host + requestId 레이블) → 고유 시계열 ${badSeries}개")
        log("lab", "→ requestId처럼 값이 매번 바뀌는 데이터는 레이블(tag)이 아니라 필드(field)로 넣어야 한다")
    }
}
