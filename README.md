# metrics-monitoring-system

『가상 면접 사례로 배우는 대규모 시스템 설계 기초 2』 5장 **지표 모니터링 및 경보 시스템**(책 159~168p)을
Kotlin + InfluxDB 2.x로 축소 구현한 스터디용 프로젝트.

## 실행

```bash
docker compose up -d                       # InfluxDB + 버킷 3개 + 다운샘플링 Task 자동 생성
./gradlew run                              # 모니터링 파이프라인 150초 실행
./gradlew run --args="monitor 300"         # 실행 시간(초) 지정
./gradlew run --args="cardinality"         # 레이블 cardinality 실험
docker compose down -v                     # 데이터까지 초기화
```

InfluxDB UI: http://localhost:8086 (admin / admin12345) → Data Explorer에서 그래프 확인 가능

## 구조 — 책 그림 5.6 대응

```
ServerSimulator ──push──▶ [Channel: 전송 큐] ──▶ MetricsCollector ──batch write──▶ InfluxDB
 (지표 출처)                (데이터 전송)          (지표 수집기)                    (시계열 DB)
                                                                                     ▲
                       ConsoleNotifier / WebhookNotifier ◀── AlertManager ──▶ QueryService
                              (경보 채널)                     (경보)          (질의 서비스)
```

| 책 내용 | 구현 위치 |
|---|---|
| 표 5.2 시계열 데이터 모델 (이름, 레이블, 값+타임스탬프) | `Metric.kt` |
| 161p 운영 지표 (CPU·메모리·요청 수·MQ 메시지 수) | `source/ServerSimulator.kt` |
| 165p 쓰기 압도적 부하 → 배치 쓰기 | `collector/MetricsCollector.kt` |
| 164p 사례 2: 지역별 CPU 평균 | `QueryService.averageByLabel` |
| 166p SQL로 까다로운 지수 이동 평균 | `QueryService.exponentialMovingAverage` |
| 161p 보관 정책 7일 → 1분 단위 30일 → 1시간 단위 1년 | `docker/init`, `docker/tasks/*.flux` |
| 168p 레이블 cardinality를 낮게 | `lab/CardinalityLab.kt` |
| 161p 경보 채널 (웹훅 등) | `alert/Notifier.kt` (`ALERT_WEBHOOK_URL` 환경변수) |

## 설계 결정

- **전송 큐를 Kafka 대신 Kotlin Channel로 구현**: 학습 목적상 인프라를 InfluxDB 하나로 유지하기 위해서. 책 후반부(Kafka 도입) 학습 시 교체 지점이다.
- **Push 방식 수집**: 코드 흐름이 눈에 보이게 하기 위해서. Pull 방식(Prometheus)과의 비교는 스터디 주제로 남겨 둔다.
- **경보 `forEvaluations`(연속 N회 위반 시 FIRING)**: 순간 스파이크로 인한 오탐을 막는다. Prometheus의 `for` 절과 같은 개념.
- **다운샘플링 시 mean과 max를 함께 보존**: mean만 남기면 스파이크가 사라진다. 1h 집계의 "평균의 평균"은 근사값이라는 점도 Task 주석에 남겼다.

## 실행 결과 예시 (2026-10-22)

- 기록 3,600건 (150초 × 6대 × 4종)
- web01 과부하 구간에서 FIRING 1회 → 회복 후 RESOLVED 1회
- 1분 다운샘플링: 같은 1분 구간에서 `mean=0.568`, `max=0.979` (평균이 스파이크를 가림)
- cardinality: 같은 1,000건 요청 → host 레이블만 쓰면 시계열 6개, requestId 레이블을 추가하면 1,000개
