package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.incident.Incident
import com.yome.dozor.incident.IncidentStatus
import com.yome.dozor.incident.IncidentTransition

class InMemoryIncidentRepository : IncidentRepository {
  private val active = linkedMapOf<ComponentId, Incident>()
  private val history = mutableListOf<IncidentTransition>()

  override fun loadActive(): Map<ComponentId, Incident> = active.toMap()

  override fun saveTransition(transition: IncidentTransition) {
    for (opened in transition.opened) {
      active[opened.rootComponentId] = opened
    }
    for (resolved in transition.resolved) {
      if (resolved.status == IncidentStatus.RESOLVED) {
        active.remove(resolved.rootComponentId)
      }
    }
    history += transition
  }

  fun history(): List<IncidentTransition> = history.toList()
}
