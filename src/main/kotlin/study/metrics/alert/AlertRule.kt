package study.metrics.alert

/**
 * 경보 규칙. Prometheus 경보 규칙의 핵심 필드만 옮겨 왔다.
 * - window: 평균을 구할 구간
 * - forEvaluations: 조건이 연속 N회 평가 동안 유지돼야 FIRING (순간 스파이크로 인한 오탐 방지)
 */
data class AlertRule(
    val name: String,
    val metric: String,
    val threshold: Double,
    val windowSeconds: Long,
    val forEvaluations: Int,
    val severity: Severity,
) {
    fun isViolated(value: Double): Boolean = value > this.threshold
}

enum class Severity { WARNING, CRITICAL }

enum class AlertState { OK, PENDING, FIRING }

data class AlertEvent(
    val rule: AlertRule,
    val host: String,
    val value: Double,
    val resolved: Boolean,
)
