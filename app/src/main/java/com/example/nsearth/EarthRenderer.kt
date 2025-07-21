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
 * High-performance OpenGL ES 2.0 renderer for 3D Earth wallpaper using procedural sphere
 */
class EarthRenderer(private val context: Context) : GLSurfaceView.Renderer {
    
    // Textured sphere shaders with lighting
    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec3 vNormal;
        attribute vec2 vTexCoord;
        uniform mat4 uMVPMatrix;
        varying vec3 normalInterp;
        varying vec2 texCoordInterp;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            normalInterp = normalize(vNormal);
            texCoordInterp = vTexCoord;
        }
    """
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec3 normalInterp;
        varying vec2 texCoordInterp;
        uniform sampler2D uTexture;
        void main() {
            // Sample the texture
            vec3 textureColor = texture2D(uTexture, texCoordInterp).rgb;
            
            // Main directional light (sun)
            vec3 lightDirection = normalize(vec3(0.5, 0.3, 1.0)); // Light from front-right-top
            float NdotL = dot(normalInterp, lightDirection);
            
            // Diffuse lighting with smoother transition
            float diffuse = max(NdotL, 0.0);
            
            // Ambient lighting (space illumination)
            float ambient = 0.15;
            
            // Rim lighting for atmosphere effect
            vec3 viewDirection = normalize(vec3(0.0, 0.0, 1.0));
            float rim = 1.0 - max(dot(normalInterp, viewDirection), 0.0);
            rim = pow(rim, 2.0) * 0.3; // Soft atmospheric glow
            
            // Combine lighting
            float totalLight = ambient + diffuse + rim;
            totalLight = min(totalLight, 1.2); // Cap the brightness
            
            vec3 finalColor = textureColor * totalLight;
            
            // Add slight blue tint to shadow areas (atmospheric scattering)
            if (diffuse < 0.3) {
                finalColor += vec3(0.05, 0.1, 0.2) * (0.3 - diffuse);
            }
            
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

    private lateinit var modelVertexBuffer: FloatBuffer
    private lateinit var modelNormalBuffer: FloatBuffer
    private lateinit var modelTexCoordBuffer: FloatBuffer
    private lateinit var modelIndexBuffer: ShortBuffer
    private var modelIndexCount = 0
    private var textureId = 0

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        Log.d("NSEarthDebug", "=== onSurfaceCreated: EarthRenderer started ===")
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE) // Enable face culling for 3D appearance
        GLES20.glCullFace(GLES20.GL_BACK)
        
        // Enable blending for smoother edges
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
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
        textureId = TextureUtils.loadTexture(context, "earth_clouds.jpg")
        Log.d("NSEarthDebug", "Loaded texture ID: $textureId")
        
        // Setup model index buffer
        val ib = ByteBuffer.allocateDirect(model.indices.size * 2)
        ib.order(ByteOrder.nativeOrder())
        modelIndexBuffer = ib.asShortBuffer()
        modelIndexBuffer.put(model.indices)
        modelIndexBuffer.position(0)
        modelIndexCount = model.indices.size
        
        // Setup shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
        Matrix.setIdentityM(mvpMatrix, 0)
        
        Log.d("NSEarthDebug", "=== Setup complete: Procedural sphere ready ===")
    }

    override fun onDrawFrame(unused: GL10?) {
        frameCount++
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(mProgram)
        
        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        val textureHandle = GLES20.glGetUniformLocation(mProgram, "uTexture")
        GLES20.glUniform1i(textureHandle, 0)
        
        val positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        modelVertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, modelVertexBuffer)

        val normalHandle = GLES20.glGetAttribLocation(mProgram, "vNormal")
        GLES20.glEnableVertexAttribArray(normalHandle)
        modelNormalBuffer.position(0)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, modelNormalBuffer)

        val texCoordHandle = GLES20.glGetAttribLocation(mProgram, "vTexCoord")
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        modelTexCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 2 * 4, modelTexCoordBuffer)

        val mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, angle, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        modelIndexBuffer.position(0)
        // Debug draw parameters (first frame only)
        if (frameCount == 1) {
            Log.d("NSEarthDebug", "=== FIRST DRAW: count=$modelIndexCount, idxBuf=${modelIndexBuffer.capacity()}, vtxBuf=${modelVertexBuffer.capacity()} ===")
            Log.d("NSEarthDebug", "Final MVP matrix: ${mvpMatrix.take(4)}")
            Log.d("NSEarthDebug", "Handles: position=$positionHandle, mvp=$mvpMatrixHandle")
        }
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, modelIndexCount, GLES20.GL_UNSIGNED_SHORT, modelIndexBuffer)
        // Check for OpenGL errors
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR && frameCount % 60 == 0) {
            Log.e("NSEarthDebug", "OpenGL error after draw: $error")
        }
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        // Animate (much slower rotation)
        angle += 0.2f  // Reduced from 1f to 0.2f for slower rotation
        if (angle > 360f) angle -= 360f
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        // Set up a perspective projection matrix
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 1f, 10f)
        // Set up a camera/view matrix: eye at (0,0,6), looking at (0,0,0), up (0,1,0)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 6f, 0f, 0f, 0f, 0f, 1f, 0f)
        Log.d("NSEarthDebug", "=== Surface changed: ${width}x${height}, ratio=$ratio ===")
        Log.d("NSEarthDebug", "Projection matrix: ${projectionMatrix.take(4)}")
        Log.d("NSEarthDebug", "View matrix: ${viewMatrix.take(4)}")
    }

    fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
} 