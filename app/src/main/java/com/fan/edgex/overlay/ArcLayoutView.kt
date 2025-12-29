package com.fan.edgex.overlay

import android.content.Context
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.OverScroller
import kotlin.math.abs

/**
 * Custom ViewGroup that positions children along an arc path
 * Supports vertical scrolling to navigate through items
 */
class ArcLayoutView(context: Context) : ViewGroup(context) {
    
    // Arc configuration - true semicircle layout
    private val arcRadiusDp = 180f // Radius for semicircle
    private val itemSpacingAngle = 32f // Larger angle to span ~180 degrees with 6 items
    private val itemSizeDp = 65f // Size of each item (smaller icons)
    
    // Convert to pixels
    private val arcRadius = dpToPx(arcRadiusDp)
    private val itemSize = dpToPx(itemSizeDp).toInt()
    
    // Scrolling
    private val scroller = OverScroller(context)
    private var scrollAngle = 0f // Scroll in degrees
    private var lastTouchY = 0f
    private var isDragging = false
    private var velocityTracker: android.view.VelocityTracker? = null
    
    // Number of visible items
    private val visibleItems = 6
    
    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        
        // Measure all children with fixed size
        val childSpec = MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            getChildAt(i).measure(childSpec, childSpec)
        }
        
        setMeasuredDimension(width, height)
    }
    
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (childCount == 0) return
        
        val centerY = height / 2
        
        // Arc center is on the right edge of the view
        val arcCenterX = width.toFloat()
        val arcCenterY = centerY.toFloat()
        
        // Total angle range for circular scrolling
        val totalAngleRange = childCount * itemSpacingAngle
        val normalizedScrollAngle = ((scrollAngle % totalAngleRange) + totalAngleRange) % totalAngleRange
        
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            
            // Calculate angle for this item with circular wrapping
            var itemAngle = i * itemSpacingAngle - normalizedScrollAngle
            
            // Wrap around for circular effect
            if (itemAngle < -totalAngleRange / 2) {
                itemAngle += totalAngleRange
            } else if (itemAngle > totalAngleRange / 2) {
                itemAngle -= totalAngleRange
            }
            
            // Convert to radians
            // At itemAngle = 0 (middle), angleRad = 0 -> cos=1, sin=0
            // x = arcCenterX - radius * cos(0) = width - radius (bulge left)
            // y = arcCenterY + radius * sin(0) = centerY (centered)
            val angleRad = Math.toRadians(itemAngle.toDouble())
            
            // Calculate position on arc
            val x = (arcCenterX - arcRadius * Math.cos(angleRad) - itemSize / 2).toInt()
            val y = (arcCenterY + arcRadius * Math.sin(angleRad) - itemSize / 2).toInt()
            
            // Only show items within visible range (~180 degrees)
            val visibleAngleRange = 100f // 100 degrees each way equals ~200 degrees total visibility
            if (Math.abs(itemAngle) > visibleAngleRange) {
                child.visibility = View.GONE
                continue
            }
            
            // Check if item is within screen bounds
            if (y < -itemSize || y > height) {
                child.visibility = View.GONE
                continue
            }
            
            child.visibility = View.VISIBLE
            
            child.layout(
                x,
                y,
                x + itemSize,
                y + itemSize
            )
            
            // Apply scale and alpha based on distance from center
            val distanceFromCenter = Math.min(1.0, Math.abs(itemAngle) / 90.0)
            val scale = 1.0f - (distanceFromCenter * 0.2).toFloat()
            val alpha = 1.0f - (distanceFromCenter * 0.5).toFloat()
            
            child.scaleX = Math.max(0.7f, scale)
            child.scaleY = Math.max(0.7f, scale)
            child.alpha = Math.max(0.3f, alpha)
        }
    }
    
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = ev.y
                isDragging = false
                scroller.forceFinished(true)
                velocityTracker?.clear()
                velocityTracker = android.view.VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val deltaY = abs(ev.y - lastTouchY)
                if (deltaY > 10) { // Touch slop threshold
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }
    
    var onEmptySpaceClick: (() -> Unit)? = null
    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastTouchY = event.y
                scroller.forceFinished(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = lastTouchY - event.y
                lastTouchY = event.y
                
                // Convert pixel movement to angle change
                val angleChange = deltaY / 8f // Higher sensitivity for larger semicircle
                scrollAngle += angleChange
                
                requestLayout()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Check for click (tap)
                val moveX = abs(event.x - downX)
                val moveY = abs(event.y - downY)
                if (moveX < 10 && moveY < 10) { // Simple touch slop
                     onEmptySpaceClick?.invoke()
                }

                velocityTracker?.computeCurrentVelocity(1000)
                val velocityY = velocityTracker?.yVelocity ?: 0f
                
                if (abs(velocityY) > 500) {
                    // Fling without bounds (circular)
                    val angleVelocity = -velocityY / 10f
                    scroller.fling(
                        0, scrollAngle.toInt(),
                        0, angleVelocity.toInt(),
                        0, 0,
                        Int.MIN_VALUE, Int.MAX_VALUE
                    )
                    postInvalidateOnAnimation()
                }
                
                velocityTracker?.recycle()
                velocityTracker = null
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollAngle = scroller.currY.toFloat()
            requestLayout()
            postInvalidateOnAnimation()
        }
    }
    
    /**
     * Add a child view to the arc layout
     */
    fun addItem(view: View) {
        addView(view)
    }
}
