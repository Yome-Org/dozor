package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import java.time.Instant

interface EvaluationEngine {
  fun evaluate(
    dirtyComponents: Set<ComponentId>,
    now: Instant,
  ): EvaluationResult
}
