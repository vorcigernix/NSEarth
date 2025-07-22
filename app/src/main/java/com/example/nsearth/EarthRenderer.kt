package com.example.nsearth

import android.content.Context
import android.opengl.GLES32
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
 * High-performance OpenGL ES 2.0 renderer for 3D Earth wallpaper using procedural sphere
 */
class EarthRenderer(private val context: Context) : GLSurfaceView.Renderer {
    
    // Textured sphere shaders with lighting
    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec3 vNormal;
        attribute vec2 vTexCoord;
        uniform mat4 uMVPMatrix;
        uniform mat4 uModelMatrix;
        uniform mat4 uViewMatrix;
        varying vec3 vNormalView;
        varying vec2 vTexCoordOut;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            // Transform to view space (like the working normal calculation)
            vNormalView = normalize((uViewMatrix * uModelMatrix * vec4(vNormal, 0.0)).xyz);
            vTexCoordOut = vTexCoord;
        }
    """

    private val fragmentShaderCode = """
        precision highp float;
        varying vec3 vNormalView;
        varying vec2 vTexCoordOut;
        uniform sampler2D uTexture;
        void main() {
            vec3 textureColor = texture2D(uTexture, vTexCoordOut).rgb;
            vec3 normal = normalize(vNormalView);
            
            // Light from the right side
            vec3 lightDir = normalize(vec3(1.0, 0.0, 0.0));
            float diff = max(dot(normal, lightDir), 0.0);
            float ambient = 0.1;
            float totalLight = ambient + diff * 0.9;
            
            // Rim lighting using fixed view direction
            vec3 fixedViewDir = vec3(0.0, 0.0, -1.0); // Camera looking down -Z
            float rim = 1.0 - max(dot(normal, fixedViewDir), 0.0);
            rim = smoothstep(0.6, 1.0, rim);
            vec3 rimColor = vec3(0.3, 0.5, 1.0);
            
            vec3 finalColor = textureColor * totalLight + rimColor * rim * 0.2;
            gl_FragColor = vec4(finalColor, 1.0);
        }
    """
    
    private var mProgram = 0
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private var angle = 0f
    private var frameCount = 0
    private var time = 0f

    private lateinit var modelVertexBuffer: FloatBuffer
    private lateinit var modelNormalBuffer: FloatBuffer
    private lateinit var modelTexCoordBuffer: FloatBuffer
    private lateinit var modelIndexBuffer: ShortBuffer
    private var modelIndexCount = 0
    private var textureId = 0

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        Log.d("NSEarthDebug", "=== onSurfaceCreated: EarthRenderer started ===")
        GLES32.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES32.glEnable(GLES32.GL_DEPTH_TEST)
        GLES32.glDepthFunc(GLES32.GL_LEQUAL)
        GLES32.glEnable(GLES32.GL_CULL_FACE) // Enable face culling for 3D appearance
        GLES32.glCullFace(GLES32.GL_BACK)
        
        // Enable blending for smoother edges
        GLES32.glEnable(GLES32.GL_BLEND)
        GLES32.glBlendFunc(GLES32.GL_SRC_ALPHA, GLES32.GL_ONE_MINUS_SRC_ALPHA)
        
        // Generate procedural sphere with higher resolution for smoother appearance
        Log.d("NSEarthDebug", "Generating procedural sphere...")
        
        val sphereData = SphereGenerator.createSphere(1.5f, 64, 32) // Increased from 32,16 to 64,32
        val model = ModelLoader.Model(
            vertices = sphereData.vertices,
            texCoords = sphereData.texCoords,
            normals = sphereData.normals,
            indices = sphereData.indices,
            vertexCount = sphereData.vertexCount,
            indexCount = sphereData.indexCount
        )
        
        Log.d("NSEarthDebug", "Generated sphere: vertices=${model.vertices.size/3}, indices=${model.indices.size}")
        Log.d("NSEarthDebug", "First vertex: [${model.vertices[0]}, ${model.vertices[1]}, ${model.vertices[2]}]")
        // Setup model vertex buffer
        val vb = ByteBuffer.allocateDirect(model.vertices.size * 4)
        vb.order(ByteOrder.nativeOrder())
        modelVertexBuffer = vb.asFloatBuffer()
        modelVertexBuffer.put(model.vertices)
        modelVertexBuffer.position(0)
        
        // Setup model normal buffer
        val nb = ByteBuffer.allocateDirect(model.normals.size * 4)
        nb.order(ByteOrder.nativeOrder())
        modelNormalBuffer = nb.asFloatBuffer()
        modelNormalBuffer.put(model.normals)
        modelNormalBuffer.position(0)
        
        // Setup texture coordinate buffer
        val tb = ByteBuffer.allocateDirect(model.texCoords.size * 4)
        tb.order(ByteOrder.nativeOrder())
        modelTexCoordBuffer = tb.asFloatBuffer()
        modelTexCoordBuffer.put(model.texCoords)
        modelTexCoordBuffer.position(0)
        
        // Load texture
        textureId = TextureUtils.loadTexture(context, "earth_cloudless.jpg")
        Log.d("NSEarthDebug", "Loaded cloudless texture ID: $textureId")
        
        // Setup model index buffer
        val ib = ByteBuffer.allocateDirect(model.indices.size * 2)
        ib.order(ByteOrder.nativeOrder())
        modelIndexBuffer = ib.asShortBuffer()
        modelIndexBuffer.put(model.indices)
        modelIndexBuffer.position(0)
        modelIndexCount = model.indices.size
        
        // Setup shaders
        val vertexShader = loadShader(GLES32.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES32.GL_FRAGMENT_SHADER, fragmentShaderCode)
        mProgram = GLES32.glCreateProgram().also {
            GLES32.glAttachShader(it, vertexShader)
            GLES32.glAttachShader(it, fragmentShader)
            GLES32.glLinkProgram(it)
            
            // Check for linking errors
            val linked = IntArray(1)
            GLES32.glGetProgramiv(it, GLES32.GL_LINK_STATUS, linked, 0)
            if (linked[0] == 0) {
                val error = GLES32.glGetProgramInfoLog(it)
                Log.e("NSEarthDebug", "Program linking error: $error")
                GLES32.glDeleteProgram(it)
                throw RuntimeException("Program linking failed: $error")
            }
        }
        Matrix.setIdentityM(mvpMatrix, 0)
        
        Log.d("NSEarthDebug", "=== Setup complete: Procedural sphere ready ===")
    }

    override fun onDrawFrame(unused: GL10?) {
        frameCount++
        GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT or GLES32.GL_DEPTH_BUFFER_BIT)
        GLES32.glUseProgram(mProgram)
        
        // Bind texture
        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, textureId)
        val textureHandle = GLES32.glGetUniformLocation(mProgram, "uTexture")
        GLES32.glUniform1i(textureHandle, 0)
        
        val positionHandle = GLES32.glGetAttribLocation(mProgram, "vPosition")
        GLES32.glEnableVertexAttribArray(positionHandle)
        modelVertexBuffer.position(0)
        GLES32.glVertexAttribPointer(positionHandle, 3, GLES32.GL_FLOAT, false, 3 * 4, modelVertexBuffer)

        val normalHandle = GLES32.glGetAttribLocation(mProgram, "vNormal")
        GLES32.glEnableVertexAttribArray(normalHandle)
        modelNormalBuffer.position(0)
        GLES32.glVertexAttribPointer(normalHandle, 3, GLES32.GL_FLOAT, false, 3 * 4, modelNormalBuffer)

        val texCoordHandle = GLES32.glGetAttribLocation(mProgram, "vTexCoord")
        GLES32.glEnableVertexAttribArray(texCoordHandle)
        modelTexCoordBuffer.position(0)
        GLES32.glVertexAttribPointer(texCoordHandle, 2, GLES32.GL_FLOAT, false, 2 * 4, modelTexCoordBuffer)

        // Set uniforms
        val mvpMatrixHandle = GLES32.glGetUniformLocation(mProgram, "uMVPMatrix")
        val modelMatrixHandle = GLES32.glGetUniformLocation(mProgram, "uModelMatrix")
        val viewMatrixHandle = GLES32.glGetUniformLocation(mProgram, "uViewMatrix")
        
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, angle, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        GLES32.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES32.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)
        GLES32.glUniformMatrix4fv(viewMatrixHandle, 1, false, viewMatrix, 0)
        modelIndexBuffer.position(0)
        // Debug draw parameters (first frame only)
        if (frameCount == 1) {
            Log.d("NSEarthDebug", "=== FIRST DRAW: count=$modelIndexCount, idxBuf=${modelIndexBuffer.capacity()}, vtxBuf=${modelVertexBuffer.capacity()} ===")
            Log.d("NSEarthDebug", "Final MVP matrix: ${mvpMatrix.take(4)}")
            Log.d("NSEarthDebug", "Handles: position=$positionHandle, mvp=$mvpMatrixHandle")
        }
        GLES32.glDrawElements(GLES32.GL_TRIANGLES, modelIndexCount, GLES32.GL_UNSIGNED_SHORT, modelIndexBuffer)
        // Check for OpenGL errors
        val error = GLES32.glGetError()
        if (error != GLES32.GL_NO_ERROR && frameCount % 60 == 0) {
            Log.e("NSEarthDebug", "OpenGL error after draw: $error")
        }
        GLES32.glDisableVertexAttribArray(positionHandle)
        GLES32.glDisableVertexAttribArray(normalHandle)
        GLES32.glDisableVertexAttribArray(texCoordHandle)
        // Animate (very slow rotation between continents)
        angle += 0.05f  // Very slow rotation to appreciate continental details
        if (angle > 360f) angle -= 360f
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES32.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        // Set up a perspective projection matrix
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 1f, 15f)
        // Set up a camera/view matrix: eye at (0,0,10), looking at (0,0,0), up (0,1,0) - zoomed out with padding
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 10f, 0f, 0f, 0f, 0f, 1f, 0f)
        Log.d("NSEarthDebug", "=== Surface changed: ${width}x${height}, ratio=$ratio ===")
        Log.d("NSEarthDebug", "Projection matrix: ${projectionMatrix.take(4)}")
        Log.d("NSEarthDebug", "View matrix: ${viewMatrix.take(4)}")
    }

    fun loadShader(type: Int, shaderCode: String): Int {
        return GLES32.glCreateShader(type).also { shader ->
            GLES32.glShaderSource(shader, shaderCode)
            GLES32.glCompileShader(shader)
            
            // Check for compilation errors
            val compiled = IntArray(1)
            GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val error = GLES32.glGetShaderInfoLog(shader)
                Log.e("NSEarthDebug", "Shader compilation error: $error")
                Log.e("NSEarthDebug", "Shader code: $shaderCode")
                GLES32.glDeleteShader(shader)
                throw RuntimeException("Shader compilation failed: $error")
            }
        }
    }
} 