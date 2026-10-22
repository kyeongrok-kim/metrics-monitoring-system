option task = {name: "downsample-raw-to-1m", every: 1m}

data = from(bucket: "metrics_raw")
    |> range(start: -task.every)
    |> filter(fn: (r) => r._field == "value")

// 평균만 남기면 스파이크가 사라지므로 max도 함께 보존한다
data
    |> aggregateWindow(every: 1m, fn: mean, createEmpty: false)
    |> set(key: "agg", value: "mean")
    |> to(bucket: "metrics_1m")

data
    |> aggregateWindow(every: 1m, fn: max, createEmpty: false)
    |> set(key: "agg", value: "max")
    |> to(bucket: "metrics_1m")
