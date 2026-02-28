package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId

interface DirtyComponentStore {
  fun markDirty(componentId: ComponentId)

  fun drain(): Set<ComponentId>
}
