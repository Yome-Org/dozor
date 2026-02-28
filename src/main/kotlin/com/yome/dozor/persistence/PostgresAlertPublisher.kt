package com.yome.dozor.persistence

import com.yome.dozor.engine.AlertPublisher
import com.yome.dozor.incident.IncidentTransition
import java.time.Instant
import java.util.UUID

class PostgresAlertPublisher(
  private val connectionFactory: JdbcConnectionFactory,
  private val channel: String,
) : AlertPublisher {
  override fun publish(
    transition: IncidentTransition,
    now: Instant,
  ) {
    val open = transition.opened.map { 0 to it.id }
    val resolved = transition.resolved.map { 1 to it.id }
    val items = open + resolved

    if (items.isEmpty()) return

    connectionFactory.open().use { connection ->
      connection.autoCommit = false
      try {
        connection
          .prepareStatement(
            """
                    INSERT INTO alerts (id, incident_id, type, sent_at, channel)
                    VALUES (?, ?, ?, ?, ?)
                    """
              .trimIndent(),
          )
          .use { statement ->
            for ((type, incidentId) in items) {
              statement.setObject(1, UUID.randomUUID())
              statement.setObject(2, incidentId)
              statement.setShort(3, type.toShort())
              statement.setTimestamp(4, java.sql.Timestamp.from(now))
              statement.setString(5, channel)
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
