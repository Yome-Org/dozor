package com.yome.dozor.propagation

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ComponentState

interface PropagationEngine {
  fun propagate(
    states: Map<ComponentId, ComponentState>,
    graph: DependencyGraph,
  ): Map<ComponentId, ComponentState>
}
