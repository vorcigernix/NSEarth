package com.example.nsearth

import android.opengl.EGL14
import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class EarthWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return EarthEngine()
    }

    inner class EarthEngine : Engine(), SurfaceHolder.Callback {
        private var glThread: GLThread? = null
        private var renderer: GLESRenderer? = null
        private val lock = ReentrantLock()

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            surfaceHolder.addCallback(this)
        }

        // Adaptive frame rate control
        fun setFrameRate(fps: Int) {
            glThread?.setTargetFrameRate(fps)
        }
        
        // Automatically adjust frame rate based on visibility and power state
        private fun updateFrameRateForState(visible: Boolean) {
            val frameRate = when {
                !visible -> 1 // Very low when not visible (safety fallback)
                else -> 30 // Smooth 30 FPS when visible - Earth rotation is slow enough that 30 FPS looks great
            }
            setFrameRate(frameRate)
        }

        // SurfaceHolder.Callback implementations
        override fun surfaceCreated(holder: SurfaceHolder) {
            // Surface created - initializing OpenGL
            Log.d(TAG, "Surface created")
            // Use the new EarthRenderer with texture and lighting
            renderer = EarthRenderer(this@EarthWallpaperService)
            glThread = GLThread(holder, renderer!!)
            glThread?.start()
            glThread?.setRunning(true)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.d(TAG, "Surface changed ${width}x$height")
            glThread?.onWindowResize(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "Surface destroyed")
            glThread?.requestExitAndWait()
            glThread = null
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            updateFrameRateForState(visible)
            glThread?.setRunning(visible)
        }

        override fun onDestroy() {
            super.onDestroy()
            glThread?.requestExitAndWait()
            // cleanup() removed from EarthRenderer minimal version
        }
    }

    /** GL rendering thread directly tied to the wallpaper surface **/
    private class GLThread(private val holder: SurfaceHolder, private val renderer: GLESRenderer) : Thread() {
        private val lock = ReentrantLock()
        private val condition = lock.newCondition()
        private var running = false
        private var shouldExit = false
        private var width = 0
        private var height = 0
        
        // Add adaptive frame rate variables
        private var targetFrameRate = 60 // Default to 60 FPS
        private var frameTimeMs = 16L // 60 FPS = ~16ms per frame

        private var eglDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface = EGL14.EGL_NO_SURFACE

        fun setRunning(run: Boolean) {
            lock.withLock {
                running = run
                // Notify the renderer about visibility changes
                renderer.onVisibilityChanged(run)
                condition.signalAll()
            }
        }

        fun setTargetFrameRate(fps: Int) {
            lock.withLock {
                targetFrameRate = fps.coerceIn(1, 60)
                frameTimeMs = 1000L / targetFrameRate
            }
        }

        fun onWindowResize(w: Int, h: Int) {
            lock.withLock {
                width = w
                height = h
            }
        }

        fun requestExitAndWait() {
            lock.withLock {
                shouldExit = true
                condition.signalAll()
            }
            join()
        }

        override fun run() {
            initEGL()
            renderer.onSurfaceCreated(null)
            renderer.onSurfaceChanged(width, height)

            var lastW = -1
            var lastH = -1
            var lastFrameTime = System.currentTimeMillis()
            
            while (true) {
                var exitNow = false
                var isRunning: Boolean
                var curW: Int
                var curH: Int
                var currentFrameTimeMs: Long
                
                lock.withLock {
                    // Wait while not running and not exiting - this is the key optimization
                    while (!running && !shouldExit) {
                        condition.await() // Thread sleeps here when wallpaper is not visible
                    }
                    if (shouldExit) exitNow = true
                    isRunning = running
                    curW = width
                    curH = height
                    currentFrameTimeMs = frameTimeMs
                }
                
                if (exitNow) break

                // Only render when visible
                if (isRunning) {
                    if (curW != lastW || curH != lastH) {
                        renderer.onSurfaceChanged(curW, curH)
                        lastW = curW
                        lastH = curH
                    }

                    val currentTime = System.currentTimeMillis()
                    val deltaTime = currentTime - lastFrameTime
                    
                    // Only render if enough time has passed for target frame rate
                    if (deltaTime >= currentFrameTimeMs) {
                        renderer.onDrawFrame()
                        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                        lastFrameTime = currentTime
                    } else {
                        // Sleep for the remaining time to maintain target frame rate
                        val sleepTime = currentFrameTimeMs - deltaTime
                        if (sleepTime > 0) {
                            sleep(sleepTime)
                        }
                    }
                } else {
                    // If not running, just wait (this shouldn't happen due to the while loop above,
                    // but it's a safety net)
                    sleep(100)
                }
            }

            renderer.release() // Release renderer resources
            cleanupEGL()
        }

        private fun initEGL() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            EGL14.eglInitialize(eglDisplay, null, 0, null, 0)
            val attrib = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE)
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val num = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attrib, 0, configs, 0, 1, num, 0)
            val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrib,0)
            val surfAttrib = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], holder, surfAttrib,0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun cleanupEGL() {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
    }

    companion object { private const val TAG = "EarthWallpaperService" }
} 