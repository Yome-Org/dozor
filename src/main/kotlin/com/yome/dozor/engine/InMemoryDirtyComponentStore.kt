package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import java.util.concurrent.ConcurrentHashMap

class InMemoryDirtyComponentStore : DirtyComponentStore {
  private val dirty = ConcurrentHashMap.newKeySet<ComponentId>()

  override fun markDirty(componentId: ComponentId) {
    dirty += componentId
  }

  override fun drain(): Set<ComponentId> = dirty.toSet().also { dirty.removeAll(it) }
}
