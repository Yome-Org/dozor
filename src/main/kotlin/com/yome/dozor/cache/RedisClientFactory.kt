package com.yome.dozor.cache

import redis.clients.jedis.JedisPooled

class RedisClientFactory(private val uri: String) {
  fun create(): JedisPooled = JedisPooled(uri)
}
