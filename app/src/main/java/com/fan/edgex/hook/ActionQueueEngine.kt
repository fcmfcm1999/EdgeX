package com.fan.edgex.hook

import com.fan.edgex.config.ActionFlowStore
import com.fan.edgex.config.ConditionStore
import com.fan.edgex.config.MultiActionStore
import java.util.Collections

/**
 * Pull-based lazy action cursor replicating XposedEdge's `w1$n0` execution engine.
 *
 * Each control-flow node (`SequenceCursor`, `ConditionCursor`, `RepeatCursor`) pulls
 * child leaf actions on demand so conditions evaluate at the exact point of execution,
 * repeats re-evaluate conditions on every iteration, and `abort` / `stop_all` terminate
 * the running queue immediately.
 */
interface ActionCursor {
    fun hasNext(): Boolean
    fun next(): String?
}

object EmptyCursor : ActionCursor {
    override fun hasNext(): Boolean = false
    override fun next(): String? = null
}

class LeafCursor(private var action: String?) : ActionCursor {
    override fun hasNext(): Boolean = action != null

    override fun next(): String? {
        val current = action
        action = null
        return current
    }
}

class SequenceCursor(
    private val steps: List<String>,
    private val createChild: (String) -> ActionCursor,
) : ActionCursor {
    private var index = 0
    private val children = arrayOfNulls<ActionCursor>(steps.size)

    override fun hasNext(): Boolean {
        if (steps.isEmpty()) return false
        val lastIndex = steps.lastIndex
        if (index < lastIndex) return true
        return index == lastIndex && (children[lastIndex] == null || children[lastIndex]?.hasNext() == true)
    }

    override fun next(): String? {
        while (index < steps.size) {
            val child = children[index] ?: createChild(steps[index]).also { children[index] = it }
            val nextAction = child.next()
            if (nextAction != null) {
                return nextAction
            }
            index++
        }
        return null
    }
}

class ConditionCursor(
    private val conditionId: String,
    private val resolveBranch: (String) -> String,
    private val createChild: (String) -> ActionCursor,
) : ActionCursor {
    private var chosenChild: ActionCursor? = null

    override fun hasNext(): Boolean {
        val child = chosenChild
        return child == null || child.hasNext()
    }

    override fun next(): String? {
        val child = chosenChild ?: createChild(resolveBranch(conditionId)).also { chosenChild = it }
        return child.next()
    }
}

class RepeatCursor(
    private val actionCode: String,
    initialCount: Int,
    private val createChild: (String) -> ActionCursor,
) : ActionCursor {
    private var remaining = initialCount.coerceAtLeast(0)
    private var currentChild: ActionCursor? = if (remaining > 0) createChild(actionCode) else null

    override fun hasNext(): Boolean {
        if (remaining <= 0) return false
        if (remaining > 1) return true
        return currentChild?.hasNext() == true
    }

    override fun next(): String? {
        var emptyIterations = 0
        while (remaining > 0) {
            val child = currentChild ?: createChild(actionCode).also { currentChild = it }
            val nextAction = child.next()
            if (nextAction != null) {
                return nextAction
            }
            remaining--
            if (remaining <= 0) {
                currentChild = null
                return null
            }
            emptyIterations++
            if (emptyIterations >= MAX_CONSECUTIVE_EMPTY_ITERATIONS) {
                remaining = 0
                currentChild = null
                return null
            }
            currentChild = createChild(actionCode)
        }
        return null
    }

    companion object {
        private const val MAX_CONSECUTIVE_EMPTY_ITERATIONS = 64
    }
}

