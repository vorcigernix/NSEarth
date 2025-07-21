package com.example.nsearth

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.Matrix

/**
 * High-performance OpenGL ES 2.0 renderer for 3D Earth wallpaper
 */
class EarthRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private val vertexShaderCode = """
        attribute vec4 vPosition;
        uniform mat4 uMVPMatrix;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
        }
    """
    private val fragmentShaderCode = """
        precision mediump float;
        uniform vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
    """
    // Replace triangleCoords with cube vertex positions
    private val cubeVertices = floatArrayOf(
        // Front face
        -1f, -1f,  1f,
         1f, -1f,  1f,
         1f,  1f,  1f,
        -1f,  1f,  1f,
        // Back face
        -1f, -1f, -1f,
        -1f,  1f, -1f,
         1f,  1f, -1f,
         1f, -1f, -1f
    )
    private val cubeIndices = shortArrayOf(
        0, 1, 2, 2, 3, 0, // Front
        4, 5, 6, 6, 7, 4, // Back
        0, 3, 5, 5, 4, 0, // Left
        1, 7, 6, 6, 2, 1, // Right
        3, 2, 6, 6, 5, 3, // Top
        0, 4, 7, 7, 1, 0  // Bottom
    )
    private val color = floatArrayOf(0.63671875f, 0.76953125f, 0.22265625f, 1.0f)
    private lateinit var cubeVertexBuffer: FloatBuffer
    private lateinit var cubeIndexBuffer: ShortBuffer
    private var mProgram = 0
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private var angle = 0f

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        // Setup cube vertex buffer
        val vb = ByteBuffer.allocateDirect(cubeVertices.size * 4)
        vb.order(ByteOrder.nativeOrder())
        cubeVertexBuffer = vb.asFloatBuffer()
        cubeVertexBuffer.put(cubeVertices)
        cubeVertexBuffer.position(0)
        // Setup cube index buffer
        val ib = ByteBuffer.allocateDirect(cubeIndices.size * 2)
        ib.order(ByteOrder.nativeOrder())
        cubeIndexBuffer = ib.asShortBuffer()
        cubeIndexBuffer.put(cubeIndices)
        cubeIndexBuffer.position(0)
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
        Matrix.setIdentityM(mvpMatrix, 0)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(mProgram)
        val positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        cubeVertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, cubeVertexBuffer)
        val colorHandle = GLES20.glGetUniformLocation(mProgram, "vColor")
        GLES20.glUniform4fv(colorHandle, 1, color, 0)
        val mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        // Model matrix: rotate around Y axis
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, angle, 0f, 1f, 0f)
        // MVP = projection * view * model
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        cubeIndexBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, cubeIndices.size, GLES20.GL_UNSIGNED_SHORT, cubeIndexBuffer)
        GLES20.glDisableVertexAttribArray(positionHandle)
        // Animate
        angle += 1f
        if (angle > 360f) angle -= 360f
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        // Set up a perspective projection matrix
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 1f, 10f)
        // Set up a camera/view matrix: eye at (0,0,5), looking at (0,0,0), up (0,1,0)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
} 