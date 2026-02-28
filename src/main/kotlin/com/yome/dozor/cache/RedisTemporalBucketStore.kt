package com.yome.dozor.cache

import com.yome.dozor.domain.Signal
import com.yome.dozor.engine.TemporalBucketStore
import java.util.UUID
import redis.clients.jedis.JedisPooled

class RedisTemporalBucketStore(
  private val jedis: JedisPooled,
  private val keyPrefix: String = "dozor:signals",
) : TemporalBucketStore {
  override fun add(signal: Signal) {
    val key = "$keyPrefix:${signal.componentId.value}"
    val member = "${signal.severity}:${signal.occurredAt.toEpochMilli()}:${UUID.randomUUID()}"
    jedis.zadd(key, signal.occurredAt.toEpochMilli().toDouble(), member)
  }
}
