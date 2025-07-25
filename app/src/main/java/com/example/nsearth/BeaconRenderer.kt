package com.example.nsearth

import android.content.Context
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class BeaconRenderer(private val context: Context) {

    private var vertexBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null
    private var indexCount: Int = 0

    private var program: Int = 0
    private var timeHandle: Int = 0

    private val beaconModel: ModelLoader.Model = BeaconGenerator.createBeacon(0.01f, 0.5f, 16)

    fun setup() {
        val vertexShaderSource = ShaderUtils.readShaderFromFile(context, "shaders/beacon_vertex.glsl")
        val fragmentShaderSource = ShaderUtils.readShaderFromFile(context, "shaders/beacon_fragment.glsl")
        program = ShaderUtils.createProgram(vertexShaderSource, fragmentShaderSource)
        timeHandle = GLES20.glGetUniformLocation(program, "u_Time")
        loadBeaconModel()
    }

    private fun loadBeaconModel() {
        indexCount = beaconModel.indexCount

        vertexBuffer = ByteBuffer.allocateDirect(beaconModel.vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .put(beaconModel.vertices).position(0) as FloatBuffer

        normalBuffer = ByteBuffer.allocateDirect(beaconModel.normals.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .put(beaconModel.normals).position(0) as FloatBuffer

        indexBuffer = ByteBuffer.allocateDirect(beaconModel.indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
            .put(beaconModel.indices).position(0) as ShortBuffer
    }

    fun draw(mvpMatrix: FloatArray, modelMatrix: FloatArray, lightDirection: FloatArray, time: Float) {
        GLES20.glUseProgram(program)

        val positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer
        )

        val normalHandle = GLES20.glGetAttribLocation(program, "a_Normal")
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(
            normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer
        )

        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        val modelMatrixHandle = GLES20.glGetUniformLocation(program, "u_ModelMatrix")
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)

        val lightDirectionHandle = GLES20.glGetUniformLocation(program, "u_LightDirection")
        GLES20.glUniform3fv(lightDirectionHandle, 1, lightDirection, 0)

        GLES20.glUniform1f(timeHandle, time)

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            indexCount,
            GLES20.GL_UNSIGNED_SHORT,
            indexBuffer
        )

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
} 