package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ComponentState

class InMemoryStateRepository(
  initialStates: Map<ComponentId, ComponentState> = emptyMap(),
) : StateRepository {
  private val store: MutableMap<ComponentId, ComponentState> = initialStates.toMutableMap()

  override fun loadAll(): Map<ComponentId, ComponentState> = store.toMap()

  override fun saveAll(states: Map<ComponentId, ComponentState>) {
    store.clear()
    store.putAll(states)
  }
}
