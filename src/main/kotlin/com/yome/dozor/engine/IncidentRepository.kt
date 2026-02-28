package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.incident.Incident
import com.yome.dozor.incident.IncidentTransition

interface IncidentRepository {
  fun loadActive(): Map<ComponentId, Incident>

  fun saveTransition(transition: IncidentTransition)
}
