package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentState
import com.yome.dozor.domain.Severity
import com.yome.dozor.domain.Signal
import com.yome.dozor.domain.ThresholdConfig
import com.yome.dozor.incident.DeterministicIncidentEngine
import com.yome.dozor.propagation.DependencyEdge
import com.yome.dozor.propagation.DependencyGraph
import com.yome.dozor.propagation.DeterministicPropagationEngine
import com.yome.dozor.state.DeterministicStateEvaluator
import com.yome.dozor.support.componentId
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterministicEvaluationEngineTest {
  @Test
  fun endToEndEvaluationForDbApiWorkerChain() {
    val db = componentId("postgres")
    val api = componentId("api")
    val worker = componentId("worker")

    val graph =
      DependencyGraph.from(
        components = setOf(db, api, worker),
        edges =
          setOf(
            DependencyEdge(db, api),
            DependencyEdge(api, worker),
          ),
      )

    val now = Instant.parse("2026-02-22T12:00:00Z")
    val signalRepository = InMemorySignalRepository()
    signalRepository.append(Signal(db, Severity.CRITICAL, now.minusSeconds(10)))
    signalRepository.append(Signal(db, Severity.CRITICAL, now.minusSeconds(20)))
    signalRepository.append(Signal(db, Severity.CRITICAL, now.minusSeconds(30)))

    val stateRepository =
      InMemoryStateRepository(
        initialStates =
          mapOf(
            db to ComponentState.HEALTHY,
            api to ComponentState.HEALTHY,
            worker to ComponentState.HEALTHY,
          ),
      )
    val incidentRepository = InMemoryIncidentRepository()
    val alertPublisher = InMemoryAlertPublisher()

    val threshold =
      ThresholdConfig(
        criticalThreshold = 3,
        degradedThreshold = 3,
        window = Duration.ofMinutes(5),
        recoveryWindow = Duration.ofMinutes(2),
      )

    val engine =
      DeterministicEvaluationEngine(
        graph = graph,
        thresholdProvider = ThresholdProvider { threshold },
        signalRepository = signalRepository,
        stateRepository = stateRepository,
        incidentRepository = incidentRepository,
        alertPublisher = alertPublisher,
        stateEvaluator = DeterministicStateEvaluator(),
        propagationEngine = DeterministicPropagationEngine(),
        incidentEngine = DeterministicIncidentEngine(),
      )

    val first = engine.evaluate(dirtyComponents = setOf(db), now = now)

    assertEquals(ComponentState.CRITICAL, first.effectiveStates[db])
    assertEquals(ComponentState.IMPACTED, first.effectiveStates[api])
    assertEquals(ComponentState.IMPACTED, first.effectiveStates[worker])
    assertEquals(setOf(db), first.rootCauses)
    assertEquals(1, first.incidentTransition.opened.size)
    assertEquals(db, first.incidentTransition.opened.single().rootComponentId)

    val second = engine.evaluate(dirtyComponents = setOf(db), now = now)

    assertTrue(second.incidentTransition.opened.isEmpty())
    assertTrue(second.incidentTransition.resolved.isEmpty())
    assertEquals(first.effectiveStates, second.effectiveStates)
  }

  @Test
  fun opensSingleIncidentForSharedDependencyFailure() {
    val mailer = componentId("mailer")
    val api = componentId("api")
    val worker = componentId("worker")
    val notifier = componentId("notifier")
    val web = componentId("web")

    val graph =
      DependencyGraph.from(
        components = setOf(mailer, api, worker, notifier, web),
        edges =
          setOf(
            DependencyEdge(mailer, api),
            DependencyEdge(mailer, worker),
            DependencyEdge(mailer, notifier),
            DependencyEdge(api, web),
          ),
      )

    val now = Instant.parse("2026-02-22T12:00:00Z")
    val signalRepository = InMemorySignalRepository()
    signalRepository.append(Signal(mailer, Severity.CRITICAL, now.minusSeconds(10)))
    signalRepository.append(Signal(mailer, Severity.CRITICAL, now.minusSeconds(20)))
    signalRepository.append(Signal(mailer, Severity.CRITICAL, now.minusSeconds(30)))

    val stateRepository =
      InMemoryStateRepository(
        initialStates =
          mapOf(
            mailer to ComponentState.HEALTHY,
            api to ComponentState.HEALTHY,
            worker to ComponentState.HEALTHY,
            notifier to ComponentState.HEALTHY,
            web to ComponentState.HEALTHY,
          ),
      )
    val incidentRepository = InMemoryIncidentRepository()
    val alertPublisher = InMemoryAlertPublisher()

    val threshold =
      ThresholdConfig(
        criticalThreshold = 3,
        degradedThreshold = 3,
        window = Duration.ofMinutes(5),
        recoveryWindow = Duration.ofMinutes(2),
      )

    val engine =
      DeterministicEvaluationEngine(
        graph = graph,
        thresholdProvider = ThresholdProvider { threshold },
        signalRepository = signalRepository,
        stateRepository = stateRepository,
        incidentRepository = incidentRepository,
        alertPublisher = alertPublisher,
        stateEvaluator = DeterministicStateEvaluator(),
        propagationEngine = DeterministicPropagationEngine(),
        incidentEngine = DeterministicIncidentEngine(),
      )

    val result = engine.evaluate(dirtyComponents = setOf(mailer), now = now)

    assertEquals(ComponentState.CRITICAL, result.effectiveStates[mailer])
    assertEquals(ComponentState.IMPACTED, result.effectiveStates[api])
    assertEquals(ComponentState.IMPACTED, result.effectiveStates[worker])
    assertEquals(ComponentState.IMPACTED, result.effectiveStates[notifier])
    assertEquals(ComponentState.IMPACTED, result.effectiveStates[web])
    assertEquals(setOf(mailer), result.rootCauses)
    assertEquals(1, result.incidentTransition.opened.size)
    assertEquals(mailer, result.incidentTransition.opened.single().rootComponentId)
  }
}
