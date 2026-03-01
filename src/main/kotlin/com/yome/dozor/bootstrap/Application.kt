package com.yome.dozor.bootstrap

import com.yome.dozor.api.configureSignalApi
import com.yome.dozor.cache.RedisClientFactory
import com.yome.dozor.cache.RedisDirtyComponentStore
import com.yome.dozor.cache.RedisTemporalBucketStore
import com.yome.dozor.config.AppConfigLoader
import com.yome.dozor.domain.Component
import com.yome.dozor.domain.ComponentId
import com.yome.dozor.domain.ThresholdConfig
import com.yome.dozor.engine.AlertChannel
import com.yome.dozor.engine.CompositeAlertPublisher
import com.yome.dozor.engine.DeterministicEvaluationEngine
import com.yome.dozor.engine.EvaluationRuntimeLoop
import com.yome.dozor.engine.InMemoryDirtyComponentStore
import com.yome.dozor.engine.NoopTemporalBucketStore
import com.yome.dozor.engine.SignalIngestionService
import com.yome.dozor.engine.ThresholdProvider
import com.yome.dozor.health.HealthCheck
import com.yome.dozor.health.HealthCheckType
import com.yome.dozor.health.HealthScheduler
import com.yome.dozor.incident.DeterministicIncidentEngine
import com.yome.dozor.persistence.FlywayMigrator
import com.yome.dozor.persistence.JdbcConfig
import com.yome.dozor.persistence.JdbcConnectionFactory
import com.yome.dozor.persistence.PostgresAlertDeliveryRecorder
import com.yome.dozor.persistence.PostgresAlertPublisher
import com.yome.dozor.persistence.PostgresIncidentRepository
import com.yome.dozor.persistence.PostgresSignalRepository
import com.yome.dozor.persistence.PostgresStateRepository
import com.yome.dozor.propagation.DependencyEdge
import com.yome.dozor.propagation.DependencyGraph
import com.yome.dozor.propagation.DeterministicPropagationEngine
import com.yome.dozor.state.DeterministicStateEvaluator
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Logger

