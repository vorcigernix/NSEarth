package com.example.nsearth

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI

class SolidSphereRenderer(private val context: Context) : GLSurfaceView.Renderer {
    companion object {
        private const val TAG = "SolidSphereRenderer"
        private const val ROT_SPEED = 0.3f
    }

    private var program = 0
    private var posLoc = 0
    private var mvpLoc = 0

    private lateinit var vertexBuf: FloatBuffer
    private lateinit var indexBuf: ShortBuffer
    private var indexCount = 0

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val temp = FloatArray(16)
    private val mvp = FloatArray(16)

    private var lastTime = System.currentTimeMillis()
    private var angle = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vs = """
            attribute vec3 aPos;
            uniform mat4 uMVP;
            void main(){
              gl_Position = uMVP * vec4(aPos,1.0);
            }
        """
        val fs = """
            precision mediump float;
            void main(){
               gl_FragColor = vec4(0.1,0.6,1.0,1.0);
            }
        """
        program = compile(vs, fs)
        posLoc = GLES20.glGetAttribLocation(program, "aPos")
        mvpLoc = GLES20.glGetUniformLocation(program, "uMVP")

        // create sphere geometry
        val sphere = SphereGenerator.createSphere(1.5f, 40, 20)
        indexCount = sphere.indexCount
        vertexBuf = ByteBuffer.allocateDirect(sphere.vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuf.put(sphere.vertices).position(0)
        indexBuf = ByteBuffer.allocateDirect(sphere.indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        indexBuf.put(sphere.indices).position(0)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glClearColor(0f,0f,0f,1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0,0,width,height)
        val aspect = width.toFloat()/height.toFloat()
        MathUtils.createPerspectiveMatrix(MathUtils.toRadians(45f), aspect,0.1f,10f, proj)
        MathUtils.createLookAtMatrix(0f,0f,4f, 0f,0f,0f, 0f,1f,0f, view)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.currentTimeMillis()
        val dt = (now-lastTime)/1000f
        lastTime = now
        angle += ROT_SPEED*dt*60f
        if(angle>360) angle-=360
        MathUtils.createRotationYMatrix(angle* (PI /180f).toFloat(), model)
        MathUtils.multiplyMatrices(view, model, temp)
        MathUtils.multiplyMatrices(proj, temp, mvp)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0)
        GLES20.glEnableVertexAttribArray(posLoc)
        vertexBuf.position(0)
        GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,0,vertexBuf)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,indexCount,GLES20.GL_UNSIGNED_SHORT,indexBuf)
        GLES20.glDisableVertexAttribArray(posLoc)
    }

    private fun compile(vsSrc:String, fsSrc:String):Int{
        fun comp(type:Int,src:String):Int{
            val s=GLES20.glCreateShader(type)
            GLES20.glShaderSource(s,src)
            GLES20.glCompileShader(s)
            return s
        }
        val vs=comp(GLES20.GL_VERTEX_SHADER,vsSrc)
        val fs=comp(GLES20.GL_FRAGMENT_SHADER,fsSrc)
        val p=GLES20.glCreateProgram()
        GLES20.glAttachShader(p,vs)
        GLES20.glAttachShader(p,fs)
        GLES20.glLinkProgram(p)
        return p
    }
} 