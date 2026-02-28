package com.yome.dozor.engine

import com.yome.dozor.incident.IncidentTransition
import java.time.Instant

interface AlertPublisher {
  fun publish(
    transition: IncidentTransition,
    now: Instant,
  )
}
