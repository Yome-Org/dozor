package com.yome.dozor.health

import com.yome.dozor.domain.Severity
import com.yome.dozor.engine.SignalIngestionService
import com.yome.dozor.engine.SignalIngestionStatus
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class HealthScheduler(
  private val checks: List<HealthCheck>,
  private val ingestionService: SignalIngestionService,
  private val executor: HealthCheckExecutor = HttpHealthCheck(),
  private val clock: Clock = Clock.systemUTC(),
) {
  private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
  private val workers = Executors.newFixedThreadPool(4)
  private val consecutiveFailures = ConcurrentHashMap<String, Int>()

  fun start() {
    for (check in checks) {
      scheduler.scheduleAtFixedRate(
        { workers.submit { runCheckOnce(check) } },
        0,
        check.interval.toMillis(),
        TimeUnit.MILLISECONDS,
      )
    }
  }

  fun stop() {
    scheduler.shutdownNow()
    workers.shutdownNow()
  }

  internal fun runCheckOnce(check: HealthCheck) {
    val result =
      when (check.type) {
        HealthCheckType.HTTP -> executor.execute(check)
      }

    val failures =
      if (result.healthy) {
        consecutiveFailures[check.component] = 0
        0
      } else {
        consecutiveFailures.merge(check.component, 1, Int::plus) ?: 1
      }

    val severity =
      when {
        result.healthy -> Severity.INFO
        failures >= check.failureThreshold -> Severity.CRITICAL
        else -> Severity.WARNING
      }

    val ingestion =
      ingestionService.ingest(
        componentName = check.component,
        severity = severity,
        occurredAt = clock.instant(),
        source = "health-check",
        idempotencyKey = null,
      )

    if (ingestion.status == SignalIngestionStatus.BACKPRESSURE) {
      println("health-check dropped by backpressure component=${check.component}")
    }
  }
}
