package com.yome.dozor.config

import java.nio.file.Files
import java.nio.file.Path
import org.yaml.snakeyaml.Yaml

class AppConfigLoader {
  fun load(path: Path): AppConfig {
    val yaml = Yaml()
    Files.newInputStream(path).use { input ->
      @Suppress("UNCHECKED_CAST") val root = yaml.load<Map<String, Any?>>(input)
      return AppConfig(
        context = parseContext(root.requiredMap("context")),
        evaluation = parseEvaluation(root.requiredMap("evaluation")),
        runtime = parseRuntime(root.requiredMap("runtime")),
        api = parseApi(),
        postgres = parsePostgres(),
        redis = parseRedis(),
        telegram = parseTelegram(),
        components =
          root.requiredList("components").map { item ->
            val map = item.requiredMapValue()
            ComponentConfig(name = map.requiredString("name"))
          },
        dependencies =
          root.requiredList("dependencies").map { item ->
            val map = item.requiredMapValue()
            DependencyConfig(
              upstream = map.requiredString("upstream"),
              downstream = map.requiredString("downstream"),
            )
          },
        thresholds =
          root.requiredMap("thresholds").mapValues { (_, raw) ->
            val map = raw.requiredMapValue()
            ThresholdItemConfig(
              critical = map.requiredInt("critical"),
              degraded = map.requiredInt("degraded"),
            )
          },
        checks =
          root.optionalList("checks").map { item ->
            val map = item.requiredMapValue()
            CheckConfig(
              component = map.requiredString("component"),
              type = map.requiredString("type"),
              url = map.requiredString("url"),
              interval = map.requiredString("interval"),
              timeout = map.requiredString("timeout"),
              failureThreshold = map.optionalInt("failure_threshold", 1),
              expectedStatus = map.optionalInt("expected_status", 200),
              bodyContains = map.optionalString("body_contains"),
              contentTypeContains = map.optionalString("content_type_contains"),
            )
          },
      )
    }
  }

  private fun parseEvaluation(map: Map<String, Any?>): EvaluationConfig =
    EvaluationConfig(
      window = map.requiredString("window"),
      recoveryWindow = map.requiredString("recovery_window"),
      incidentThreshold = map.requiredString("incident_threshold"),
      debounce = map.requiredString("debounce"),
    )

  private fun parseRuntime(map: Map<String, Any?>): RuntimeConfig =
    RuntimeConfig(queueCapacity = map.requiredInt("queue_capacity"))

  private fun parseContext(map: Map<String, Any?>): ContextConfig =
    ContextConfig(
      project = map.requiredString("project"),
      environment = map.requiredString("environment"),
      stack = map.optionalString("stack"),
    )

  private fun parseApi(): ApiConfig =
    ApiConfig(
      host = requireEnvString("API_HOST"),
      port = requireEnvInt("API_PORT"),
    )

  private fun parsePostgres(): PostgresConfig =
    PostgresConfig(
      jdbcUrl = requireEnvString("POSTGRES_JDBC_URL"),
      username = requireEnvString("POSTGRES_USERNAME"),
      password = requireEnvString("POSTGRES_PASSWORD"),
    )

  private fun parseRedis(): RedisConfig {
    val enabled = requireEnvBoolean("REDIS_ENABLED")
    return RedisConfig(
      enabled = enabled,
      uri = if (enabled) requireEnvString("REDIS_URI") else envString("REDIS_URI") ?: "",
    )
  }

  private fun parseTelegram(): TelegramConfig {
    val enabled = requireEnvBoolean("TELEGRAM_ENABLED")
    return TelegramConfig(
      enabled = enabled,
      botToken =
        if (enabled) requireEnvString("TELEGRAM_BOT_TOKEN")
        else envString("TELEGRAM_BOT_TOKEN") ?: "",
      chatId =
        if (enabled) requireEnvString("TELEGRAM_CHAT_ID") else envString("TELEGRAM_CHAT_ID") ?: "",
    )
  }
}

private fun envString(key: String): String? = System.getenv(key)?.takeIf { it.isNotBlank() }

private fun envInt(key: String): Int? = envString(key)?.toInt()

private fun envBoolean(key: String): Boolean? = envString(key)?.toBooleanStrict()

private fun requireEnvString(key: String): String =
  envString(key) ?: error("Missing required environment variable: $key")

private fun requireEnvInt(key: String): Int =
  envInt(key) ?: error("Missing required environment variable: $key")

private fun requireEnvBoolean(key: String): Boolean =
  envBoolean(key) ?: error("Missing required environment variable: $key")

private fun Map<String, Any?>.requiredString(key: String): String =
  this[key]?.toString() ?: error("Missing required string: $key")

private fun Map<String, Any?>.optionalString(key: String): String? =
  this[key]?.toString()?.takeIf { it.isNotBlank() }

private fun Map<String, Any?>.requiredInt(key: String): Int {
  val value = this[key] ?: error("Missing required int: $key")
  return when (value) {
    is Number -> value.toInt()
    is String -> value.toInt()
    else -> error("Invalid int for key $key: $value")
  }
}

private fun Map<String, Any?>.optionalInt(key: String, default: Int): Int {
  val value = this[key] ?: return default
  return when (value) {
    is Number -> value.toInt()
    is String -> value.toInt()
    else -> error("Invalid int for key $key: $value")
  }
}

private fun Map<String, Any?>.requiredBoolean(key: String): Boolean {
  val value = this[key] ?: error("Missing required boolean: $key")
  return when (value) {
    is Boolean -> value
    is String -> value.toBooleanStrict()
    else -> error("Invalid boolean for key $key: $value")
  }
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.requiredMap(key: String): Map<String, Any?> =
  this[key] as? Map<String, Any?> ?: error("Missing required map: $key")

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.requiredList(key: String): List<Any?> =
  this[key] as? List<Any?> ?: error("Missing required list: $key")

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.optionalList(key: String): List<Any?> =
  this[key] as? List<Any?> ?: emptyList()

@Suppress("UNCHECKED_CAST")
private fun Any?.requiredMapValue(): Map<String, Any?> =
  this as? Map<String, Any?> ?: error("Expected map value, got: $this")
