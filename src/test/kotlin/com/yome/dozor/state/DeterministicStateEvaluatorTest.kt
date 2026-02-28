package com.yome.dozor.state

import com.yome.dozor.domain.ComponentState
import com.yome.dozor.domain.Severity
import com.yome.dozor.domain.Signal
import com.yome.dozor.domain.ThresholdConfig
import com.yome.dozor.support.componentId
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class DeterministicStateEvaluatorTest {
  private val evaluator = DeterministicStateEvaluator()
  private val componentId = componentId("db")
  private val config =
    ThresholdConfig(
      criticalThreshold = 3,
      degradedThreshold = 3,
      window = Duration.ofMinutes(5),
      recoveryWindow = Duration.ofMinutes(2),
    )
  private val now = Instant.parse("2026-02-22T12:00:00Z")

  @Test
  fun returnsUnknownWhenNoSignals() {
    val state =
      evaluator.evaluate(
        signals = emptyList(),
        previousState = ComponentState.UNKNOWN,
        config = config,
        now = now,
      )

    assertEquals(ComponentState.UNKNOWN, state)
  }

  @Test
  fun returnsUnknownWhenWindowIsEmpty() {
    val signals =
      listOf(
        Signal(componentId, Severity.CRITICAL, now.minus(Duration.ofMinutes(6))),
      )

    val state = evaluator.evaluate(signals, ComponentState.HEALTHY, config, now)

    assertEquals(ComponentState.UNKNOWN, state)
  }

  @Test
  fun entersCriticalWhenThresholdReachedWithinWindow() {
    val signals =
      listOf(
        Signal(componentId, Severity.CRITICAL, now.minusSeconds(10)),
        Signal(componentId, Severity.CRITICAL, now.minusSeconds(30)),
        Signal(componentId, Severity.CRITICAL, now.minusSeconds(45)),
      )

    val state = evaluator.evaluate(signals, ComponentState.HEALTHY, config, now)

    assertEquals(ComponentState.CRITICAL, state)
  }

  @Test
  fun entersDegradedWhenWarningThresholdReachedAndCriticalNotReached() {
    val signals =
      listOf(
        Signal(componentId, Severity.WARNING, now.minusSeconds(10)),
        Signal(componentId, Severity.WARNING, now.minusSeconds(30)),
        Signal(componentId, Severity.WARNING, now.minusSeconds(45)),
        Signal(componentId, Severity.CRITICAL, now.minusSeconds(90)),
      )

    val state = evaluator.evaluate(signals, ComponentState.HEALTHY, config, now)

    assertEquals(ComponentState.DEGRADED, state)
  }

  @Test
  fun keepsPreviousDegradedDuringRecoveryWindowWithRecentNonInfoSignals() {
    val signals =
      listOf(
        Signal(componentId, Severity.WARNING, now.minusSeconds(30)),
        Signal(componentId, Severity.INFO, now.minusSeconds(70)),
      )

    val state = evaluator.evaluate(signals, ComponentState.DEGRADED, config, now)

    assertEquals(ComponentState.DEGRADED, state)
  }

  @Test
  fun recoversToHealthyWithRecentInfoOnlySignals() {
    val signals =
      listOf(
        Signal(componentId, Severity.INFO, now.minusSeconds(30)),
        Signal(componentId, Severity.INFO, now.minusSeconds(70)),
      )

    val state = evaluator.evaluate(signals, ComponentState.CRITICAL, config, now)

    assertEquals(ComponentState.HEALTHY, state)
  }

  @Test
  fun recoversToHealthyAfterRecoveryWindowSilence() {
    val signals =
      listOf(
        Signal(componentId, Severity.INFO, now.minus(Duration.ofMinutes(3))),
        Signal(componentId, Severity.INFO, now.minus(Duration.ofMinutes(4))),
      )

    val state = evaluator.evaluate(signals, ComponentState.CRITICAL, config, now)

    assertEquals(ComponentState.HEALTHY, state)
  }

  @Test
  fun includesWindowBoundarySignal() {
    val boundarySignal = Signal(componentId, Severity.WARNING, now.minus(config.window))
    val signals =
      listOf(
        boundarySignal,
        Signal(componentId, Severity.WARNING, now.minus(Duration.ofMinutes(1))),
        Signal(componentId, Severity.WARNING, now.minus(Duration.ofMinutes(2))),
      )

    val state = evaluator.evaluate(signals, ComponentState.HEALTHY, config, now)

    assertEquals(ComponentState.DEGRADED, state)
  }

  @Test
  fun ignoresFutureDatedSignals() {
    val signals =
      listOf(
        Signal(componentId, Severity.CRITICAL, now.plusSeconds(5)),
        Signal(componentId, Severity.CRITICAL, now.plusSeconds(6)),
        Signal(componentId, Severity.CRITICAL, now.plusSeconds(7)),
        Signal(componentId, Severity.INFO, now.minusSeconds(10)),
      )

    val state = evaluator.evaluate(signals, ComponentState.HEALTHY, config, now)

    assertEquals(ComponentState.HEALTHY, state)
  }
}
