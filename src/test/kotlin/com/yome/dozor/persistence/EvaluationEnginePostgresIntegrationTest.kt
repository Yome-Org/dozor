package com.yome.dozor.persistence

import com.yome.dozor.domain.ComponentState
import com.yome.dozor.domain.Severity
import com.yome.dozor.domain.Signal
import com.yome.dozor.domain.ThresholdConfig
import com.yome.dozor.engine.AlertChannel
import com.yome.dozor.engine.AlertDeliveryStatus
import com.yome.dozor.engine.DeterministicEvaluationEngine
import com.yome.dozor.engine.ThresholdProvider
import com.yome.dozor.incident.DeterministicIncidentEngine
import com.yome.dozor.propagation.DependencyEdge
import com.yome.dozor.propagation.DependencyGraph
import com.yome.dozor.propagation.DeterministicPropagationEngine
import com.yome.dozor.state.DeterministicStateEvaluator
import com.yome.dozor.support.componentId
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class EvaluationEnginePostgresIntegrationTest {
  companion object {
    @Container
    @JvmStatic
    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
  }

  private lateinit var jdbcConfig: JdbcConfig
  private lateinit var connectionFactory: JdbcConnectionFactory

  @BeforeEach
  fun setup() {
    jdbcConfig =
      JdbcConfig(
        jdbcUrl = postgres.jdbcUrl,
        username = postgres.username,
        password = postgres.password,
      )
    connectionFactory = JdbcConnectionFactory(jdbcConfig)

    FlywayMigrator(jdbcConfig).migrate()
    resetTables()
  }

  @Test
  fun persistsStateIncidentAndAlertAfterEvaluation() {
    val db = componentId("postgres")
    val api = componentId("api")
    val worker = componentId("worker")
    val now = Instant.parse("2026-02-22T12:00:00Z")

    seedComponents(db.value to "postgres", api.value to "api", worker.value to "worker")
    seedDependencies(db.value to api.value, api.value to worker.value)
    seedSignals(
      Signal(db, Severity.CRITICAL, now.minusSeconds(10)),
      Signal(db, Severity.CRITICAL, now.minusSeconds(20)),
      Signal(db, Severity.CRITICAL, now.minusSeconds(30)),
    )

    val graph =
      DependencyGraph.from(
        components = setOf(db, api, worker),
        edges =
          setOf(
            DependencyEdge(db, api),
            DependencyEdge(api, worker),
          ),
      )

    val engine =
      DeterministicEvaluationEngine(
        graph = graph,
        thresholdProvider =
          ThresholdProvider {
            ThresholdConfig(
              criticalThreshold = 3,
              degradedThreshold = 3,
              window = Duration.ofMinutes(5),
              recoveryWindow = Duration.ofMinutes(2),
            )
          },
        signalRepository = PostgresSignalRepository(connectionFactory),
        stateRepository = PostgresStateRepository(connectionFactory),
        incidentRepository = PostgresIncidentRepository(connectionFactory),
        alertPublisher =
          PostgresAlertPublisher(
            connectionFactory,
            channel = AlertChannel.INTERNAL,
            deliveryStatus = AlertDeliveryStatus.SENT,
          ),
        stateEvaluator = DeterministicStateEvaluator(),
        propagationEngine = DeterministicPropagationEngine(),
        incidentEngine = DeterministicIncidentEngine(),
      )

    val result = engine.evaluate(dirtyComponents = setOf(db), now = now)

    assertEquals(ComponentState.CRITICAL, result.effectiveStates[db])
    assertEquals(ComponentState.IMPACTED, result.effectiveStates[api])
    assertEquals(ComponentState.IMPACTED, result.effectiveStates[worker])
    assertEquals(setOf(db), result.rootCauses)

    val persistedStates = PostgresStateRepository(connectionFactory).loadAll()
    assertEquals(ComponentState.CRITICAL, persistedStates[db])
    assertEquals(ComponentState.IMPACTED, persistedStates[api])
    assertEquals(ComponentState.IMPACTED, persistedStates[worker])

    connectionFactory.open().use { connection ->
      connection.createStatement().use { statement ->
        statement.executeQuery("SELECT COUNT(*) AS c FROM incidents WHERE status = 0").use { rs ->
          rs.next()
          assertEquals(1, rs.getInt("c"))
        }
        statement
          .executeQuery(
            "SELECT COUNT(*) AS c FROM alerts WHERE type = 0 AND channel = 'internal' AND delivery_status = 0",
          )
          .use { rs ->
            rs.next()
            assertEquals(1, rs.getInt("c"))
          }
      }
    }
  }

  private fun resetTables() {
    connectionFactory.open().use { connection ->
      connection.createStatement().use { statement ->
        statement.execute(
          """
                    TRUNCATE TABLE alerts, incidents, component_state, signals, dependencies, components
                    RESTART IDENTITY CASCADE
                    """
            .trimIndent(),
        )
      }
    }
  }

  private fun seedComponents(vararg components: Pair<UUID, String>) {
    connectionFactory.open().use { connection ->
      connection
        .prepareStatement(
          """
                INSERT INTO components (id, name, description, created_at)
                VALUES (?, ?, NULL, ?)
                """
            .trimIndent(),
        )
        .use { statement ->
          for ((id, name) in components) {
            statement.setObject(1, id)
            statement.setString(2, name)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.addBatch()
          }
          statement.executeBatch()
        }
    }
  }

  private fun seedDependencies(vararg dependencies: Pair<UUID, UUID>) {
    connectionFactory.open().use { connection ->
      connection
        .prepareStatement(
          """
                INSERT INTO dependencies (upstream_component_id, downstream_component_id)
                VALUES (?, ?)
                """
            .trimIndent(),
        )
        .use { statement ->
          for ((upstream, downstream) in dependencies) {
            statement.setObject(1, upstream)
            statement.setObject(2, downstream)
            statement.addBatch()
          }
          statement.executeBatch()
        }
    }
  }

  private fun seedSignals(vararg signals: Signal) {
    connectionFactory.open().use { connection ->
      connection
        .prepareStatement(
          """
                INSERT INTO signals (id, component_id, severity, source, occurred_at, ingested_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """
            .trimIndent(),
        )
        .use { statement ->
          for (signal in signals) {
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, signal.componentId.value)
            statement.setShort(3, severityToCode(signal.severity))
            statement.setString(4, "integration-test")
            statement.setTimestamp(5, Timestamp.from(signal.occurredAt))
            statement.setTimestamp(6, Timestamp.from(signal.occurredAt))
            statement.setString(7, null)
            statement.addBatch()
          }
          statement.executeBatch()
        }
    }
  }
}
