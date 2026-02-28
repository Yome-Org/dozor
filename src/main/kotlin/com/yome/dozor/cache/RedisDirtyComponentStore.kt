package com.yome.dozor.cache

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.engine.DirtyComponentStore
import java.util.UUID
import redis.clients.jedis.JedisPooled

class RedisDirtyComponentStore(
  private val jedis: JedisPooled,
  private val key: String = "dozor:dirty:components",
) : DirtyComponentStore {
  override fun markDirty(componentId: ComponentId) {
    jedis.sadd(key, componentId.value.toString())
  }

  override fun drain(): Set<ComponentId> {
    val all = jedis.smembers(key)
    if (all.isEmpty()) return emptySet()
    jedis.del(key)
    return all.map { ComponentId(UUID.fromString(it)) }.toSet()
  }
}
