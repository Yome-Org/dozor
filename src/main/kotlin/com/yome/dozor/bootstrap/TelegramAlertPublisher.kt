package com.yome.dozor.bootstrap

import com.yome.dozor.config.ContextConfig
import com.yome.dozor.domain.ComponentId
import com.yome.dozor.engine.AlertChannel
import com.yome.dozor.engine.AlertDeliveryRecord
import com.yome.dozor.engine.AlertDeliveryRecorder
import com.yome.dozor.engine.AlertDeliveryStatus
import com.yome.dozor.engine.AlertPublisher
import com.yome.dozor.engine.AlertSnapshot
import com.yome.dozor.engine.AlertType
import com.yome.dozor.incident.Incident
import com.yome.dozor.incident.IncidentTransition
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import java.util.logging.Logger

class TelegramAlertPublisher(
  private val botToken: String,
  private val chatId: String,
  private val deliveryRecorder: AlertDeliveryRecorder,
  private val componentNames: Map<ComponentId, String>,
  private val context: ContextConfig,
) : AlertPublisher {
  private val client = HttpClient.newHttpClient()
  private val logger = Logger.getLogger(TelegramAlertPublisher::class.java.name)
  private val timestampFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)

  override fun publish(
    transition: IncidentTransition,
    snapshot: AlertSnapshot,
    now: Instant,
  ) {
    transition.opened.forEach {
      deliver(
        incident = it,
        type = AlertType.OPEN,
        now = now,
        message = formatOpenIncident(it, snapshot),
      )
    }
    transition.resolved.forEach {
      deliver(
        incident = it,
        type = AlertType.RESOLVED,
        now = now,
        message = formatResolvedIncident(it, now),
      )
    }
  }

  private fun formatOpenIncident(
    incident: Incident,
    snapshot: AlertSnapshot,
  ): String = buildString {
    appendLine("❗ INCIDENT OPEN (${shortIncidentId(incident)})")
    appendLine("Project: ${context.project}")
    appendLine("Environment: ${context.environment}")
    context.stack?.let { appendLine("Stack: $it") }
    appendLine("Component: ${componentName(incident)}")
    appendLine("Severity: Critical")
    val impacted = impactedComponents(incident, snapshot)
    if (impacted.isNotEmpty()) {
      appendLine("Impacted: ${impacted.joinToString(", ")}")
    }
    append("Started: ${formatTimestamp(incident.startedAt)}")
  }

  private fun formatResolvedIncident(
    incident: Incident,
    now: Instant,
  ): String = buildString {
    appendLine("✅ INCIDENT RESOLVED (${shortIncidentId(incident)})")
    appendLine("Project: ${context.project}")
    appendLine("Environment: ${context.environment}")
    context.stack?.let { appendLine("Stack: $it") }
    appendLine("Component: ${componentName(incident)}")
    appendLine("Resolved: ${formatTimestamp(now)}")
    append("Duration: ${formatDuration(incident.startedAt, now)}")
  }

  private fun deliver(
    incident: Incident,
    type: AlertType,
    now: Instant,
    message: String,
  ) {
    val encoded = URLEncoder.encode(message, StandardCharsets.UTF_8)
    val uri =
      URI.create("https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$encoded")
    val request = HttpRequest.newBuilder(uri).GET().build()
    try {
      val response = client.send(request, HttpResponse.BodyHandlers.ofString())
      val body = response.body()
      if (response.statusCode() in 200..299) {
        logger.info(
          "Telegram delivery succeeded: type=$type incidentId=${incident.id} statusCode=${response.statusCode()} response=${summarize(body)}",
        )
        deliveryRecorder.record(
          listOf(
            AlertDeliveryRecord(
              incidentId = incident.id,
              type = type,
              sentAt = now,
              channel = AlertChannel.TELEGRAM,
              status = AlertDeliveryStatus.SENT,
            ),
          ),
        )
      } else {
        val error = "statusCode=${response.statusCode()} response=${summarize(body)}"
        logger.severe("Telegram delivery failed: type=$type incidentId=${incident.id} $error")
        deliveryRecorder.record(
          listOf(
            AlertDeliveryRecord(
              incidentId = incident.id,
              type = type,
              sentAt = now,
              channel = AlertChannel.TELEGRAM,
              status = AlertDeliveryStatus.FAILED,
              errorMessage = error,
            ),
          ),
        )
      }
    } catch (ex: Exception) {
      val error = ex.message ?: ex.javaClass.simpleName
      logger.log(
        Level.SEVERE,
        "Telegram delivery failed: type=$type incidentId=${incident.id} error=$error",
        ex,
      )
      deliveryRecorder.record(
        listOf(
          AlertDeliveryRecord(
            incidentId = incident.id,
            type = type,
            sentAt = now,
            channel = AlertChannel.TELEGRAM,
            status = AlertDeliveryStatus.FAILED,
            errorMessage = error,
          ),
        ),
      )
    }
  }

  private fun summarize(body: String): String {
    val normalized = body.replace('\n', ' ').trim()
    return if (normalized.length <= 200) normalized else normalized.take(200) + "..."
  }

  private fun componentName(incident: Incident): String =
    componentNames[incident.rootComponentId] ?: incident.rootComponentId.toString()

  private fun shortIncidentId(incident: Incident): String = incident.id.toString().take(8)

  private fun impactedComponents(
    incident: Incident,
    snapshot: AlertSnapshot,
  ): List<String> =
    snapshot.graph
      .allDownstreamOf(incident.rootComponentId)
      .filter { snapshot.effectiveStates[it] == com.yome.dozor.domain.ComponentState.IMPACTED }
      .map { componentNames[it] ?: it.toString() }
      .sorted()

  private fun formatTimestamp(instant: Instant): String = timestampFormatter.format(instant)

  private fun formatDuration(
    startedAt: Instant,
    now: Instant,
  ): String {
    val totalSeconds = java.time.Duration.between(startedAt, now).seconds.coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
  }
}
