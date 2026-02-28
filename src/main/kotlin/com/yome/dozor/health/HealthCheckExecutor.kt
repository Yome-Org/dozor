package com.yome.dozor.health

fun interface HealthCheckExecutor {
  fun execute(check: HealthCheck): HealthCheckResult
}
