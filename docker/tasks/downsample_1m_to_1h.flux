option task = {name: "downsample-1m-to-1h", every: 1h}

data = from(bucket: "metrics_1m")
    |> range(start: -task.every)
    |> filter(fn: (r) => r._field == "value")

// 주의: "평균의 평균"은 원본 평균과 다를 수 있다 (구간별 샘플 수가 다르면 오차 발생)
data
    |> filter(fn: (r) => r.agg == "mean")
    |> aggregateWindow(every: 1h, fn: mean, createEmpty: false)
    |> to(bucket: "metrics_1h")

data
    |> filter(fn: (r) => r.agg == "max")
    |> aggregateWindow(every: 1h, fn: max, createEmpty: false)
    |> to(bucket: "metrics_1h")
