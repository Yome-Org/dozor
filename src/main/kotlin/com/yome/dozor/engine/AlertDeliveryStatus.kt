package com.yome.dozor.engine

enum class AlertDeliveryStatus(
  val code: Short,
) {
  SENT(0),
  FAILED(1),
}
