package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.Signal

class InMemorySignalRepository(
  initialSignals: Map<ComponentId, List<Signal>> = emptyMap(),
) : SignalRepository {
  private val store: MutableMap<ComponentId, MutableList<Signal>> =
    initialSignals.mapValues { it.value.toMutableList() }.toMutableMap()

  override fun findByComponent(componentId: ComponentId): List<Signal> =
    store[componentId]?.toList().orEmpty()

  fun append(signal: Signal) {
    store.getOrPut(signal.componentId) { mutableListOf() }.add(signal)
  }
}
