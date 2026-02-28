package com.yome.dozor.engine

import com.yome.dozor.domain.ComponentId
import java.time.Clock
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EvaluationRuntimeLoop(
  private val evaluationEngine: EvaluationEngine,
  private val dirtyStore: DirtyComponentStore,
  private val debounce: Duration,
  private val queueCapacity: Int,
  private val clock: Clock = Clock.systemUTC(),
) {
  private val queue = LinkedBlockingQueue<ComponentId>(queueCapacity)
  private val running = AtomicBoolean(false)
  private var worker: Thread? = null

  fun start() {
    if (!running.compareAndSet(false, true)) return
    worker =
      Thread(::runLoop, "dozor-evaluation-loop").apply {
        isDaemon = true
        start()
      }
  }

  fun stop() {
    running.set(false)
    worker?.interrupt()
    worker?.join(2_000)
  }

  fun canAccept(): Boolean = queue.remainingCapacity() > 0

  fun submitDirty(componentId: ComponentId): Boolean {
    dirtyStore.markDirty(componentId)
    val accepted = queue.offer(componentId)
    return accepted
  }

  fun queueUtilization(): Double = queue.size.toDouble() / queueCapacity.toDouble()

  private fun runLoop() {
    while (running.get()) {
      try {
        val first = queue.poll(250, TimeUnit.MILLISECONDS) ?: continue
        dirtyStore.markDirty(first)

        // Debounce and aggregate dirty set before single evaluation pass.
        val debounceNanos = debounce.toNanos().coerceAtLeast(0)
        val started = System.nanoTime()
        while (System.nanoTime() - started < debounceNanos) {
          val remaining = debounceNanos - (System.nanoTime() - started)
          val polled = queue.poll(remaining.coerceAtLeast(1), TimeUnit.NANOSECONDS) ?: break
          dirtyStore.markDirty(polled)
        }

        val dirty = dirtyStore.drain()
        if (dirty.isNotEmpty()) {
          evaluationEngine.evaluate(dirtyComponents = dirty, now = clock.instant())
        }
      } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        return
      }
    }
  }
}
