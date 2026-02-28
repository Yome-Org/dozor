package com.yome.dozor.state

import com.yome.dozor.domain.ComponentState
import com.yome.dozor.domain.Severity
import com.yome.dozor.domain.Signal
import com.yome.dozor.domain.ThresholdConfig
import java.time.Instant

class DeterministicStateEvaluator : StateEvaluator {
  override fun evaluate(
    signals: List<Signal>,
    previousState: ComponentState,
    config: ThresholdConfig,
    now: Instant,
  ): ComponentState {
    if (signals.isEmpty()) {
      return ComponentState.UNKNOWN
    }

    val windowStart = now.minus(config.window)
    val windowSignals =
      signals.filter { !it.occurredAt.isAfter(now) && !it.occurredAt.isBefore(windowStart) }
    if (windowSignals.isEmpty()) {
      return ComponentState.UNKNOWN
    }

    val criticalCount = windowSignals.count { it.severity == Severity.CRITICAL }
    if (criticalCount >= config.criticalThreshold) {
      return ComponentState.CRITICAL
    }

    val warningCount = windowSignals.count { it.severity == Severity.WARNING }
    if (warningCount >= config.degradedThreshold) {
      return ComponentState.DEGRADED
    }

    val inRecovery =
      previousState == ComponentState.CRITICAL || previousState == ComponentState.DEGRADED
    if (inRecovery) {
      val recoveryStart = now.minus(config.recoveryWindow)
      val hasRecentSignals =
        signals.any { !it.occurredAt.isAfter(now) && it.occurredAt.isAfter(recoveryStart) }
      return if (!hasRecentSignals) ComponentState.HEALTHY else previousState
    }

    return ComponentState.HEALTHY
  }
}
