package com.yome.dozor.persistence

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.engine.IncidentRepository
import com.yome.dozor.incident.Incident
import com.yome.dozor.incident.IncidentTransition

class PostgresIncidentRepository(
  private val connectionFactory: JdbcConnectionFactory,
) : IncidentRepository {
  override fun loadActive(): Map<ComponentId, Incident> {
    connectionFactory.open().use { connection ->
      connection
        .prepareStatement(
          """
                SELECT id, root_component_id, started_at, resolved_at, status
                FROM incidents
                WHERE status = 0
                """
            .trimIndent(),
        )
        .use { statement ->
          statement.executeQuery().use { rs ->
            val result = linkedMapOf<ComponentId, Incident>()
            while (rs.next()) {
              val rootId =
                ComponentId(rs.getObject("root_component_id", java.util.UUID::class.java))
              result[rootId] =
                Incident(
                  id = rs.getObject("id", java.util.UUID::class.java),
                  rootComponentId = rootId,
                  startedAt = rs.getTimestamp("started_at").toInstant(),
                  resolvedAt = rs.getTimestamp("resolved_at")?.toInstant(),
                  status = codeToIncidentStatus(rs.getShort("status")),
                )
            }
            return result
          }
        }
    }
  }

  override fun saveTransition(transition: IncidentTransition) {
    connectionFactory.open().use { connection ->
      connection.autoCommit = false
      try {
        if (transition.opened.isNotEmpty()) {
          connection
            .prepareStatement(
              """
                        INSERT INTO incidents (id, root_component_id, started_at, resolved_at, status)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO NOTHING
                        """
                .trimIndent(),
            )
            .use { statement ->
              for (incident in transition.opened) {
                statement.setObject(1, incident.id)
                statement.setObject(2, incident.rootComponentId.value)
                statement.setTimestamp(3, java.sql.Timestamp.from(incident.startedAt))
                statement.setTimestamp(4, incident.resolvedAt?.let { java.sql.Timestamp.from(it) })
                statement.setShort(5, incidentStatusToCode(incident.status))
                statement.addBatch()
              }
              statement.executeBatch()
            }
        }

        if (transition.resolved.isNotEmpty()) {
          connection
            .prepareStatement(
              """
                        UPDATE incidents
                        SET status = ?, resolved_at = ?
                        WHERE id = ? AND status = 0
                        """
                .trimIndent(),
            )
            .use { statement ->
              for (incident in transition.resolved) {
                statement.setShort(1, incidentStatusToCode(incident.status))
                statement.setTimestamp(2, incident.resolvedAt?.let { java.sql.Timestamp.from(it) })
                statement.setObject(3, incident.id)
                statement.addBatch()
              }
              statement.executeBatch()
            }
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
