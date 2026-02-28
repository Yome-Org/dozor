package com.yome.dozor.health

import java.time.Duration

enum class HealthCheckType {
  HTTP,
}

data class HealthCheck(
  val component: String,
  val type: HealthCheckType,
  val url: String,
  val interval: Duration,
  val timeout: Duration,
  val failureThreshold: Int,
)

data class HealthCheckResult(
  val healthy: Boolean,
  val details: String,
)
