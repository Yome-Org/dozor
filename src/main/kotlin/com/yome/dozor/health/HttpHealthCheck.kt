package com.yome.dozor.health

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class HttpHealthCheck(
  private val client: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
) : HealthCheckExecutor {
  override fun execute(check: HealthCheck): HealthCheckResult {
    val request = HttpRequest.newBuilder(URI.create(check.url)).timeout(check.timeout).GET().build()

    return runCatching { client.send(request, HttpResponse.BodyHandlers.discarding()) }
      .fold(
        onSuccess = { response ->
          if (response.statusCode() in 200..299) {
            HealthCheckResult(healthy = true, details = "status=${response.statusCode()}")
          } else {
            HealthCheckResult(healthy = false, details = "status=${response.statusCode()}")
          }
        },
        onFailure = { ex -> HealthCheckResult(healthy = false, details = ex.javaClass.simpleName) },
      )
  }
}
