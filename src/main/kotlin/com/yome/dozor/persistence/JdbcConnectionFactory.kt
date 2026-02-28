package com.yome.dozor.persistence

import java.sql.Connection
import java.sql.DriverManager

class JdbcConnectionFactory(private val config: JdbcConfig) {
  fun open(): Connection =
    DriverManager.getConnection(config.jdbcUrl, config.username, config.password)
}
