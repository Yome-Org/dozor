package com.yome.dozor.domain

import java.time.Duration

data class ThresholdConfig(
  val criticalThreshold: Int,
  val degradedThreshold: Int,
  val window: Duration,
  val recoveryWindow: Duration,
) {
  init {
    require(criticalThreshold > 0) { "criticalThreshold must be > 0" }
    require(degradedThreshold > 0) { "degradedThreshold must be > 0" }
    require(!window.isNegative && !window.isZero) { "window must be > 0" }
    require(!recoveryWindow.isNegative && !recoveryWindow.isZero) { "recoveryWindow must be > 0" }
  }
}
