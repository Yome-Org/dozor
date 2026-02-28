package com.yome.dozor.engine

interface AlertDeliveryRecorder {
  fun record(records: List<AlertDeliveryRecord>)
}
