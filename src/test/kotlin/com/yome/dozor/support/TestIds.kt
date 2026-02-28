package com.yome.dozor.support

import com.yome.dozor.domain.ComponentId
import java.nio.charset.StandardCharsets
import java.util.UUID

fun componentId(name: String): ComponentId =
  ComponentId(UUID.nameUUIDFromBytes(name.toByteArray(StandardCharsets.UTF_8)))
