package com.yome.dozor.engine

import com.yome.dozor.incident.IncidentTransition
import java.time.Instant

class CompositeAlertPublisher(
  private val delegates: List<AlertPublisher>,
) : AlertPublisher {
  override fun publish(
    transition: IncidentTransition,
    snapshot: AlertSnapshot,
    now: Instant,
  ) {
    for (delegate in delegates) {
      delegate.publish(transition, snapshot, now)
    }
  }
}
