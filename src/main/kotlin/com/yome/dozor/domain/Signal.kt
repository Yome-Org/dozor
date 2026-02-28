package com.yome.dozor.domain

import java.time.Instant

data class Signal(
  val componentId: ComponentId,
  val severity: Severity,
  val occurredAt: Instant,
)
