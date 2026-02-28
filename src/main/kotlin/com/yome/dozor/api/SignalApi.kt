package com.yome.dozor.api

import com.yome.dozor.domain.Severity
import com.yome.dozor.engine.SignalIngestionResult
import com.yome.dozor.engine.SignalIngestionService
import com.yome.dozor.engine.SignalIngestionStatus
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.time.Instant
import kotlinx.serialization.Serializable

fun Application.configureSignalApi(ingestion: SignalIngestionService) {
  install(ContentNegotiation) { json() }

  routing {
    post("/signal") {
      val request =
        runCatching { call.receive<SignalRequest>() }
          .getOrElse {
            call.respond(
              HttpStatusCode.BadRequest,
              ErrorResponse(
                code = "invalid_json",
                message = "Request body must be valid JSON",
              ),
            )
            return@post
          }

      val component = request.component.trim()
      if (component.isEmpty()) {
        call.respond(
          HttpStatusCode.BadRequest,
          ErrorResponse("validation_error", "component must be non-empty"),
        )
        return@post
      }

      val source = request.source.trim()
      if (source.isEmpty()) {
        call.respond(
          HttpStatusCode.BadRequest,
          ErrorResponse("validation_error", "source must be non-empty"),
        )
        return@post
      }

      val severity = parseSeverity(request.severity)
      if (severity == null) {
        call.respond(
          HttpStatusCode.BadRequest,
          ErrorResponse("validation_error", "severity must be INFO, WARNING, or CRITICAL"),
        )
        return@post
      }

      val occurredAt =
        runCatching { Instant.parse(request.occurredAt) }
          .getOrElse {
            call.respond(
              HttpStatusCode.BadRequest,
              ErrorResponse("validation_error", "occurredAt must be ISO-8601 instant"),
            )
            return@post
          }

      val idempotencyKey =
        call.request.headers["Idempotency-Key"]?.takeIf { it.isNotBlank() }
          ?: request.idempotencyKey?.takeIf { it.isNotBlank() }

      val result =
        ingestion.ingest(
          componentName = component,
          severity = severity,
          occurredAt = occurredAt,
          source = source,
          idempotencyKey = idempotencyKey,
        )

      call.respondResult(result)
    }
  }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondResult(
  result: SignalIngestionResult
) {
  when (result.status) {
    SignalIngestionStatus.ACCEPTED ->
      respond(
        HttpStatusCode.Accepted,
        SignalAcceptedResponse(status = "accepted", queueUtilization = result.queueUtilization),
      )
    SignalIngestionStatus.DUPLICATE ->
      respond(
        HttpStatusCode.OK,
        SignalAcceptedResponse(status = "duplicate", queueUtilization = result.queueUtilization),
      )
    SignalIngestionStatus.UNKNOWN_COMPONENT ->
      respond(
        HttpStatusCode.NotFound,
        ErrorResponse("unknown_component", "component is not registered"),
      )
    SignalIngestionStatus.BACKPRESSURE ->
      respond(
        HttpStatusCode.TooManyRequests,
        ErrorResponse(
          code = "backpressure",
          message = "ingestion queue is full",
          details = "queueUtilization=${"%.3f".format(result.queueUtilization)}",
        ),
      )
  }
}

private fun parseSeverity(raw: String): Severity? =
  when (raw.trim().uppercase()) {
    "INFO" -> Severity.INFO
    "WARNING" -> Severity.WARNING
    "CRITICAL" -> Severity.CRITICAL
    else -> null
  }

@Serializable
data class SignalRequest(
  val component: String,
  val severity: String,
  val source: String,
  val occurredAt: String,
  val idempotencyKey: String? = null,
)

@Serializable
data class SignalAcceptedResponse(
  val status: String,
  val queueUtilization: Double,
)

@Serializable
data class ErrorResponse(
  val code: String,
  val message: String,
  val details: String? = null,
)
