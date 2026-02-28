package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.Signal

interface SignalRepository {
  fun findByComponent(componentId: ComponentId): List<Signal>
}
