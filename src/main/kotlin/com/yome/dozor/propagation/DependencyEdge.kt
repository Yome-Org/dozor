package com.yome.dozor.propagation

import com.yome.dozor.domain.ComponentId

data class DependencyEdge(
  val upstream: ComponentId,
  val downstream: ComponentId,
)
