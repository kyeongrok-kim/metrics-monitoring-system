#!/bin/bash
# 책 161p 보관 정책: 원본 7일 → 1분 단위 30일 → 1시간 단위 1년
set -e
AUTH="-o ${DOCKER_INFLUXDB_INIT_ORG} -t ${DOCKER_INFLUXDB_INIT_ADMIN_TOKEN}"

influx bucket create -n metrics_1m -r 30d  $AUTH
influx bucket create -n metrics_1h -r 365d $AUTH
influx bucket create -n cardinality_lab -r 1d $AUTH   # cardinality 실험 전용

influx task create -f /tasks/downsample_raw_to_1m.flux $AUTH
influx task create -f /tasks/downsample_1m_to_1h.flux $AUTH
