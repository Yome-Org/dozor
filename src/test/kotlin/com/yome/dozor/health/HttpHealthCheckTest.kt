package com.yome.dozor.health

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import javax.net.ssl.SSLSession
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpHealthCheckTest {
  @Test
  fun acceptsExpectedStatusOnlyByDefault() {
    val check =
      HealthCheck(
        component = "postgres",
        type = HealthCheckType.HTTP,
        url = "http://example/health",
        interval = Duration.ofSeconds(30),
        timeout = Duration.ofSeconds(2),
        failureThreshold = 3,
      )

    val executor =
      HttpHealthCheck(
        client =
          StubHttpClient(
            StubHttpResponse(
              statusCode = 200,
              body = """{"status":"UP"}""",
              headers = mapOf("content-type" to listOf("application/json")),
            ),
          ),
      )

    val result = executor.execute(check)

    assertTrue(result.healthy)
  }

  @Test
  fun failsWhenBodyDoesNotContainExpectedMarker() {
    val check =
      HealthCheck(
        component = "homepage",
        type = HealthCheckType.HTTP,
        url = "http://example/",
        interval = Duration.ofSeconds(30),
        timeout = Duration.ofSeconds(2),
        failureThreshold = 2,
        bodyContains = "<title>Example</title>",
      )

    val executor =
      HttpHealthCheck(
        client =
          StubHttpClient(
            StubHttpResponse(
              statusCode = 200,
              body = "<html><title>Other</title></html>",
              headers = mapOf("content-type" to listOf("text/html; charset=utf-8")),
            ),
          ),
      )

    val result = executor.execute(check)

    assertFalse(result.healthy)
  }

  @Test
  fun acceptsXmlByContentTypeAndBodyMarker() {
    val check =
      HealthCheck(
        component = "sitemap",
        type = HealthCheckType.HTTP,
        url = "http://example/sitemap.xml",
        interval = Duration.ofMinutes(5),
        timeout = Duration.ofSeconds(5),
        failureThreshold = 2,
        contentTypeContains = "xml",
        bodyContains = "<urlset",
      )

    val executor =
      HttpHealthCheck(
        client =
          StubHttpClient(
            StubHttpResponse(
              statusCode = 200,
              body = """<?xml version="1.0"?><urlset></urlset>""",
              headers = mapOf("content-type" to listOf("application/xml")),
            ),
          ),
      )

    val result = executor.execute(check)

    assertTrue(result.healthy)
  }

  private class StubHttpClient(
    private val response: HttpResponse<String>,
  ) : HttpClient() {
    override fun <T : Any?> send(
      request: HttpRequest?,
      responseBodyHandler: HttpResponse.BodyHandler<T>?,
    ): HttpResponse<T> = response as HttpResponse<T>

    override fun <T : Any?> sendAsync(
      request: HttpRequest?,
      responseBodyHandler: HttpResponse.BodyHandler<T>?,
    ) = throw UnsupportedOperationException()

    override fun <T : Any?> sendAsync(
      request: HttpRequest?,
      responseBodyHandler: HttpResponse.BodyHandler<T>?,
      pushPromiseHandler: HttpResponse.PushPromiseHandler<T>?,
    ) = throw UnsupportedOperationException()

    override fun cookieHandler(): Optional<java.net.CookieHandler> = Optional.empty()

    override fun connectTimeout(): Optional<Duration> = Optional.empty()

    override fun followRedirects(): Redirect = Redirect.NEVER

    override fun proxy(): Optional<java.net.ProxySelector> = Optional.empty()

    override fun sslContext(): javax.net.ssl.SSLContext = throw UnsupportedOperationException()

    override fun sslParameters(): javax.net.ssl.SSLParameters =
      throw UnsupportedOperationException()

    override fun authenticator(): Optional<java.net.Authenticator> = Optional.empty()

    override fun version(): Version = Version.HTTP_1_1

    override fun executor(): Optional<java.util.concurrent.Executor> = Optional.empty()
  }

  private class StubHttpResponse(
    private val statusCode: Int,
    private val body: String,
    private val headers: Map<String, List<String>>,
  ) : HttpResponse<String> {
    override fun statusCode(): Int = statusCode

    override fun request(): HttpRequest =
      HttpRequest.newBuilder(URI.create("http://example")).build()

    override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()

    override fun headers(): HttpHeaders = HttpHeaders.of(headers) { _, _ -> true }

    override fun body(): String = body

    override fun sslSession(): Optional<SSLSession> = Optional.empty()

    override fun uri(): URI = URI.create("http://example")

    override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
  }
}
