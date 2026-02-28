package com.yome.dozor.incident

import com.yome.dozor.domain.ComponentId
import java.time.Instant
import java.util.UUID

enum class IncidentStatus {
  OPEN,
  RESOLVED,
}

data class Incident(
  val id: UUID,
  val rootComponentId: ComponentId,
  val startedAt: Instant,
  val resolvedAt: Instant?,
  val status: IncidentStatus,
)

data class IncidentTransition(
  val opened: List<Incident>,
  val resolved: List<Incident>,
)
