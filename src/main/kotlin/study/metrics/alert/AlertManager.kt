package study.metrics.alert

import study.metrics.log
import study.metrics.query.QueryService

/**
 * 경보 관리자: 주기적으로 질의 서비스를 호출해 규칙을 평가한다.
 *
 * 상태 전이 (rule, host 단위)
 *   OK ──위반──▶ PENDING ──연속 forEvaluations회 위반──▶ FIRING ──정상──▶ OK(RESOLVED 알림)
 *   PENDING 중 정상으로 돌아오면 알림 없이 OK
 *
 * 중복 제거: FIRING 상태에서 계속 위반해도 알림은 전이 시점에 1회만 보낸다.
 */
class AlertManager(
    private val queryService: QueryService,
    private val rules: List<AlertRule>,
    private val notifiers: List<Notifier>,
) {
    private data class Key(val rule: String, val host: String)

    private val states = mutableMapOf<Key, AlertState>()
    private val violationCounts = mutableMapOf<Key, Int>()
    var firedCount = 0
        private set
    var resolvedCount = 0
        private set

    suspend fun evaluate() {
        for (rule in this.rules) {
            val valuesByHost = this.queryService.averagePerHost(rule.metric, rule.windowSeconds)
            for ((host, value) in valuesByHost) {
                transition(rule, host, value)
            }
        }
    }

    private fun transition(rule: AlertRule, host: String, value: Double) {
        val key = Key(rule.name, host)
        val current = this.states[key] ?: AlertState.OK

        if (rule.isViolated(value)) {
            val count = (this.violationCounts[key] ?: 0) + 1
            this.violationCounts[key] = count
            val next = if (count >= rule.forEvaluations) AlertState.FIRING else AlertState.PENDING
            if (current != AlertState.FIRING && next == AlertState.FIRING) {
                this.firedCount++
                publish(AlertEvent(rule, host, value, resolved = false))
            } else if (current == AlertState.OK && next == AlertState.PENDING) {
                log("alert", "PENDING ${rule.name} host=$host (1/${rule.forEvaluations})")
            }
            this.states[key] = next
            return
        }

        this.violationCounts[key] = 0
        if (current == AlertState.FIRING) {
            this.resolvedCount++
            publish(AlertEvent(rule, host, value, resolved = true))
        }
        this.states[key] = AlertState.OK
    }

    private fun publish(event: AlertEvent) = this.notifiers.forEach { it.notify(event) }
}
