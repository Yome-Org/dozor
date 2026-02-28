package com.yome.dozor.persistence

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.Signal
import com.yome.dozor.engine.SignalAppendResult
import com.yome.dozor.engine.SignalIngestionRepository
import com.yome.dozor.engine.SignalRepository
import java.time.Instant
import java.util.UUID

class PostgresSignalRepository(
  private val connectionFactory: JdbcConnectionFactory,
) : SignalRepository, SignalIngestionRepository {
  override fun findByComponent(componentId: ComponentId): List<Signal> {
    connectionFactory.open().use { connection ->
      connection
        .prepareStatement(
          """
                SELECT component_id, severity, occurred_at
                FROM signals
                WHERE component_id = ?
                ORDER BY occurred_at DESC
                """
            .trimIndent(),
        )
        .use { statement ->
          statement.setObject(1, componentId.value)
          statement.executeQuery().use { rs ->
            val result = mutableListOf<Signal>()
            while (rs.next()) {
              result +=
                Signal(
                  componentId =
                    ComponentId(rs.getObject("component_id", java.util.UUID::class.java)),
                  severity = codeToSeverity(rs.getShort("severity")),
                  occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                )
            }
            return result
          }
        }
    }
  }

  override fun append(
    signal: Signal,
    source: String,
    ingestedAt: Instant,
    idempotencyKey: String?,
  ): SignalAppendResult {
    connectionFactory.open().use { connection ->
      connection
        .prepareStatement(
          """
                INSERT INTO signals (id, component_id, severity, source, occurred_at, ingested_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """
            .trimIndent(),
        )
        .use { statement ->
          statement.setObject(1, UUID.randomUUID())
          statement.setObject(2, signal.componentId.value)
          statement.setShort(3, severityToCode(signal.severity))
          statement.setString(4, source)
          statement.setTimestamp(5, java.sql.Timestamp.from(signal.occurredAt))
          statement.setTimestamp(6, java.sql.Timestamp.from(ingestedAt))
          statement.setString(7, idempotencyKey)
          return if (statement.executeUpdate() > 0) SignalAppendResult.INSERTED
          else SignalAppendResult.DUPLICATE
        }
    }
  }
}
