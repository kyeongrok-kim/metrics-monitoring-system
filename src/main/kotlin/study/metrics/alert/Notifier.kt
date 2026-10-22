package study.metrics.alert

import study.metrics.log
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** 경보 채널. 책 161p 요구사항: 이메일, 전화, PagerDuty, 웹훅 */
interface Notifier {
    fun notify(event: AlertEvent)
}

class ConsoleNotifier : Notifier {
    override fun notify(event: AlertEvent) {
        val status = if (event.resolved) "✅ RESOLVED" else "🚨 FIRING"
        log("notifier", "$status [${event.rule.severity}] ${event.rule.name} host=${event.host} " +
            "value=%.3f threshold=${event.rule.threshold}".format(event.value))
    }
}

/** ALERT_WEBHOOK_URL 환경변수가 있을 때만 HTTP POST 발송 */
class WebhookNotifier(private val url: String) : Notifier {
    private val http = HttpClient.newHttpClient()

    override fun notify(event: AlertEvent) {
        val body = """{"rule":"${event.rule.name}","host":"${event.host}","value":${event.value},"resolved":${event.resolved}}"""
        val request = HttpRequest.newBuilder(URI.create(this.url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        runCatching { this.http.send(request, HttpResponse.BodyHandlers.discarding()) }
            .onFailure { log("notifier", "웹훅 발송 실패: ${it.message}") }
    }
}