fun main() {
  val logger = Logger.getLogger("com.yome.dozor.bootstrap.Application")
  val configPath = Path.of(System.getenv("DOZOR_CONFIG") ?: "config/dozor.yaml")
  val config = AppConfigLoader().load(configPath)

  val jdbcConfig =
    JdbcConfig(
      jdbcUrl = config.postgres.jdbcUrl,
      username = config.postgres.username,
      password = config.postgres.password,
    )
  val connectionFactory = JdbcConnectionFactory(jdbcConfig)
  FlywayMigrator(jdbcConfig).migrate()

  val components =
    config.components.map { c -> Component(id = stableComponentId(c.name), name = c.name) }
  val componentByName = components.associateBy { it.name }

  val edges =
    config.dependencies.map { d ->
      DependencyEdge(
        upstream = componentByName.getValue(d.upstream).id,
        downstream = componentByName.getValue(d.downstream).id,
      )
    }

  PostgresTopologySeeder(connectionFactory).seed(components, edges)

  val graph =
    DependencyGraph.from(
      components = components.map { it.id }.toSet(),
      edges = edges.toSet(),
    )

  val signalRepository = PostgresSignalRepository(connectionFactory)
  val stateRepository = PostgresStateRepository(connectionFactory)
  val incidentRepository = PostgresIncidentRepository(connectionFactory)

  val deliveryRecorder = PostgresAlertDeliveryRecorder(connectionFactory)
  val internalAlertPublisher =
    PostgresAlertPublisher(connectionFactory, channel = AlertChannel.INTERNAL)
  val alertPublisher =
    if (config.telegram.enabled) {
      CompositeAlertPublisher(
        listOf(
          internalAlertPublisher,
          TelegramAlertPublisher(
            config.telegram.botToken,
            config.telegram.chatId,
            deliveryRecorder,
            components.associate { it.id to it.name },
            config.context,
          ),
        ),
      )
    } else {
      internalAlertPublisher
    }

  val dirtyStore =
    if (config.redis.enabled) {
      RedisDirtyComponentStore(RedisClientFactory(config.redis.uri).create())
    } else {
      InMemoryDirtyComponentStore()
    }
  val temporalBucketStore =
    if (config.redis.enabled) {
      RedisTemporalBucketStore(RedisClientFactory(config.redis.uri).create())
    } else {
      NoopTemporalBucketStore()
    }

  val thresholdProvider = ThresholdProvider { componentId ->
    val componentName = components.first { it.id == componentId }.name
    val threshold =
      config.thresholds[componentName]
        ?: error("Threshold config missing for component: $componentName")
    ThresholdConfig(
      criticalThreshold = threshold.critical,
      degradedThreshold = threshold.degraded,
      window = parseDuration(config.evaluation.window),
      recoveryWindow = parseDuration(config.evaluation.recoveryWindow),
    )
  }

  val evaluationEngine =
    DeterministicEvaluationEngine(
      graph = graph,
      thresholdProvider = thresholdProvider,
      signalRepository = signalRepository,
      stateRepository = stateRepository,
      incidentRepository = incidentRepository,
      alertPublisher = alertPublisher,
      stateEvaluator = DeterministicStateEvaluator(),
      propagationEngine = DeterministicPropagationEngine(),
      incidentEngine = DeterministicIncidentEngine(),
    )

  val runtimeLoop =
    EvaluationRuntimeLoop(
      evaluationEngine = evaluationEngine,
      dirtyStore = dirtyStore,
      debounce = parseDuration(config.evaluation.debounce),
      queueCapacity = config.runtime.queueCapacity,
    )
  runtimeLoop.start()

  val ingestion =
    SignalIngestionService(
      componentByName = componentByName.mapValues { it.value.id },
      repository = signalRepository,
      temporalBucketStore = temporalBucketStore,
      runtimeLoop = runtimeLoop,
    )

  val healthChecks =
    config.checks.map { check ->
      HealthCheck(
        component = check.component,
        type =
          when (check.type.lowercase()) {
            "http" -> HealthCheckType.HTTP
            else -> error("Unsupported check type: ${check.type}")
          },
        url = check.url,
        interval = parseDuration(check.interval),
        timeout = parseDuration(check.timeout),
        failureThreshold = check.failureThreshold,
        expectedStatus = check.expectedStatus,
        bodyContains = check.bodyContains,
        contentTypeContains = check.contentTypeContains,
      )
    }
  val healthScheduler = HealthScheduler(healthChecks, ingestion)
  healthScheduler.start()

  logger.info(
    buildString {
      append("Dozor startup: ")
      append("configPath=").append(configPath)
      append(", project=").append(config.context.project)
      append(", environment=").append(config.context.environment)
      config.context.stack?.let { append(", stack=").append(it) }
      append(", api=").append(config.api.host).append(":").append(config.api.port)
      append(", postgres=").append(config.postgres.jdbcUrl)
      append(", redisEnabled=").append(config.redis.enabled)
      append(", telegramEnabled=").append(config.telegram.enabled)
      append(", components=").append(components.size)
      append(", dependencies=").append(edges.size)
      append(", checks=").append(healthChecks.size)
    }
  )

  Runtime.getRuntime()
    .addShutdownHook(
      Thread {
        logger.info("Dozor shutdown: stopping health scheduler and evaluation loop")
        healthScheduler.stop()
        runtimeLoop.stop()
      },
    )

  embeddedServer(
      factory = Netty,
      host = config.api.host,
      port = config.api.port,
    ) {
      configureSignalApi(ingestion)
    }
    .start(wait = true)
}

private fun stableComponentId(name: String): ComponentId =
  ComponentId(UUID.nameUUIDFromBytes(name.toByteArray(StandardCharsets.UTF_8)))
