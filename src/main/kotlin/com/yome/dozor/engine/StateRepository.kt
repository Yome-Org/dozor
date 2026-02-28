package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ComponentState

interface StateRepository {
  fun loadAll(): Map<ComponentId, ComponentState>

  fun saveAll(states: Map<ComponentId, ComponentState>)
}
