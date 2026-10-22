package study.metrics.query

import study.metrics.InfluxConfig
import study.metrics.storage.MetricsRepository
import java.time.Instant

/**
 * 질의 서비스(query service): 시각화·경보가 쓰는 질의를 한곳에 모은다.
 * 좋은 TSDB를 쓰면 이 계층이 하는 일은 Flux 질의 조립 정도로 얇아진다 (책 168p).
 */
class QueryService(private val repository: MetricsRepository) {

    /** 책 164p 사례 2: "지난 N분간 특정 레이블(region 등)을 가진 서버들의 평균값" */
    suspend fun averageByLabel(metric: String, labelKey: String, labelValue: String, rangeSeconds: Long): Double? {
        val flux = """
            from(bucket: "${InfluxConfig.BUCKET_RAW}")
              |> range(start: -${rangeSeconds}s)
              |> filter(fn: (r) => r._measurement == "$metric" and r._field == "value" and r.$labelKey == "$labelValue")
              |> group()
              |> mean()
        """.trimIndent()
        return this.repository.query(flux).firstOrNull()?.value as? Double
    }

    /** 경보 평가용: host별 최근 N초 평균 */
    suspend fun averagePerHost(metric: String, rangeSeconds: Long): Map<String, Double> {
        val flux = """
            from(bucket: "${InfluxConfig.BUCKET_RAW}")
              |> range(start: -${rangeSeconds}s)
              |> filter(fn: (r) => r._measurement == "$metric" and r._field == "value")
              |> group(columns: ["host"])
              |> mean()
        """.trimIndent()
        return this.repository.query(flux)
            .associate { it.getValueByKey("host") as String to it.value as Double }
            .toSortedMap()
    }

    /** 책 166p: SQL로는 까다로운 지수 이동 평균(EMA)이 TSDB에서는 함수 한 줄 */
    suspend fun exponentialMovingAverage(metric: String, host: String, n: Int, rangeSeconds: Long): List<Pair<Instant, Double>> {
        val flux = """
            from(bucket: "${InfluxConfig.BUCKET_RAW}")
              |> range(start: -${rangeSeconds}s)
              |> filter(fn: (r) => r._measurement == "$metric" and r._field == "value" and r.host == "$host")
              |> aggregateWindow(every: 10s, fn: mean, createEmpty: false)
              |> exponentialMovingAverage(n: $n)
        """.trimIndent()
        return this.repository.query(flux).map { it.time!! to it.value as Double }
    }

    /** 다운샘플링 결과 확인: metrics_1m 버킷의 host별 1분 단위 mean/max */
    suspend fun downsampled(metric: String, host: String, rangeSeconds: Long): List<Triple<Instant, String, Double>> {
        val flux = """
            from(bucket: "${InfluxConfig.BUCKET_1M}")
              |> range(start: -${rangeSeconds}s)
              |> filter(fn: (r) => r._measurement == "$metric" and r.host == "$host")
        """.trimIndent()
        return this.repository.query(flux)
            .map { Triple(it.time!!, it.getValueByKey("agg") as String, it.value as Double) }
            .sortedWith(compareBy({ it.first }, { it.second }))
    }

    /** 버킷의 고유 시계열 개수 (책 168p cardinality) */
    suspend fun seriesCardinality(bucket: String, measurement: String): Long {
        val flux = """
            import "influxdata/influxdb"
            influxdb.cardinality(bucket: "$bucket", start: -1d, predicate: (r) => r._measurement == "$measurement")
        """.trimIndent()
        return this.repository.query(flux).firstOrNull()?.value as? Long ?: 0L
    }
}
