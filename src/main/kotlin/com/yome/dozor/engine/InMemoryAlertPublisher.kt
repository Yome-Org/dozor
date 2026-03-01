package com.yome.dozor.engine

import com.yome.dozor.incident.IncidentTransition
import java.time.Instant

class InMemoryAlertPublisher : AlertPublisher {
  private val published = mutableListOf<Triple<Instant, IncidentTransition, AlertSnapshot>>()

  override fun publish(
    transition: IncidentTransition,
    snapshot: AlertSnapshot,
    now: Instant,
  ) {
    published += Triple(now, transition, snapshot)
  }

  fun publishedTransitions(): List<Triple<Instant, IncidentTransition, AlertSnapshot>> =
    published.toList()
}
