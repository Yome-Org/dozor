package com.yome.dozor.bootstrap

import com.yome.dozor.domain.Component
import com.yome.dozor.persistence.JdbcConnectionFactory
import com.yome.dozor.propagation.DependencyEdge

class PostgresTopologySeeder(
  private val connectionFactory: JdbcConnectionFactory,
) {
  fun seed(
    components: Collection<Component>,
    edges: Collection<DependencyEdge>,
  ) {
    connectionFactory.open().use { connection ->
      connection.autoCommit = false
      try {
        connection
          .prepareStatement(
            """
                    INSERT INTO components (id, name, description, created_at)
                    VALUES (?, ?, NULL, now())
                    ON CONFLICT (id) DO NOTHING
                    """
              .trimIndent(),
          )
          .use { statement ->
            for (component in components) {
              statement.setObject(1, component.id.value)
              statement.setString(2, component.name)
              statement.addBatch()
            }
            statement.executeBatch()
          }

        connection
          .prepareStatement(
            """
                    INSERT INTO dependencies (upstream_component_id, downstream_component_id)
                    VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """
              .trimIndent(),
          )
          .use { statement ->
            for (edge in edges) {
              statement.setObject(1, edge.upstream.value)
              statement.setObject(2, edge.downstream.value)
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
