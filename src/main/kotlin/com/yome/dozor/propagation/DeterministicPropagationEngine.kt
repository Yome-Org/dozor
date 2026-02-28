package com.yome.dozor.propagation

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ComponentState

class DeterministicPropagationEngine : PropagationEngine {
  override fun propagate(
    states: Map<ComponentId, ComponentState>,
    graph: DependencyGraph,
  ): Map<ComponentId, ComponentState> {
    val result = linkedMapOf<ComponentId, ComponentState>()
    val criticalComponents = states.filterValues { it == ComponentState.CRITICAL }.keys

    for (componentId in graph.topologicalOrder()) {
      val localState = states[componentId] ?: ComponentState.UNKNOWN
      val propagated =
        when {
          localState == ComponentState.CRITICAL -> ComponentState.CRITICAL
          localState == ComponentState.DEGRADED -> ComponentState.DEGRADED
          graph.allUpstreamOf(componentId).any { it in criticalComponents } ->
            ComponentState.IMPACTED
          else -> localState
        }
      result[componentId] = propagated
    }

    // Pass through states that are not part of the graph.
    for ((componentId, state) in states) {
      result.putIfAbsent(componentId, state)
    }

    return result
  }
}
