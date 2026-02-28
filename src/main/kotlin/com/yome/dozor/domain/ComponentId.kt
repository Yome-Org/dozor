package com.yome.dozor.domain

import java.util.UUID

@JvmInline
value class ComponentId(val value: UUID) {
  override fun toString(): String = value.toString()

  companion object {
    fun from(raw: String): ComponentId = ComponentId(UUID.fromString(raw))
  }
}
