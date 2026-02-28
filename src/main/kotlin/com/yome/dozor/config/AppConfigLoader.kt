package com.yome.dozor.config

import io.github.cdimascio.dotenv.Dotenv
import java.nio.file.Files
import java.nio.file.Path
import org.yaml.snakeyaml.Yaml

class AppConfigLoader {
  fun load(path: Path): AppConfig {
    val dotenv =
      Dotenv.configure()
        .directory(path.parent?.toString() ?: ".")
        .ignoreIfMalformed()
        .ignoreIfMissing()
        .load()
    val yaml = Yaml()
    Files.newInputStream(path).use { input ->
      @Suppress("UNCHECKED_CAST") val root = yaml.load<Map<String, Any?>>(input)
      return AppConfig(
        evaluation = parseEvaluation(root.requiredMap("evaluation")),
        runtime = parseRuntime(root.requiredMap("runtime")),
        api = parseApi(root.requiredMap("api"), dotenv),
        postgres = parsePostgres(root.requiredMap("postgres"), dotenv),
        redis = parseRedis(root.requiredMap("redis"), dotenv),
        telegram = parseTelegram(root.requiredMap("telegram"), dotenv),
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

  private fun parseApi(map: Map<String, Any?>, dotenv: Dotenv): ApiConfig =
    ApiConfig(
      host = envString(dotenv, "API_HOST") ?: map.requiredString("host"),
      port = envInt(dotenv, "API_PORT") ?: map.requiredInt("port"),
    )

  private fun parsePostgres(map: Map<String, Any?>, dotenv: Dotenv): PostgresConfig =
    PostgresConfig(
      jdbcUrl = envString(dotenv, "POSTGRES_JDBC_URL") ?: map.requiredString("jdbc_url"),
      username = envString(dotenv, "POSTGRES_USERNAME") ?: map.requiredString("username"),
      password = envString(dotenv, "POSTGRES_PASSWORD") ?: map.requiredString("password"),
    )

  private fun parseRedis(map: Map<String, Any?>, dotenv: Dotenv): RedisConfig =
    RedisConfig(
      enabled = envBoolean(dotenv, "REDIS_ENABLED") ?: map.requiredBoolean("enabled"),
      uri = envString(dotenv, "REDIS_URI") ?: map.requiredString("uri"),
    )

  private fun parseTelegram(map: Map<String, Any?>, dotenv: Dotenv): TelegramConfig =
    TelegramConfig(
      enabled = envBoolean(dotenv, "TELEGRAM_ENABLED") ?: map.requiredBoolean("enabled"),
      botToken = envString(dotenv, "TELEGRAM_BOT_TOKEN") ?: map.requiredString("bot_token"),
      chatId = envString(dotenv, "TELEGRAM_CHAT_ID") ?: map.requiredString("chat_id"),
    )
}

private fun envString(dotenv: Dotenv, key: String): String? =
  dotenv[key]?.takeIf { it.isNotBlank() } ?: System.getenv(key)?.takeIf { it.isNotBlank() }

private fun envInt(dotenv: Dotenv, key: String): Int? = envString(dotenv, key)?.toInt()

private fun envBoolean(dotenv: Dotenv, key: String): Boolean? =
  envString(dotenv, key)?.toBooleanStrict()

private fun Map<String, Any?>.requiredString(key: String): String =
  this[key]?.toString() ?: error("Missing required string: $key")

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
