package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ComponentState
import com.yome.dozor.propagation.DependencyGraph

data class AlertSnapshot(
  val effectiveStates: Map<ComponentId, ComponentState>,
  val graph: DependencyGraph,
)
