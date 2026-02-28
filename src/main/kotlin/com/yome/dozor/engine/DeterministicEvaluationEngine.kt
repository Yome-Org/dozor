package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ComponentState
import com.yome.dozor.incident.IncidentEngine
import com.yome.dozor.propagation.DependencyGraph
import com.yome.dozor.propagation.PropagationEngine
import com.yome.dozor.state.StateEvaluator
import java.time.Instant

class DeterministicEvaluationEngine(
  private val graph: DependencyGraph,
  private val thresholdProvider: ThresholdProvider,
  private val signalRepository: SignalRepository,
  private val stateRepository: StateRepository,
  private val incidentRepository: IncidentRepository,
  private val alertPublisher: AlertPublisher,
  private val stateEvaluator: StateEvaluator,
  private val propagationEngine: PropagationEngine,
  private val incidentEngine: IncidentEngine,
) : EvaluationEngine {
  override fun evaluate(
    dirtyComponents: Set<ComponentId>,
    now: Instant,
  ): EvaluationResult {
    val previousEffective = stateRepository.loadAll().withUnknownDefaults(graph)
    val isolated = previousEffective.toMutableMap()
    val isolatedChanged = linkedMapOf<ComponentId, ComponentState>()

    for (componentId in dirtyComponents.sortedBy { it.toString() }) {
      val signals = signalRepository.findByComponent(componentId)
      val previousState = isolated[componentId] ?: ComponentState.UNKNOWN
      val evaluated =
        stateEvaluator.evaluate(
          signals = signals,
          previousState = previousState,
          config = thresholdProvider.configFor(componentId),
          now = now,
        )
      isolated[componentId] = evaluated
      isolatedChanged[componentId] = evaluated
    }

    val effective = propagationEngine.propagate(isolated.withUnknownDefaults(graph), graph)
    val transition =
      incidentEngine.detectTransitions(
        previousStates = previousEffective,
        currentStates = effective,
        activeIncidents = incidentRepository.loadActive(),
        graph = graph,
        now = now,
      )

    stateRepository.saveAll(effective)
    incidentRepository.saveTransition(transition)
    alertPublisher.publish(transition, now)

    return EvaluationResult(
      isolatedStates = isolatedChanged,
      effectiveStates = effective,
      rootCauses = findRootCauses(effective, graph),
      incidentTransition = transition,
    )
  }

  private fun Map<ComponentId, ComponentState>.withUnknownDefaults(
    graph: DependencyGraph
  ): Map<ComponentId, ComponentState> {
    val copy = this.toMutableMap()
    for (componentId in graph.components()) {
      copy.putIfAbsent(componentId, ComponentState.UNKNOWN)
    }
    return copy
  }

  private fun findRootCauses(
    states: Map<ComponentId, ComponentState>,
    graph: DependencyGraph,
  ): Set<ComponentId> =
    graph
      .topologicalOrder()
      .filter { componentId ->
        states[componentId] == ComponentState.CRITICAL &&
          graph.allUpstreamOf(componentId).none { states[it] == ComponentState.CRITICAL }
      }
      .toSet()
}
