package com.example.nsearth

import javax.microedition.khronos.egl.EGLConfig

/**
 * A modern renderer interface that removes the legacy GL10 parameter.
 */
interface GLESRenderer {
    fun onSurfaceCreated(config: EGLConfig?)
    fun onSurfaceChanged(width: Int, height: Int)
    fun onDrawFrame()
    fun onVisibilityChanged(visible: Boolean)
    fun release()
}
