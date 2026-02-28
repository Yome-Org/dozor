package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ThresholdConfig

fun interface ThresholdProvider {
  fun configFor(componentId: ComponentId): ThresholdConfig
}
