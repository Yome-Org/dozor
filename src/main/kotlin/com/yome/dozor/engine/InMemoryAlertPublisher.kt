package com.yome.dozor.engine

import com.yome.dozor.incident.IncidentTransition
import java.time.Instant

class InMemoryAlertPublisher : AlertPublisher {
  private val published = mutableListOf<Pair<Instant, IncidentTransition>>()

  override fun publish(
    transition: IncidentTransition,
    now: Instant,
  ) {
    published += now to transition
  }

  fun publishedTransitions(): List<Pair<Instant, IncidentTransition>> = published.toList()
}
