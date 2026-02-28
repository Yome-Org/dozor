package com.yome.dozor.persistence

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ComponentState
import com.yome.dozor.engine.StateRepository

class PostgresStateRepository(
  private val connectionFactory: JdbcConnectionFactory,
) : StateRepository {
  override fun loadAll(): Map<ComponentId, ComponentState> {
    connectionFactory.open().use { connection ->
      connection.createStatement().use { statement ->
        statement
          .executeQuery(
            """
                    SELECT component_id, state
                    FROM component_state
                    """
              .trimIndent(),
          )
          .use { rs ->
            val result = linkedMapOf<ComponentId, ComponentState>()
            while (rs.next()) {
              result[ComponentId(rs.getObject("component_id", java.util.UUID::class.java))] =
                codeToState(rs.getShort("state"))
            }
            return result
          }
      }
    }
  }

  override fun saveAll(states: Map<ComponentId, ComponentState>) {
    if (states.isEmpty()) return

    connectionFactory.open().use { connection ->
      connection.autoCommit = false
      try {
        connection
          .prepareStatement(
            """
                    INSERT INTO component_state (
                      component_id,
                      state,
                      last_evaluated_at,
                      last_state_change_at,
                      version
                    )
                    VALUES (?, ?, now(), now(), 1)
                    ON CONFLICT (component_id) DO UPDATE SET
                      state = EXCLUDED.state,
                      last_evaluated_at = EXCLUDED.last_evaluated_at,
                      last_state_change_at = CASE
                        WHEN component_state.state <> EXCLUDED.state THEN EXCLUDED.last_state_change_at
                        ELSE component_state.last_state_change_at
                      END,
                      version = component_state.version + 1
                    """
              .trimIndent(),
          )
          .use { statement ->
            for ((componentId, state) in states) {
              statement.setObject(1, componentId.value)
              statement.setShort(2, stateToCode(state))
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
