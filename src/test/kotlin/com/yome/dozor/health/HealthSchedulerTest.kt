package com.yome.dozor.health

import com.yome.dozor.domain.Severity
import com.yome.dozor.domain.Signal
import com.yome.dozor.engine.EvaluationEngine
import com.yome.dozor.engine.EvaluationResult
import com.yome.dozor.engine.EvaluationRuntimeLoop
import com.yome.dozor.engine.InMemoryDirtyComponentStore
import com.yome.dozor.engine.NoopTemporalBucketStore
import com.yome.dozor.engine.SignalAppendResult
import com.yome.dozor.engine.SignalIngestionRepository
import com.yome.dozor.engine.SignalIngestionService
import com.yome.dozor.support.componentId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthSchedulerTest {
  @Test
  fun escalatesToCriticalOnlyAfterConfiguredConsecutiveFailures() {
    val check =
      HealthCheck(
        component = "database",
        type = HealthCheckType.HTTP,
        url = "http://example/health",
        interval = Duration.ofSeconds(30),
        timeout = Duration.ofSeconds(2),
        failureThreshold = 3,
      )

    val recorder = RecordingSignalRepository()
    val ingestion = ingestionService(recorder)

    val scheduler =
      HealthScheduler(
        checks = listOf(check),
        ingestionService = ingestion,
        executor =
          SequenceExecutor(
            listOf(
              HealthCheckResult(healthy = false, details = "500"),
              HealthCheckResult(healthy = false, details = "500"),
              HealthCheckResult(healthy = false, details = "500"),
            ),
          ),
        clock = Clock.fixed(Instant.parse("2026-02-22T12:00:00Z"), ZoneOffset.UTC),
      )

    scheduler.runCheckOnce(check)
    scheduler.runCheckOnce(check)
    scheduler.runCheckOnce(check)

    assertEquals(
      listOf(Severity.WARNING, Severity.WARNING, Severity.CRITICAL),
      recorder.signals.map { it.severity },
    )
  }

  @Test
  fun resetsFailureCounterAfterSuccessfulCheck() {
    val check =
      HealthCheck(
        component = "database",
        type = HealthCheckType.HTTP,
        url = "http://example/health",
        interval = Duration.ofSeconds(30),
        timeout = Duration.ofSeconds(2),
        failureThreshold = 2,
      )

    val recorder = RecordingSignalRepository()
    val ingestion = ingestionService(recorder)

    val scheduler =
      HealthScheduler(
        checks = listOf(check),
        ingestionService = ingestion,
        executor =
          SequenceExecutor(
            listOf(
              HealthCheckResult(healthy = false, details = "500"),
              HealthCheckResult(healthy = false, details = "500"),
              HealthCheckResult(healthy = true, details = "200"),
              HealthCheckResult(healthy = false, details = "500"),
            ),
          ),
        clock = Clock.fixed(Instant.parse("2026-02-22T12:00:00Z"), ZoneOffset.UTC),
      )

    scheduler.runCheckOnce(check)
    scheduler.runCheckOnce(check)
    scheduler.runCheckOnce(check)
    scheduler.runCheckOnce(check)

    assertEquals(
      listOf(Severity.WARNING, Severity.CRITICAL, Severity.INFO, Severity.WARNING),
      recorder.signals.map { it.severity },
    )
  }

  private fun ingestionService(repository: RecordingSignalRepository): SignalIngestionService {
    val evaluationEngine = NoopEvaluationEngine()
    val runtimeLoop =
      EvaluationRuntimeLoop(
        evaluationEngine = evaluationEngine,
        dirtyStore = InMemoryDirtyComponentStore(),
        debounce = Duration.ofMillis(5),
        queueCapacity = 128,
        clock = Clock.fixed(Instant.parse("2026-02-22T12:00:00Z"), ZoneOffset.UTC),
      )

    return SignalIngestionService(
      componentByName = mapOf("database" to componentId("database")),
      repository = repository,
      temporalBucketStore = NoopTemporalBucketStore(),
      runtimeLoop = runtimeLoop,
    )
  }

  private class RecordingSignalRepository : SignalIngestionRepository {
    val signals = mutableListOf<Signal>()

    override fun append(
      signal: Signal,
      source: String,
      ingestedAt: Instant,
      idempotencyKey: String?,
    ): SignalAppendResult {
      signals += signal
      return SignalAppendResult.INSERTED
    }
  }

  private class SequenceExecutor(
    private val results: List<HealthCheckResult>,
  ) : HealthCheckExecutor {
    private var idx = 0

    override fun execute(check: HealthCheck): HealthCheckResult {
      val result = results.getOrElse(idx) { results.last() }
      idx++
      return result
    }
  }

  private class NoopEvaluationEngine : EvaluationEngine {
    override fun evaluate(
      dirtyComponents: Set<com.yome.dozor.domain.ComponentId>,
      now: Instant,
    ): EvaluationResult =
      EvaluationResult(
        isolatedStates = emptyMap(),
        effectiveStates = emptyMap(),
        rootCauses = emptySet(),
        incidentTransition = com.yome.dozor.incident.IncidentTransition(emptyList(), emptyList()),
      )
  }
}
