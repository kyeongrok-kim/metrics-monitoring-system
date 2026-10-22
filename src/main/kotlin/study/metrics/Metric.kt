package study.metrics

import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import java.time.Instant

/**
 * 책 표 5.2 시계열 데이터 모델
 * - 지표 이름: 문자열
 * - 태그/레이블 집합: <키:값> 쌍의 리스트
 * - 지표 값과 타임스탬프
 */
data class Metric(
    val name: String,
    val labels: Map<String, String>,
    val value: Double,
    val timestamp: Instant,
) {
    fun toPoint(): Point =
        Point.measurement(this.name)
            .addTags(this.labels)
            .addField("value", this.value)
            .time(this.timestamp, WritePrecision.MS)
}

fun log(component: String, message: String) {
    println("[${Instant.now().toString().substring(11, 19)}] [%-10s] %s".format(component, message))
}
