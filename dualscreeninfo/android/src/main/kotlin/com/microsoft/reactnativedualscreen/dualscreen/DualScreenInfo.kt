package com.microsoft.reactnativedualscreen.dualscreen

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.WindowManager
import android.view.Surface
import android.view.Window
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Arguments.createMap
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import com.microsoft.device.display.DisplayMask

const val HINGE_WIDTH_KEY = "hingeWidth"
const val IS_DUALSCREEN_DEVICE_KEY = "isDualScreenDevice"
const val FEATURE_NAME = "com.microsoft.device.display.displaymask"

class DualScreenInfo constructor(context: ReactApplicationContext) : ReactContextBaseJavaModule(context), LifecycleEventListener  {
	private val mDisplayMask: DisplayMask?
		get() {
			return if(currentActivity != null && isDualScreenDevice) DisplayMask.fromResourcesRect(currentActivity) else null
		}
	private val rotation: Int
		get() {
			val wm = currentActivity?.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
			return wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
		}
	private val hinge: Rect
		get() {
			val boundings = mDisplayMask?.getBoundingRectsForRotation(rotation)
			return if (boundings == null || boundings.size == 0) {
				Rect(0, 0, 0, 0)
			} else boundings[0]
		}
	private val isStatusBarVisible: Boolean
		get() {
			val rectangle = Rect()
			val window: Window? = currentActivity?.window;
			window?.decorView?.getWindowVisibleDisplayFrame(rectangle)
			val statusBarHeight = rectangle.top
			return statusBarHeight != 0
		}
	private val mStatusBarHeight: Int
		get() {
			var statusBarHeight: Int = 0;
			if(isStatusBarVisible)
			{
				val resourceId: Int = reactApplicationContext.resources.getIdentifier("status_bar_height", "dimen", "android")
				if (resourceId > 0) {
					statusBarHeight = reactApplicationContext.resources.getDimensionPixelSize(resourceId)
				}
				return statusBarHeight;
			}
			return statusBarHeight;
		}
    private val isNavigationBarVisible: Boolean
        get() {
            val rectangle = Rect()
            val window: Window? = currentActivity?.window;
            window?.decorView?.getWindowVisibleDisplayFrame(rectangle)
            val statusBarHeight = rectangle.top
            return statusBarHeight != 0
        }
    private val mNavigationBarHeight: Int
        get() {
            var navigationBarHeight: Int = 0;
            if (isNavigationBarVisible) {
                val resourceId: Int = reactApplicationContext.resources.getIdentifier("navigation_bar_height", "dimen", "android")
                if (resourceId > 0) {
                    navigationBarHeight = reactApplicationContext.resources.getDimensionPixelSize(resourceId)
                }
                return navigationBarHeight;
            }
            return navigationBarHeight;
        }
    private val windowRects: List<Rect>
        get() {
            val boundings = mDisplayMask?.getBoundingRectsForRotation(rotation)
            val windowBounds = windowRect;
            val barHeight = mStatusBarHeight + mNavigationBarHeight;
            return if (boundings == null || boundings.size == 0) {
                windowBounds.bottom = windowBounds.bottom - barHeight;
                listOf(windowBounds)
            } else {
                val hingeRect = boundings[0]
                if (hingeRect.top == 0) {
                    windowBounds.bottom = windowBounds.bottom - barHeight;
                    val leftRect = Rect(0, 0, hingeRect.left, windowBounds.bottom)
                    val rightRect = Rect(hingeRect.right, 0, windowBounds.right, windowBounds.bottom)
                    listOf(leftRect, rightRect)
                } else {
                    hingeRect.bottom = hingeRect.bottom - barHeight;
                    hingeRect.top = hingeRect.top - barHeight;
                    val topRect = Rect(0, 0, windowBounds.right, hingeRect.top)
                    val bottomRect = Rect(0, hingeRect.bottom, windowBounds.right, windowBounds.bottom)
                    listOf(topRect, bottomRect)
                }
            }
        }
	private val windowRect: Rect
		get() {
			val windowRect = Rect()
			val rootView: View? = currentActivity?.window?.decorView?.rootView
			rootView?.getDrawingRect(windowRect)
			return windowRect
		}
	private val isDualScreenDevice = reactApplicationContext.packageManager.hasSystemFeature(FEATURE_NAME)
	private var mIsSpanning: Boolean = false
	private var mWindowRects: List<Rect> = emptyList()
	private var mRotation: Int = Surface.ROTATION_0

	private val onLayoutChange = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
		emitUpdateStateEvent()
	}

	override fun getName() = "DualScreenInfo"

	override fun initialize() {
		super.initialize()
		reactApplicationContext.addLifecycleEventListener(this)
		emitUpdateStateEvent()
	}

	override fun getConstants(): Map<String, Any>? {
        val constants: MutableMap<String, Any> = HashMap()
    	constants[HINGE_WIDTH_KEY] = 34
		constants[IS_DUALSCREEN_DEVICE_KEY] = isDualScreenDevice

    	return constants
    }

	override fun onHostResume() {
		val rootView: View? = currentActivity?.window?.decorView?.rootView
		rootView?.addOnLayoutChangeListener(onLayoutChange)

	}

	override fun onHostPause() {
		val rootView: View? = currentActivity?.window?.decorView?.rootView
		rootView?.removeOnLayoutChangeListener(onLayoutChange)
	}

	override fun onHostDestroy() {}

	/**
	 * Resolving a promise detecting if device is in Dual modes
	 */
	private fun isSpanning(): Boolean {
		if (windowRect.width() > 0 && windowRect.height() > 0) {
			return hinge.intersect(windowRect)
		}

		return false
	}

	private fun rotationToOrientationString(rotation : Int) : String {
		if (rotation == Surface.ROTATION_0) return "portrait"
		if (rotation == Surface.ROTATION_90) return "landscape"
		if (rotation == Surface.ROTATION_180) return "portraitFlipped"
		assert(rotation == Surface.ROTATION_270)
		return "landscapeFlipped"
	}

	private fun convertPixelsToDp(px: Int): Double {
		val metrics = reactApplicationContext.resources.displayMetrics
		return (px.toDouble() / (metrics.density))
	}

	private fun emitUpdateStateEvent() {
		if (reactApplicationContext.hasActiveCatalystInstance()) {
			// Don't emit an event to JS if the dimensions haven't changed
			val isSpanning = isSpanning()
			val newWindowRects = windowRects
			val newRotation = rotation
			if (mIsSpanning != isSpanning || mWindowRects != newWindowRects || mRotation != newRotation) {
				mIsSpanning = isSpanning
				mWindowRects = newWindowRects
				mRotation = newRotation

				val params = createMap()
				val windowRectsArray = Arguments.createArray()

				windowRects.forEach {
					val rectMap = createMap()
					rectMap.putDouble("width", convertPixelsToDp(it.right - it.left))
					rectMap.putDouble("height",  convertPixelsToDp(it.bottom - it.top))
					rectMap.putDouble("x", convertPixelsToDp(it.left))
					rectMap.putDouble("y", convertPixelsToDp(it.top))
					windowRectsArray.pushMap(rectMap)
				}

				params.putBoolean("isSpanning", isSpanning)
				params.putArray("windowRects", windowRectsArray)
				params.putString("orientation", rotationToOrientationString(mRotation))
				reactApplicationContext
						.getJSModule(RCTDeviceEventEmitter::class.java)
						.emit("didUpdateSpanning", params)
			}
		}
	}
}
