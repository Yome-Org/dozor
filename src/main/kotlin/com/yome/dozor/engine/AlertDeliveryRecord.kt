package com.yome.dozor.engine

import java.time.Instant
import java.util.UUID

data class AlertDeliveryRecord(
  val incidentId: UUID,
  val type: AlertType,
  val sentAt: Instant,
  val channel: String,
  val status: AlertDeliveryStatus,
  val errorMessage: String? = null,
)
