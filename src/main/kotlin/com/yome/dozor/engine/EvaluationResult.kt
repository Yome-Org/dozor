package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ComponentState
import com.yome.dozor.incident.IncidentTransition

data class EvaluationResult(
  val isolatedStates: Map<ComponentId, ComponentState>,
  val effectiveStates: Map<ComponentId, ComponentState>,
  val rootCauses: Set<ComponentId>,
  val incidentTransition: IncidentTransition,
)
