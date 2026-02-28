package com.yome.dozor.propagation

import com.yome.dozor.domain.ComponentId
import java.util.ArrayDeque

class DependencyGraph
private constructor(
  private val nodes: Set<ComponentId>,
  private val downstream: Map<ComponentId, Set<ComponentId>>,
  private val upstream: Map<ComponentId, Set<ComponentId>>,
  private val topoOrder: List<ComponentId>,
) {
  fun components(): Set<ComponentId> = nodes

  fun downstreamOf(componentId: ComponentId): Set<ComponentId> = downstream[componentId].orEmpty()

  fun upstreamOf(componentId: ComponentId): Set<ComponentId> = upstream[componentId].orEmpty()

  fun allUpstreamOf(componentId: ComponentId): Set<ComponentId> {
    val visited = linkedSetOf<ComponentId>()
    val queue = ArrayDeque<ComponentId>()
    queue.addAll(upstreamOf(componentId))

    while (queue.isNotEmpty()) {
      val current = queue.removeFirst()
      if (visited.add(current)) {
        queue.addAll(upstreamOf(current))
      }
    }

    return visited
  }

  fun topologicalOrder(): List<ComponentId> = topoOrder

  companion object {
    fun from(
      components: Set<ComponentId>,
      edges: Set<DependencyEdge>,
    ): DependencyGraph {
      edges.forEach {
        require(it.upstream in components) { "Unknown upstream component: ${it.upstream}" }
        require(it.downstream in components) { "Unknown downstream component: ${it.downstream}" }
      }

      val downstream = components.associateWith { linkedSetOf<ComponentId>() }.toMutableMap()
      val upstream = components.associateWith { linkedSetOf<ComponentId>() }.toMutableMap()
      val indegree = components.associateWith { 0 }.toMutableMap()

      for (edge in edges) {
        if (downstream.getValue(edge.upstream).add(edge.downstream)) {
          upstream.getValue(edge.downstream).add(edge.upstream)
          indegree[edge.downstream] = indegree.getValue(edge.downstream) + 1
        }
      }

      val queue = ArrayDeque<ComponentId>()
      indegree.entries
        .filter { it.value == 0 }
        .map { it.key }
        .sortedBy { it.toString() }
        .forEach { queue.addLast(it) }

      val topo = mutableListOf<ComponentId>()
      while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        topo.add(current)

        val nextNodes = downstream.getValue(current).sortedBy { it.toString() }
        for (next in nextNodes) {
          val nextIndegree = indegree.getValue(next) - 1
          indegree[next] = nextIndegree
          if (nextIndegree == 0) {
            queue.addLast(next)
          }
        }
      }

      require(topo.size == components.size) { "Dependency graph contains a cycle" }

      return DependencyGraph(
        nodes = components,
        downstream = downstream.mapValues { it.value.toSet() },
        upstream = upstream.mapValues { it.value.toSet() },
        topoOrder = topo,
      )
    }
  }
}
