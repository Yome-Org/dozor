package com.yome.dozor.config

data class AppConfig(
  val context: ContextConfig,
  val evaluation: EvaluationConfig,
  val runtime: RuntimeConfig,
  val api: ApiConfig,
  val postgres: PostgresConfig,
  val redis: RedisConfig,
  val telegram: TelegramConfig,
  val components: List<ComponentConfig>,
  val dependencies: List<DependencyConfig>,
  val thresholds: Map<String, ThresholdItemConfig>,
  val checks: List<CheckConfig>,
)

data class ContextConfig(
  val project: String,
  val environment: String,
  val stack: String?,
)

data class EvaluationConfig(
  val window: String,
  val recoveryWindow: String,
  val incidentThreshold: String,
  val debounce: String,
)

data class RuntimeConfig(
  val queueCapacity: Int,
)

data class ApiConfig(
  val host: String,
  val port: Int,
)

data class PostgresConfig(
  val jdbcUrl: String,
  val username: String,
  val password: String,
)

data class RedisConfig(
  val enabled: Boolean,
  val uri: String,
)

data class TelegramConfig(
  val enabled: Boolean,
  val botToken: String,
  val chatId: String,
)

data class ComponentConfig(
  val name: String,
)

data class DependencyConfig(
  val upstream: String,
  val downstream: String,
)

data class ThresholdItemConfig(
  val critical: Int,
  val degraded: Int,
)

data class CheckConfig(
  val component: String,
  val type: String,
  val url: String,
  val interval: String,
  val timeout: String,
  val failureThreshold: Int,
  val expectedStatus: Int,
  val bodyContains: String?,
  val contentTypeContains: String?,
)
