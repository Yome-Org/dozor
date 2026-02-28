package com.yome.dozor.persistence

import org.flywaydb.core.Flyway

class FlywayMigrator(private val config: JdbcConfig) {
  fun migrate() {
    Flyway.configure()
      .dataSource(config.jdbcUrl, config.username, config.password)
      .locations("classpath:db/migration")
      .load()
      .migrate()
  }
}
