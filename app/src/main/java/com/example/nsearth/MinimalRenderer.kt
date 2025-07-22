package com.example.nsearth

import android.opengl.GLES32
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Very small renderer that clears the screen to black and draws a green triangle.
 * Used to verify that the EGL pipeline is working before loading complex models.
 */
class MinimalRenderer : GLSurfaceView.Renderer {
    private var program = 0
    private var vbo = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Compile shaders and link program
        val vsSource = """
            attribute vec4 aPos;
            void main() {
                gl_Position = aPos;
            }
        """.trimIndent()
        val fsSource = """
            precision mediump float;
            void main() {
                gl_FragColor = vec4(0.0, 1.0, 0.2, 1.0);
            }
        """.trimIndent()
        val vs = compileShader(GLES32.GL_VERTEX_SHADER, vsSource)
        val fs = compileShader(GLES32.GL_FRAGMENT_SHADER, fsSource)
        program = GLES32.glCreateProgram()
        GLES32.glAttachShader(program, vs)
        GLES32.glAttachShader(program, fs)
        GLES32.glLinkProgram(program)
        // Setup triangle VBO
        val triangle = floatArrayOf(
            0f, 0.8f, 0f,
            -0.8f, -0.8f, 0f,
            0.8f, -0.8f, 0f
        )
        val buf: FloatBuffer = ByteBuffer.allocateDirect(triangle.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(triangle).position(0)
        val ids = IntArray(1)
        GLES32.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vbo)
        GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, triangle.size * 4, buf, GLES32.GL_STATIC_DRAW)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES32.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES32.glClearColor(0f, 0f, 0f, 1f)
        GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT or GLES32.GL_DEPTH_BUFFER_BIT)
        GLES32.glUseProgram(program)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vbo)
        val loc = GLES32.glGetAttribLocation(program, "aPos")
        if (loc >= 0) {
            GLES32.glEnableVertexAttribArray(loc)
            GLES32.glVertexAttribPointer(loc, 3, GLES32.GL_FLOAT, false, 0, 0)
            GLES32.glDrawArrays(GLES32.GL_TRIANGLES, 0, 3)
            GLES32.glDisableVertexAttribArray(loc)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES32.glCreateShader(type)
        GLES32.glShaderSource(shader, source)
        GLES32.glCompileShader(shader)
        return shader
    }
} 