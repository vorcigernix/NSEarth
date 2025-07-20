package com.example.nsearth

import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import kotlinx.coroutines.*

/**
 * Live wallpaper service for 3D Earth OpenGL ES 2.0 rendering
 */
class EarthWallpaperService : WallpaperService() {
    
    companion object {
        private const val TAG = "EarthWallpaperService"
    }
    
    override fun onCreateEngine(): Engine {
        Log.d(TAG, "Creating wallpaper engine")
        return EarthWallpaperEngine()
    }
    
    inner class EarthWallpaperEngine : Engine() {
        
        private var renderer: EarthRenderer? = null
        private var glSurfaceView: WallpaperGLSurfaceView? = null
        private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        
        private var isVisible = false
        
        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            Log.d(TAG, "Engine created")
            
            // Create renderer and GLSurfaceView
            renderer = EarthRenderer(this@EarthWallpaperService)
            glSurfaceView = WallpaperGLSurfaceView().apply {
                setEGLContextClientVersion(2)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
            
            Log.d(TAG, "Wallpaper engine initialized")
        }
        
        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.d(TAG, "Visibility changed: $visible")
            
            isVisible = visible
            
            glSurfaceView?.let { view ->
                if (visible) {
                    view.onResume()
                } else {
                    view.onPause()
                }
            }
        }
        
        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            Log.d(TAG, "Surface created")
            
            glSurfaceView?.onWallpaperSurfaceCreated(holder)
        }
        
        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.d(TAG, "Surface changed: ${width}x${height}, format: $format")
            
            glSurfaceView?.onWallpaperSurfaceChanged(holder, format, width, height)
        }
        
        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            Log.d(TAG, "Surface destroyed")
            
            glSurfaceView?.onWallpaperSurfaceDestroyed(holder)
        }
        
        override fun onDestroy() {
            Log.d(TAG, "Engine destroying")
            
            glSurfaceView?.onPause()
            renderer?.cleanup()
            
            // Cancel any ongoing coroutines
            serviceScope.cancel()
            
            glSurfaceView = null
            renderer = null
            
            super.onDestroy()
            Log.d(TAG, "Engine destroyed")
        }
        
        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            
            // Optional: Adjust camera or rotation based on home screen scrolling
            // For now, we'll keep the Earth centered and rotating consistently
        }
        
        /**
         * Check if the wallpaper is currently active and visible
         */
        fun isActiveAndVisible(): Boolean = isVisible
    }

    /**
     * Custom GLSurfaceView for wallpaper rendering
     */
    private inner class WallpaperGLSurfaceView : GLSurfaceView(this@EarthWallpaperService) {
        
        private val TAG = "WallpaperGLSurfaceView"
        private var wallpaperSurfaceHolder: SurfaceHolder? = null
        
        override fun getHolder(): SurfaceHolder = wallpaperSurfaceHolder ?: super.getHolder()
        
        fun onWallpaperSurfaceCreated(holder: SurfaceHolder?) {
            Log.d(TAG, "GLSurfaceView surface created")
            wallpaperSurfaceHolder = holder
            holder?.let { super.surfaceCreated(it) }
        }
        
        fun onWallpaperSurfaceDestroyed(holder: SurfaceHolder?) {
            Log.d(TAG, "GLSurfaceView surface destroyed")
            holder?.let { super.surfaceDestroyed(it) }
            wallpaperSurfaceHolder = null
        }
        
        fun onWallpaperSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            Log.d(TAG, "GLSurfaceView surface changed: ${width}x${height}")
            wallpaperSurfaceHolder = holder
            holder?.let { super.surfaceChanged(it, format, width, height) }
        }
    }
} 