package com.fan.edgex.hook

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent

internal class NativeTouchHandoff(
    private val log: (String) -> Unit,
) {
    data class Session(
        val savedDownEvent: MotionEvent,
        val consumeStream: Boolean = true,
        var nativeStreamCancelled: Boolean = false,
        var nativeDownInjected: Boolean = false,
    )

    private var injectMethod: java.lang.reflect.Method? = null

    fun begin(event: MotionEvent): Session =
        Session(savedDownEvent = MotionEvent.obtain(event))

    fun cancel(session: Session, context: Context) {
        if (!session.nativeStreamCancelled) {
            injectEvent(context, session.savedDownEvent, MotionEvent.ACTION_CANCEL)
            session.nativeStreamCancelled = true
        }
    }

    fun dispatchSavedDownIfNeeded(session: Session, context: Context) {
        if (!session.nativeDownInjected && !session.nativeStreamCancelled) {
            injectEvent(context, session.savedDownEvent)
            session.nativeDownInjected = true
        }
    }

    fun resume(session: Session, context: Context, currentEvent: MotionEvent) {
        if (!session.nativeStreamCancelled) {
            dispatchSavedDownIfNeeded(session, context)
            injectEvent(context, currentEvent)
        }
    }

    fun shouldProxyToNative(session: Session): Boolean =
        session.consumeStream && session.nativeDownInjected && !session.nativeStreamCancelled

    fun forwardToNative(session: Session, context: Context, event: MotionEvent) {
        if (shouldProxyToNative(session)) {
            injectEvent(context, event)
        }
    }

    fun dispose(session: Session) {
        session.savedDownEvent.recycle()
    }

    private fun injectEvent(context: Context, event: MotionEvent, action: Int? = null) {
        try {
            val inputManager =
                context.getSystemService(Context.INPUT_SERVICE) as android.hardware.input.InputManager
            if (injectMethod == null) {
                injectMethod = inputManager.javaClass.getMethod(
                    "injectInputEvent",
                    android.view.InputEvent::class.java,
                    Int::class.javaPrimitiveType,
                )
            }

            val injected = if (action != null) {
                MotionEvent.obtain(
                    event.downTime,
                    SystemClock.uptimeMillis(),
                    action,
                    event.rawX,
                    event.rawY,
                    event.metaState,
                )
            } else {
                MotionEvent.obtain(event)
            }

            injectMethod?.invoke(inputManager, injected, 0)
            injected.recycle()
        } catch (e: Exception) {
            log("Proxy injection failed: ${e.message}")
        }
    }
}
