package com.yome.dozor.bootstrap

import java.time.Duration

fun parseDuration(raw: String): Duration {
  val trimmed = raw.trim().lowercase()
  return when {
    trimmed.endsWith("ms") -> Duration.ofMillis(trimmed.removeSuffix("ms").toLong())
    trimmed.endsWith("s") -> Duration.ofSeconds(trimmed.removeSuffix("s").toLong())
    trimmed.endsWith("m") -> Duration.ofMinutes(trimmed.removeSuffix("m").toLong())
    trimmed.endsWith("h") -> Duration.ofHours(trimmed.removeSuffix("h").toLong())
    else -> error("Unsupported duration format: $raw")
  }
}
