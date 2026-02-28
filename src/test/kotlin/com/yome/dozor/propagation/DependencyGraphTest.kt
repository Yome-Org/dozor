package com.yome.dozor.propagation

import com.yome.dozor.support.componentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DependencyGraphTest {
  @Test
  fun detectsCycle() {
    val a = componentId("a")
    val b = componentId("b")

    val components = setOf(a, b)
    val edges =
      setOf(
        DependencyEdge(a, b),
        DependencyEdge(b, a),
      )

    assertFailsWith<IllegalArgumentException> { DependencyGraph.from(components, edges) }
  }

  @Test
  fun computesTopologicalOrderAndUpstreams() {
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

    val order = graph.topologicalOrder()
    assertTrue(order.indexOf(db) < order.indexOf(api))
    assertTrue(order.indexOf(api) < order.indexOf(worker))
    assertEquals(setOf(db, api), graph.allUpstreamOf(worker))
  }
}
