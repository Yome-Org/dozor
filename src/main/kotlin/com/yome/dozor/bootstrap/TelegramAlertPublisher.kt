package com.yome.dozor.bootstrap

import com.yome.dozor.engine.AlertPublisher
import com.yome.dozor.incident.Incident
import com.yome.dozor.incident.IncidentTransition
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant

class TelegramAlertPublisher(
  private val botToken: String,
  private val chatId: String,
) : AlertPublisher {
  private val client = HttpClient.newHttpClient()

  override fun publish(
    transition: IncidentTransition,
    now: Instant,
  ) {
    transition.opened.forEach { send("DOZOR INCIDENT OPEN: ${formatIncident(it, now)}") }
    transition.resolved.forEach { send("DOZOR INCIDENT RESOLVED: ${formatIncident(it, now)}") }
  }

  private fun formatIncident(incident: Incident, now: Instant): String =
    "root=${incident.rootComponentId} startedAt=${incident.startedAt} observedAt=$now"

  private fun send(message: String) {
    val encoded = URLEncoder.encode(message, StandardCharsets.UTF_8)
    val uri =
      URI.create("https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$encoded")
    val request = HttpRequest.newBuilder(uri).GET().build()
    client.send(request, HttpResponse.BodyHandlers.discarding())
  }
}
