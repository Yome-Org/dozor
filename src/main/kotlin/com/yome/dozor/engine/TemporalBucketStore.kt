package com.yome.dozor.engine

import com.yome.dozor.domain.Signal

interface TemporalBucketStore {
  fun add(signal: Signal)
}
