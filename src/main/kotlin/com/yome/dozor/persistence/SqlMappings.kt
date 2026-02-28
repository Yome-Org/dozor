package com.yome.dozor.persistence

import com.yome.dozor.domain.ComponentState
import com.yome.dozor.domain.Severity
import com.yome.dozor.incident.IncidentStatus

internal fun severityToCode(severity: Severity): Short =
  when (severity) {
    Severity.INFO -> 0
    Severity.WARNING -> 1
    Severity.CRITICAL -> 2
  }

internal fun codeToSeverity(code: Short): Severity =
  when (code.toInt()) {
    0 -> Severity.INFO
    1 -> Severity.WARNING
    2 -> Severity.CRITICAL
    else -> error("Unsupported severity code: $code")
  }

internal fun stateToCode(state: ComponentState): Short =
  when (state) {
    ComponentState.UNKNOWN -> 0
    ComponentState.HEALTHY -> 1
    ComponentState.DEGRADED -> 2
    ComponentState.CRITICAL -> 3
    ComponentState.IMPACTED -> 4
  }

internal fun codeToState(code: Short): ComponentState =
  when (code.toInt()) {
    0 -> ComponentState.UNKNOWN
    1 -> ComponentState.HEALTHY
    2 -> ComponentState.DEGRADED
    3 -> ComponentState.CRITICAL
    4 -> ComponentState.IMPACTED
    else -> error("Unsupported state code: $code")
  }

internal fun incidentStatusToCode(status: IncidentStatus): Short =
  when (status) {
    IncidentStatus.OPEN -> 0
    IncidentStatus.RESOLVED -> 1
  }

internal fun codeToIncidentStatus(code: Short): IncidentStatus =
  when (code.toInt()) {
    0 -> IncidentStatus.OPEN
    1 -> IncidentStatus.RESOLVED
    else -> error("Unsupported incident status code: $code")
  }
