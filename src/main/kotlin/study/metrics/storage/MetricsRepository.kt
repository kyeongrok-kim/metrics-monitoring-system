package study.metrics.storage

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.query.FluxRecord
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.toList
import study.metrics.InfluxConfig
import study.metrics.Metric

/**
 * 시계열 데이터베이스(time-series database) 접근 계층.
 * 단순 쓰기/조회만 담당하고, 질의 조합은 QueryService가 맡는다.
 */
class MetricsRepository(private val client: InfluxDBClientKotlin) {

    suspend fun write(metrics: List<Metric>, bucket: String = InfluxConfig.BUCKET_RAW) {
        this.client.getWriteKotlinApi().writePoints(metrics.map { it.toPoint() }, bucket = bucket)
    }

    suspend fun query(flux: String): List<FluxRecord> =
        this.client.getQueryKotlinApi().query(flux).consumeAsFlow().toList()
}
