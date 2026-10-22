package study.metrics.collector

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import study.metrics.Metric
import study.metrics.log
import study.metrics.storage.MetricsRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 지표 수집기(metrics collector) + 데이터 전송(data transmission)
 *
 * - 출처는 Channel(전송 큐)에 지표를 push 한다 (실무에서는 Kafka가 이 자리를 차지함)
 * - 수집기는 batchSize 또는 flushInterval 중 먼저 도달한 조건에서 TSDB에 묶어서 쓴다
 *   → 쓰기가 압도적인 부하(책 165p)에서 요청 수를 줄이기 위함
 * - 쓰기 실패 시 버퍼를 비우지 않고 다음 flush에서 재시도한다 (maxBufferSize 초과분은 버림)
 */
class MetricsCollector(
    private val repository: MetricsRepository,
    private val batchSize: Int = 100,
    private val flushInterval: Duration = 1.seconds,
    private val maxBufferSize: Int = 10_000,
) {
    val inbox = Channel<Metric>(capacity = 1_000)

    private val buffer = mutableListOf<Metric>()
    var totalWritten = 0
        private set

    suspend fun run() {
        var closed = false
        while (!closed) {
            select {
                this@MetricsCollector.inbox.onReceiveCatching { result ->
                    val metric = result.getOrNull()
                    if (metric == null) closed = true else this@MetricsCollector.buffer += metric
                }
                onTimeout(this@MetricsCollector.flushInterval) { flush() }
            }
            if (this.buffer.size >= this.batchSize) flush()
        }
        flush()
        log("collector", "종료: 누적 기록 ${this.totalWritten}건")
    }

    private suspend fun flush() {
        if (this.buffer.isEmpty()) return
        try {
            this.repository.write(this.buffer)
            this.totalWritten += this.buffer.size
            this.buffer.clear()
        } catch (e: Exception) {
            log("collector", "쓰기 실패, 다음 flush에서 재시도 (버퍼 ${this.buffer.size}건): ${e.message}")
            if (this.buffer.size > this.maxBufferSize) {
                val dropped = this.buffer.size - this.maxBufferSize
                repeat(dropped) { this.buffer.removeAt(0) }
                log("collector", "버퍼 초과로 오래된 지표 ${dropped}건 폐기")
            }
        }
    }
}
