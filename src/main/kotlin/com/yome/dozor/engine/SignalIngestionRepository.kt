package com.yome.dozor.engine

import com.yome.dozor.domain.Signal
import java.time.Instant

enum class SignalAppendResult {
  INSERTED,
  DUPLICATE,
}

interface SignalIngestionRepository {
  fun append(
    signal: Signal,
    source: String,
    ingestedAt: Instant,
    idempotencyKey: String? = null,
  ): SignalAppendResult
}
