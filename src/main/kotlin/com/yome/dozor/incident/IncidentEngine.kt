package com.yome.dozor.incident

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ComponentState
import com.yome.dozor.propagation.DependencyGraph
import java.time.Instant

interface IncidentEngine {
  fun detectTransitions(
    previousStates: Map<ComponentId, ComponentState>,
    currentStates: Map<ComponentId, ComponentState>,
    activeIncidents: Map<ComponentId, Incident>,
    graph: DependencyGraph,
    now: Instant,
  ): IncidentTransition
}
