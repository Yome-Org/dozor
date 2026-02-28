package com.yome.dozor.state

import com.yome.dozor.domain.ComponentState
import com.yome.dozor.domain.Signal
import com.yome.dozor.domain.ThresholdConfig
import java.time.Instant

interface StateEvaluator {
  fun evaluate(
    signals: List<Signal>,
    previousState: ComponentState,
    config: ThresholdConfig,
    now: Instant,
  ): ComponentState
}