class ActionCursorFactory(
    private val resolveConfig: (String) -> String,
    private val evaluateCondition: (String) -> Boolean,
    private val resolveMultiActionSteps: (String) -> List<String> = { id ->
        MultiActionStore.getStepsFromConfig(resolveConfig, id).map { it.code }
    },
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {
    fun create(actionCode: String, depth: Int = 0): ActionCursor {
        if (depth > maxDepth || actionCode.isBlank() || actionCode == "none") {
            return EmptyCursor
        }
        return when {
            actionCode.startsWith("condition:") -> {
                val id = ConditionStore.extractId(actionCode) ?: return EmptyCursor
                ConditionCursor(
                    conditionId = id,
                    resolveBranch = { condId ->
                        val result = evaluateCondition(condId)
                        if (result) {
                            resolveConfig(ConditionStore.condThenKey(condId))
                        } else {
                            resolveConfig(ConditionStore.condElseKey(condId))
                        }
                    },
                    createChild = { branchCode -> create(branchCode, depth + 1) },
                )
            }
            actionCode.startsWith("multi_action:") -> {
                val id = actionCode.removePrefix("multi_action:")
                if (id.isBlank()) return EmptyCursor
                val stepCodes = resolveMultiActionSteps(id)
                    .filter { it.isNotBlank() && it != "none" }
                if (stepCodes.isEmpty()) return EmptyCursor
                SequenceCursor(stepCodes) { stepCode -> create(stepCode, depth + 1) }
            }
            actionCode.startsWith(ActionFlowStore.ACTION_REPEAT_PREFIX) -> {
                val id = ActionFlowStore.extractRepeatId(actionCode) ?: return EmptyCursor
                val count = ActionFlowStore.parseRepeatCount(resolveConfig(ActionFlowStore.repeatCountKey(id)))
                val childAction = resolveConfig(ActionFlowStore.repeatActionKey(id))
                if (count <= 0 || childAction.isBlank() || childAction == "none") {
                    return EmptyCursor
                }
                RepeatCursor(childAction, count) { bodyCode -> create(bodyCode, depth + 1) }
            }
            else -> LeafCursor(actionCode)
        }
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 16
    }
}

interface ActionClock {
    fun uptimeMillis(): Long
}

interface ActionCancellable {
    fun cancel()
}

interface ActionQueueScheduler {
    fun scheduleShort(delayMs: Long, task: () -> Unit): ActionCancellable
    fun scheduleLong(delayMs: Long, task: () -> Unit): ActionCancellable
}

interface ActionWakeLock {
    fun acquire()
    fun release()
}

object NoOpWakeLock : ActionWakeLock {
    override fun acquire() = Unit
    override fun release() = Unit
}

/**
 * Coordinates queued execution of multi-actions, repeats, conditions, delays, aborts,
 * and `stop_all` cancellation (replicating XposedEdge's `w1$r0` and `w1.v4()`).
 */
class ActionQueueCoordinator(
    private val clock: ActionClock,
    private val scheduler: ActionQueueScheduler,
    private val wakeLockFactory: (String) -> ActionWakeLock = { NoOpWakeLock },
    private val cursorFactory: ActionCursorFactory? = null,
    private val dispatchLeaf: (String) -> Unit = {},
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
    private val log: (String) -> Unit = {},
) {
    @Volatile
    private var lastStopAllUptimeMs: Long = Long.MIN_VALUE

    private var queueCounter = 0L
    private val activeRuns = Collections.synchronizedSet(mutableSetOf<QueueRun>())

    fun isStopped(startTimeUptimeMs: Long): Boolean = startTimeUptimeMs <= lastStopAllUptimeMs

    fun start(
        actionCode: String,
        runCursorFactory: ActionCursorFactory = requireNotNull(cursorFactory) { "cursorFactory must be provided" },
        runDispatchLeaf: (String) -> Unit = dispatchLeaf,
    ) {
        val startTime = clock.uptimeMillis()
        val id = ++queueCounter
        val run = QueueRun(
            tag = "EdgeX:Queue-$id",
            cursor = runCursorFactory.create(actionCode),
            startTimeUptimeMs = startTime,
            runDispatchLeaf = runDispatchLeaf,
        )
        activeRuns.add(run)
        run.start()
    }

    fun stopAll() {
        lastStopAllUptimeMs = clock.uptimeMillis()
        val snapshot = synchronized(activeRuns) { activeRuns.toList() }
        snapshot.forEach { it.cancel() }
        log("stop_all triggered at ${lastStopAllUptimeMs}ms, cancelled ${snapshot.size} active queue(s)")
    }

    fun activeRunCount(): Int = activeRuns.size

    private inner class QueueRun(
        private val tag: String,
        private val cursor: ActionCursor,
        private val startTimeUptimeMs: Long,
        private val runDispatchLeaf: (String) -> Unit,
    ) {
        private val wakeLock = wakeLockFactory(tag)

        @Volatile
        private var pendingHandle: ActionCancellable? = null

        @Volatile
        private var finished = false

        fun start() {
            wakeLock.acquire()
            step()
        }

        fun cancel() {
            val handle = pendingHandle
            pendingHandle = null
            handle?.cancel()
            finish()
        }

        private fun step() {
            if (finished) return
            pendingHandle = null
            try {
                if (isStopped(startTimeUptimeMs)) {
                    finish()
                    return
                }
                val nextAction = cursor.next()
                if (nextAction == null || nextAction == ActionFlowStore.ACTION_ABORT || isStopped(startTimeUptimeMs)) {
                    if (nextAction == ActionFlowStore.ACTION_ABORT) {
                        log("$tag aborted by abort action")
                    }
                    finish()
                    return
                }

                val delayMs = ActionFlowStore.parseDelayMs(nextAction)
                val waitMs = if (delayMs != null) {
                    log("$tag delaying ${delayMs}ms")
                    delayMs
                } else if (nextAction == ActionFlowStore.ACTION_STOP_ALL) {
                    stopAll()
                    finish()
                    return
                } else {
                    try {
                        log("$tag executing '$nextAction'")
                        runDispatchLeaf(nextAction)
                    } catch (t: Throwable) {
                        onError(nextAction, t)
                        finish()
                        return
                    }
                    0L
                }

                if (finished || isStopped(startTimeUptimeMs)) {
                    finish()
                    return
                }

                if (cursor.hasNext()) {
                    scheduleNext(waitMs)
                } else {
                    finish()
                }
            } catch (t: Throwable) {
                onError(tag, t)
                finish()
            }
        }

        private fun scheduleNext(delayMs: Long) {
            if (delayMs < SHORT_DELAY_THRESHOLD_MS) {
                wakeLock.acquire()
                pendingHandle = scheduler.scheduleShort(delayMs) { step() }
            } else {
                pendingHandle = scheduler.scheduleLong(delayMs) {
                    if (!finished) {
                        wakeLock.acquire()
                        step()
                    }
                }
                wakeLock.release()
            }
        }

        private fun finish() {
            if (finished) return
            finished = true
            pendingHandle = null
            activeRuns.remove(this)
            runCatching { wakeLock.release() }
        }
    }

    companion object {
        const val SHORT_DELAY_THRESHOLD_MS = 30_000L
    }
}
