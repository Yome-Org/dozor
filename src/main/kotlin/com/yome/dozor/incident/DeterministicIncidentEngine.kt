package com.yome.dozor.incident

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ComponentState
import com.yome.dozor.propagation.DependencyGraph
import java.time.Instant
import java.util.UUID

class DeterministicIncidentEngine : IncidentEngine {
  override fun detectTransitions(
    previousStates: Map<ComponentId, ComponentState>,
    currentStates: Map<ComponentId, ComponentState>,
    activeIncidents: Map<ComponentId, Incident>,
    graph: DependencyGraph,
    now: Instant,
  ): IncidentTransition {
    val opened = mutableListOf<Incident>()
    val resolved = mutableListOf<Incident>()

    for (componentId in graph.topologicalOrder()) {
      val previous = previousStates[componentId] ?: ComponentState.UNKNOWN
      val current = currentStates[componentId] ?: ComponentState.UNKNOWN
      val isRoot =
        graph.allUpstreamOf(componentId).none { upstreamId ->
          (currentStates[upstreamId] ?: ComponentState.UNKNOWN) == ComponentState.CRITICAL
        }

      val transitionedToCritical =
        previous != ComponentState.CRITICAL && current == ComponentState.CRITICAL
      if (transitionedToCritical && isRoot && componentId !in activeIncidents) {
        opened +=
          Incident(
            id = UUID.randomUUID(),
            rootComponentId = componentId,
            startedAt = now,
            resolvedAt = null,
            status = IncidentStatus.OPEN,
          )
      }

      val activeIncident = activeIncidents[componentId]
      if (activeIncident != null && current != ComponentState.CRITICAL) {
        resolved +=
          activeIncident.copy(
            resolvedAt = now,
            status = IncidentStatus.RESOLVED,
          )
      }
    }

    return IncidentTransition(opened = opened, resolved = resolved)
  }
}
