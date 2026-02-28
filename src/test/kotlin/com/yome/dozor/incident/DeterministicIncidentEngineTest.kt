package com.yome.dozor.incident

import com.yome.dozor.domain.ComponentState
import com.yome.dozor.propagation.DependencyEdge
import com.yome.dozor.propagation.DependencyGraph
import com.yome.dozor.support.componentId
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterministicIncidentEngineTest {
  private val engine = DeterministicIncidentEngine()
  private val now = Instant.parse("2026-02-22T12:00:00Z")

  @Test
  fun opensIncidentForRootCriticalTransition() {
    val db = componentId("db")
    val api = componentId("api")
    val graph =
      DependencyGraph.from(
        components = setOf(db, api),
        edges = setOf(DependencyEdge(db, api)),
      )

    val transitions =
      engine.detectTransitions(
        previousStates = mapOf(db to ComponentState.HEALTHY, api to ComponentState.HEALTHY),
        currentStates = mapOf(db to ComponentState.CRITICAL, api to ComponentState.IMPACTED),
        activeIncidents = emptyMap(),
        graph = graph,
        now = now,
      )

    assertEquals(1, transitions.opened.size)
    assertEquals(db, transitions.opened.single().rootComponentId)
    assertTrue(transitions.resolved.isEmpty())
  }

  @Test
  fun doesNotOpenIncidentForNonRootCriticalComponent() {
    val db = componentId("db")
    val api = componentId("api")
    val graph =
      DependencyGraph.from(
        components = setOf(db, api),
        edges = setOf(DependencyEdge(db, api)),
      )

    val transitions =
      engine.detectTransitions(
        previousStates = mapOf(db to ComponentState.HEALTHY, api to ComponentState.HEALTHY),
        currentStates = mapOf(db to ComponentState.CRITICAL, api to ComponentState.CRITICAL),
        activeIncidents = emptyMap(),
        graph = graph,
        now = now,
      )

    assertEquals(1, transitions.opened.size)
    assertEquals(db, transitions.opened.single().rootComponentId)
  }

  @Test
  fun resolvesIncidentWhenRootLeavesCritical() {
    val db = componentId("db")
    val graph =
      DependencyGraph.from(
        components = setOf(db),
        edges = emptySet(),
      )
    val active =
      Incident(
        id = UUID.randomUUID(),
        rootComponentId = db,
        startedAt = now.minusSeconds(60),
        resolvedAt = null,
        status = IncidentStatus.OPEN,
      )

    val transitions =
      engine.detectTransitions(
        previousStates = mapOf(db to ComponentState.CRITICAL),
        currentStates = mapOf(db to ComponentState.HEALTHY),
        activeIncidents = mapOf(db to active),
        graph = graph,
        now = now,
      )

    assertTrue(transitions.opened.isEmpty())
    assertEquals(1, transitions.resolved.size)
    assertEquals(IncidentStatus.RESOLVED, transitions.resolved.single().status)
  }
}
