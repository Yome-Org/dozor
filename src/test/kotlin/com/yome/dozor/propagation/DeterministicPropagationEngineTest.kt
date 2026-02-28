package com.yome.dozor.propagation

import com.yome.dozor.domain.ComponentState
import com.yome.dozor.support.componentId
import kotlin.test.Test
import kotlin.test.assertEquals

class DeterministicPropagationEngineTest {
  private val engine = DeterministicPropagationEngine()

  @Test
  fun propagatesImpactedDownstreamFromCriticalRoot() {
    val db = componentId("db")
    val api = componentId("api")
    val worker = componentId("worker")

    val graph =
      DependencyGraph.from(
        components = setOf(db, api, worker),
        edges =
          setOf(
            DependencyEdge(db, api),
            DependencyEdge(api, worker),
          ),
      )

    val result =
      engine.propagate(
        states =
          mapOf(
            db to ComponentState.CRITICAL,
            api to ComponentState.HEALTHY,
            worker to ComponentState.HEALTHY,
          ),
        graph = graph,
      )

    assertEquals(ComponentState.CRITICAL, result[db])
    assertEquals(ComponentState.IMPACTED, result[api])
    assertEquals(ComponentState.IMPACTED, result[worker])
  }

  @Test
  fun doesNotOverrideLocalDegradedWithImpacted() {
    val db = componentId("db")
    val api = componentId("api")

    val graph =
      DependencyGraph.from(
        components = setOf(db, api),
        edges = setOf(DependencyEdge(db, api)),
      )

    val result =
      engine.propagate(
        states =
          mapOf(
            db to ComponentState.CRITICAL,
            api to ComponentState.DEGRADED,
          ),
        graph = graph,
      )

    assertEquals(ComponentState.DEGRADED, result[api])
  }

  @Test
  fun preservesUnknownWhenNoCriticalUpstream() {
    val db = componentId("db")
    val api = componentId("api")

    val graph =
      DependencyGraph.from(
        components = setOf(db, api),
        edges = setOf(DependencyEdge(db, api)),
      )

    val result =
      engine.propagate(
        states =
          mapOf(
            db to ComponentState.HEALTHY,
            api to ComponentState.UNKNOWN,
          ),
        graph = graph,
      )

    assertEquals(ComponentState.UNKNOWN, result[api])
  }
}
