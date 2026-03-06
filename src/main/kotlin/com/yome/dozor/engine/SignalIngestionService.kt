package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.Severity
import com.yome.dozor.domain.Signal
import java.time.Instant

enum class SignalIngestionStatus {
  ACCEPTED,
  DUPLICATE,
  UNKNOWN_COMPONENT,
  BACKPRESSURE,
}

data class SignalIngestionResult(
  val status: SignalIngestionStatus,
  val queueUtilization: Double,
)

class SignalIngestionService(
  private val componentByName: Map<String, ComponentId>,
  private val repository: SignalIngestionRepository,
  private val temporalBucketStore: TemporalBucketStore,
  private val runtimeLoop: EvaluationRuntimeLoop,
  private val diagnosticLogs: Boolean = false,
) {
  fun ingest(
    componentName: String,
    severity: Severity,
    occurredAt: Instant,
    source: String,
    idempotencyKey: String? = null,
  ): SignalIngestionResult {
    val componentId =
      componentByName[componentName]
        ?: return SignalIngestionResult(
          SignalIngestionStatus.UNKNOWN_COMPONENT,
          runtimeLoop.queueUtilization()
        ).also { result ->
          if (diagnosticLogs) {
            logIngestion(componentName, severity, source, occurredAt, result)
          }
        }

    if (!runtimeLoop.canAccept()) {
      return SignalIngestionResult(
        SignalIngestionStatus.BACKPRESSURE,
        runtimeLoop.queueUtilization()
      ).also { result ->
        if (diagnosticLogs) {
          logIngestion(componentName, severity, source, occurredAt, result)
        }
      }
    }

    val signal = Signal(componentId = componentId, severity = severity, occurredAt = occurredAt)
    return when (
      repository.append(
        signal = signal,
        source = source,
        ingestedAt = Instant.now(),
        idempotencyKey = idempotencyKey,
      )
    ) {
      SignalAppendResult.DUPLICATE -> {
        SignalIngestionResult(SignalIngestionStatus.DUPLICATE, runtimeLoop.queueUtilization())
      }
      SignalAppendResult.INSERTED -> {
        temporalBucketStore.add(signal)
        runtimeLoop.submitDirty(componentId)
        SignalIngestionResult(SignalIngestionStatus.ACCEPTED, runtimeLoop.queueUtilization())
      }
    }.also { result ->
      if (diagnosticLogs) {
        logIngestion(componentName, severity, source, occurredAt, result)
      }
    }
  }

  private fun logIngestion(
    componentName: String,
    severity: Severity,
    source: String,
    occurredAt: Instant,
    result: SignalIngestionResult,
  ) {
    println(
      "signal-ingest component=$componentName severity=$severity source=$source occurredAt=$occurredAt status=${result.status} queueUtilization=${"%.3f".format(result.queueUtilization)}",
    )
  }
}
