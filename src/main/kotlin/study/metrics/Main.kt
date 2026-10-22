package study.metrics

import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import study.metrics.alert.AlertManager
import study.metrics.alert.AlertRule
import study.metrics.alert.ConsoleNotifier
import study.metrics.alert.Notifier
import study.metrics.alert.Severity
import study.metrics.alert.WebhookNotifier
import study.metrics.collector.MetricsCollector
import study.metrics.lab.CardinalityLab
import study.metrics.query.QueryService
import study.metrics.source.ServerSimulator
import study.metrics.storage.MetricsRepository
import kotlin.time.Duration.Companion.seconds

/**
 * 실행 모드
 *   ./gradlew run                          → 모니터링 파이프라인 150초 실행
 *   ./gradlew run --args="monitor 200"     → 실행 시간(초) 지정
 *   ./gradlew run --args="cardinality"     → 레이블 cardinality 실험
 */
fun main(args: Array<String>) = runBlocking {
    val client = InfluxDBClientKotlinFactory.create(
        InfluxConfig.URL, InfluxConfig.TOKEN.toCharArray(), InfluxConfig.ORG, InfluxConfig.BUCKET_RAW,
    )
    client.use {
        val repository = MetricsRepository(it)
        val queryService = QueryService(repository)

        when (args.firstOrNull() ?: "monitor") {
            "cardinality" -> CardinalityLab(repository, queryService).run()
            "monitor" -> runMonitoring(repository, queryService, durationSeconds = args.getOrNull(1)?.toLong() ?: 150)
            else -> println("알 수 없는 모드: ${args.first()} (monitor | cardinality)")
        }
    }
}

/**
 * 책 그림 5.6 개략적 설계안을 축소 구현
 *   지표 출처(ServerSimulator) → [전송 큐] → 지표 수집기 → 시계열 DB ← 질의 서비스 ← 경보 관리자 → 알림 채널
 */
private suspend fun runMonitoring(repository: MetricsRepository, queryService: QueryService, durationSeconds: Long) = coroutineScope {
    val fleet = ServerSimulator.fleet()
    val collector = MetricsCollector(repository)
    val notifiers = buildList<Notifier> {
        add(ConsoleNotifier())
        System.getenv("ALERT_WEBHOOK_URL")?.let { url -> add(WebhookNotifier(url)) }
    }
    val alertManager = AlertManager(
        queryService,
        rules = listOf(
            AlertRule("HighCpuLoad", "cpu.load", threshold = 0.8, windowSeconds = 15, forEvaluations = 2, severity = Severity.CRITICAL),
        ),
        notifiers = notifiers,
    )

    log("main", "시작: 서버 ${fleet.size}대 × 지표 4종, 1초 간격 수집, ${durationSeconds}초 실행")
    log("main", "web01은 20~80초 구간에 CPU 과부하 시나리오가 주입되어 있음")

    val collectorJob = launch { collector.run() }

    // 지표 출처: 1초마다 모든 서버의 지표를 전송 큐로 push
    val sourceJob = launch {
        while (isActive) {
            fleet.flatMap { it.scrape() }.forEach { m -> collector.inbox.send(m) }
            delay(1.seconds)
        }
    }

    // 경보: 5초마다 규칙 평가
    val alertJob = launch {
        delay(5.seconds)
        while (isActive) {
            runCatching { alertManager.evaluate() }.onFailure { e -> log("alert", "평가 실패: ${e.message}") }
            delay(5.seconds)
        }
    }

    // 시각화 대용: 30초마다 대시보드 요약 출력
    val dashboardJob = launch {
        delay(10.seconds)
        while (isActive) {
            val cpu = queryService.averagePerHost("cpu.load", 30)
            log("dashboard", "최근 30초 host별 CPU 평균: " + cpu.entries.joinToString { (h, v) -> "$h=%.2f".format(v) })
            delay(30.seconds)
        }
    }

    delay(durationSeconds.seconds)
    sourceJob.cancel()
    alertJob.cancel()
    dashboardJob.cancel()
    collector.inbox.close()
    collectorJob.join()

    printReport(queryService, alertManager, collector.totalWritten)
}

private suspend fun printReport(queryService: QueryService, alertManager: AlertManager, written: Int) {
    println()
    println("==================== 실행 리포트 ====================")
    println("기록한 데이터 포인트: ${written}건")
    println("경보: FIRING ${alertManager.firedCount}회 / RESOLVED ${alertManager.resolvedCount}회")

    println()
    println("[책 164p 사례 2] 최근 10분 us-west 서버 CPU 평균")
    val usWest = queryService.averageByLabel("cpu.load", "region", "us-west", 600)
    println("  us-west cpu.load avg = ${usWest?.let { "%.3f".format(it) } ?: "데이터 없음"}")

    println()
    println("[책 166p] web01 CPU의 지수 이동 평균(10초 버킷, n=3) — 최근 8개")
    queryService.exponentialMovingAverage("cpu.load", "web01", n = 3, rangeSeconds = 600)
        .takeLast(8)
        .forEach { (t, v) -> println("  ${t.toString().substring(11, 19)}  EMA=%.3f".format(v)) }

    println()
    println("[책 161p 보관 정책] metrics_1m 버킷 다운샘플링 결과 (web01 cpu.load)")
    val downsampled = queryService.downsampled("cpu.load", "web01", 3600)
    if (downsampled.isEmpty()) {
        println("  아직 없음 — 1분 Task는 매 분 정각에 실행되므로 잠시 뒤 다시 조회하면 보인다")
    } else {
        downsampled.forEach { (t, agg, v) -> println("  ${t.toString().substring(11, 19)}  %-4s = %.3f".format(agg, v)) }
    }
    println("=====================================================")
}
