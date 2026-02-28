package com.yome.dozor.engine

import com.yome.dozor.domain.Signal

class NoopTemporalBucketStore : TemporalBucketStore {
  override fun add(signal: Signal) = Unit
}
