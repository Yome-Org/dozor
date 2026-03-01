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

    return runCatching { client.send(request, HttpResponse.BodyHandlers.ofString()) }
      .fold(
        onSuccess = { response ->
          val statusMatches = response.statusCode() == check.expectedStatus
          val contentType = response.headers().firstValue("content-type").orElse("")
          val contentTypeMatches =
            check.contentTypeContains?.let { expected ->
              contentType.contains(expected, ignoreCase = true)
            } ?: true
          val bodyMatches =
            check.bodyContains?.let { expected -> response.body().contains(expected) } ?: true

          if (statusMatches && contentTypeMatches && bodyMatches) {
            HealthCheckResult(
              healthy = true,
              details = "status=${response.statusCode()} contentType=$contentType",
            )
          } else {
            HealthCheckResult(
              healthy = false,
              details =
                "status=${response.statusCode()} expectedStatus=${check.expectedStatus} contentType=$contentType bodyContainsMatched=$bodyMatches contentTypeMatched=$contentTypeMatches",
            )
          }
        },
        onFailure = { ex -> HealthCheckResult(healthy = false, details = ex.javaClass.simpleName) },
      )
  }
}
