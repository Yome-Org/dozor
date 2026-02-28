package com.yome.dozor.persistence

import com.yome.dozor.engine.AlertDeliveryRecord
import com.yome.dozor.engine.AlertDeliveryRecorder
import java.sql.Timestamp
import java.util.UUID

class PostgresAlertDeliveryRecorder(
  private val connectionFactory: JdbcConnectionFactory,
) : AlertDeliveryRecorder {
  override fun record(records: List<AlertDeliveryRecord>) {
    if (records.isEmpty()) return

    connectionFactory.open().use { connection ->
      connection.autoCommit = false
      try {
        connection
          .prepareStatement(
            """
            INSERT INTO alerts (id, incident_id, type, sent_at, channel, delivery_status, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """
              .trimIndent(),
          )
          .use { statement ->
            for (record in records) {
              statement.setObject(1, UUID.randomUUID())
              statement.setObject(2, record.incidentId)
              statement.setShort(3, record.type.code)
              statement.setTimestamp(4, Timestamp.from(record.sentAt))
              statement.setString(5, record.channel)
              statement.setShort(6, record.status.code)
              statement.setString(7, record.errorMessage)
              statement.addBatch()
            }
            statement.executeBatch()
          }
        connection.commit()
      } catch (ex: Exception) {
        connection.rollback()
        throw ex
      } finally {
        connection.autoCommit = true
      }
    }
  }
}
