package study.metrics

object InfluxConfig {
    const val URL = "http://localhost:8086"
    const val TOKEN = "study-token"
    const val ORG = "study"

    const val BUCKET_RAW = "metrics_raw"
    const val BUCKET_1M = "metrics_1m"
    const val BUCKET_1H = "metrics_1h"
    const val BUCKET_CARDINALITY_LAB = "cardinality_lab"
}
