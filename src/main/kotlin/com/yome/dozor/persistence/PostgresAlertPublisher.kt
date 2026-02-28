package com.yome.dozor.persistence

import com.yome.dozor.engine.AlertDeliveryRecord
import com.yome.dozor.engine.AlertDeliveryStatus
import com.yome.dozor.engine.AlertPublisher
import com.yome.dozor.engine.AlertType
import com.yome.dozor.incident.IncidentTransition
import java.time.Instant

class PostgresAlertPublisher(
  connectionFactory: JdbcConnectionFactory,
  private val channel: String,
  private val deliveryStatus: AlertDeliveryStatus = AlertDeliveryStatus.SENT,
  private val errorMessage: String? = null,
) : AlertPublisher {
  private val deliveryRecorder = PostgresAlertDeliveryRecorder(connectionFactory)

  override fun publish(
    transition: IncidentTransition,
    now: Instant,
  ) {
    val records =
      transition.opened.map {
        AlertDeliveryRecord(
          incidentId = it.id,
          type = AlertType.OPEN,
          sentAt = now,
          channel = channel,
          status = deliveryStatus,
          errorMessage = errorMessage,
        )
      } +
        transition.resolved.map {
          AlertDeliveryRecord(
            incidentId = it.id,
            type = AlertType.RESOLVED,
            sentAt = now,
            channel = channel,
            status = deliveryStatus,
            errorMessage = errorMessage,
          )
        }

    deliveryRecorder.record(records)
  }
}
