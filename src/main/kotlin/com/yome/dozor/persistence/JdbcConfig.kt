package com.yome.dozor.persistence

data class JdbcConfig(
  val jdbcUrl: String,
  val username: String,
  val password: String,
)
