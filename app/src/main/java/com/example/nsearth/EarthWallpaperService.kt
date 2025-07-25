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
        private var renderer: GLSurfaceView.Renderer? = null
        private val lock = ReentrantLock()

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            surfaceHolder.addCallback(this)
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
            glThread?.setRunning(visible)
        }

        override fun onDestroy() {
            super.onDestroy()
            glThread?.requestExitAndWait()
            // cleanup() removed from EarthRenderer minimal version
        }
    }

    /** GL rendering thread directly tied to the wallpaper surface **/
    private class GLThread(private val holder: SurfaceHolder, private val renderer: GLSurfaceView.Renderer) : Thread() {
        private val lock = ReentrantLock()
        private val condition = lock.newCondition()
        private var running = false
        private var shouldExit = false
        private var width = 0
        private var height = 0

        private var eglDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface = EGL14.EGL_NO_SURFACE

        fun setRunning(run: Boolean) {
            lock.withLock {
                running = run
                condition.signalAll()
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
            renderer.onSurfaceCreated(null, null)
            renderer.onSurfaceChanged(null, width, height)

            var lastW = -1
            var lastH = -1
            while (true) {
                var exitNow = false
                var curW:Int
                var curH:Int
                lock.withLock {
                    while (!running && !shouldExit) {
                        condition.await()
                    }
                    if (shouldExit) exitNow = true
                    curW = width
                    curH = height
                }
                if (exitNow) break

                if (curW != lastW || curH != lastH) {
                    renderer.onSurfaceChanged(null, curW, curH)
                    lastW = curW
                    lastH = curH
                }

                renderer.onDrawFrame(null)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                sleep(16)
            }

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