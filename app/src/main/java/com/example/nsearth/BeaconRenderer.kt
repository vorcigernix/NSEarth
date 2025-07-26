package com.example.nsearth

import android.content.Context
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class BeaconRenderer(private val context: Context) {

    private var program: Int = 0
    private var positionHandle: Int = 0
    private var normalHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var modelMatrixHandle: Int = 0
    private var lightDirectionHandle: Int = 0
    private var timeHandle: Int = 0
    private var colorHandle: Int = 0
    private var pulseHandle: Int = 0
    
    // Visibility state for optimizations
    private var isVisible = true

    private lateinit var mainBeaconModel: ModelLoader.Model
    private lateinit var cityBeaconModel: ModelLoader.Model

    fun setup() {
        val vertexShaderSource = ShaderUtils.readShaderFromFile(context, "shaders/beacon_vertex.glsl")
        val fragmentShaderSource = ShaderUtils.readShaderFromFile(context, "shaders/beacon_fragment.glsl")
        program = ShaderUtils.createProgram(vertexShaderSource, fragmentShaderSource)

        timeHandle = GLES20.glGetUniformLocation(program, "u_Time")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")
        modelMatrixHandle = GLES20.glGetUniformLocation(program, "u_ModelMatrix")
        lightDirectionHandle = GLES20.glGetUniformLocation(program, "u_LightDirection")
        colorHandle = GLES20.glGetUniformLocation(program, "u_Color")
        pulseHandle = GLES20.glGetUniformLocation(program, "u_Pulse")

        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        normalHandle = GLES20.glGetAttribLocation(program, "a_Normal")

        mainBeaconModel = BeaconGenerator.createBeacon(0.02f, 0.6f, 16)
        cityBeaconModel = BeaconGenerator.createBeacon(0.01f, 0.5f, 16)
    }

    fun draw(mvpMatrix: FloatArray, modelMatrix: FloatArray, lightDirection: FloatArray, time: Float, isMainBeacon: Boolean, alpha: Float, pulse: Float) {
        // Skip rendering if not visible (this should rarely happen due to caller optimizations, but it's a safety net)
        if (!isVisible) return
        
        GLES20.glUseProgram(program)

        val beaconModel = if (isMainBeacon) mainBeaconModel else cityBeaconModel

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle, 3, GLES20.GL_FLOAT, false, 0, beaconModel.vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(
            normalHandle, 3, GLES20.GL_FLOAT, false, 0, beaconModel.normalBuffer
        )

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glUniform3fv(lightDirectionHandle, 1, lightDirection, 0)
        GLES20.glUniform1f(timeHandle, time)
        GLES20.glUniform4f(colorHandle, 1.0f, 1.0f, 0.8f, alpha)
        GLES20.glUniform1f(pulseHandle, pulse)

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            beaconModel.indexCount,
            GLES20.GL_UNSIGNED_SHORT,
            beaconModel.indexBuffer
        )

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
    
    fun onVisibilityChanged(visible: Boolean) {
        isVisible = visible
        // BeaconRenderer doesn't have heavy resources to manage like textures,
        // but we track visibility for potential future optimizations
    }

    fun release() {
        GLES20.glDeleteProgram(program)
    }
}