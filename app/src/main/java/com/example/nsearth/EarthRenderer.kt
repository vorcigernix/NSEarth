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

/**
 * High-performance OpenGL ES 2.0 renderer for 3D Earth wallpaper
 */
class EarthRenderer(private val context: Context) : GLSurfaceView.Renderer {
    
    companion object {
        private const val TAG = "EarthRenderer"
        
        // Rendering settings
        private const val ROTATION_SPEED = 0.5f // degrees per frame
        private const val TARGET_FPS = 60f
        private const val FRAME_TIME_MS = 1000f / TARGET_FPS
        
        // Camera settings
        private const val FOV_DEGREES = 45f
        private const val CAMERA_DISTANCE = 3.5f
        private const val NEAR_PLANE = 0.1f
        private const val FAR_PLANE = 10f
        
        // Lighting
        private val LIGHT_DIRECTION = floatArrayOf(-1f, -0.5f, -1f)
        private val AMBIENT_COLOR = floatArrayOf(0.3f, 0.3f, 0.4f)
    }
    
    // OpenGL resources
    private var shaderProgram = 0
    private var earthTexture = 0
    private var earthModel: ModelLoader.Model? = null
    
    // Vertex buffers
    private var vertexBuffer: FloatBuffer? = null
    private var texCoordBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null
    
    // Shader attribute/uniform locations
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var normalHandle = 0
    private var mvpMatrixHandle = 0
    private var modelMatrixHandle = 0
    private var lightDirectionHandle = 0
    private var textureHandle = 0
    private var ambientColorHandle = 0
    
    // Matrices
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)
    
    // Animation
    private var rotationAngle = 0f
    private var lastFrameTime = System.currentTimeMillis()
    
    // Performance monitoring
    private var frameCount = 0
    private var fpsTimer = System.currentTimeMillis()
    
    // Initialization flag
    private var isInitialized = false
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "Surface created - initializing OpenGL")
        
        // Log OpenGL info
        Log.d(TAG, "GL Version: ${GLES20.glGetString(GLES20.GL_VERSION)}")
        Log.d(TAG, "GL Vendor: ${GLES20.glGetString(GLES20.GL_VENDOR)}")
        Log.d(TAG, "GL Renderer: ${GLES20.glGetString(GLES20.GL_RENDERER)}")
        
        // Enable features for better quality
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES20.GL_CCW)
        
        // Set clear color to space black
        GLES20.glClearColor(0.05f, 0.05f, 0.1f, 1.0f)
        
        // Initialize resources
        initializeResources()
        Log.d(TAG, "Surface creation complete, initialized: $isInitialized")
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: ${width}x${height}")
        
        GLES20.glViewport(0, 0, width, height)
        
        // Calculate projection matrix
        val aspectRatio = width.toFloat() / height.toFloat()
        MathUtils.createPerspectiveMatrix(
            MathUtils.toRadians(FOV_DEGREES),
            aspectRatio,
            NEAR_PLANE,
            FAR_PLANE,
            projectionMatrix
        )
        
        // Setup camera
        MathUtils.createLookAtMatrix(
            0f, 0f, CAMERA_DISTANCE,  // eye
            0f, 0f, 0f,               // center
            0f, 1f, 0f,               // up
            viewMatrix
        )
    }
    
    override fun onDrawFrame(gl: GL10?) {
        if (!isInitialized) {
            Log.w(TAG, "onDrawFrame called but renderer not initialized")
            return
        }
        
        Log.d(TAG, "Drawing frame - rotation: $rotationAngle")
        
        // Clear buffers
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        // Update animation
        updateAnimation()
        
        // Use shader program
        GLES20.glUseProgram(shaderProgram)
        
        // Update model matrix with rotation
        MathUtils.createRotationYMatrix(MathUtils.toRadians(rotationAngle), modelMatrix)
        
        // Calculate MVP matrix
        MathUtils.multiplyMatrices(viewMatrix, modelMatrix, tempMatrix)
        MathUtils.multiplyMatrices(projectionMatrix, tempMatrix, mvpMatrix)
        
        // Set uniforms
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glUniform3fv(lightDirectionHandle, 1, LIGHT_DIRECTION, 0)
        GLES20.glUniform3fv(ambientColorHandle, 1, AMBIENT_COLOR, 0)
        
        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, earthTexture)
        GLES20.glUniform1i(textureHandle, 0)
        
        // Set vertex attributes
        vertexBuffer?.let { buffer ->
            buffer.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(
                positionHandle, 3, GLES20.GL_FLOAT, false, 0, buffer
            )
        }
        
        texCoordBuffer?.let { buffer ->
            buffer.position(0)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(
                texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, buffer
            )
        }
        
        normalBuffer?.let { buffer ->
            buffer.position(0)
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glVertexAttribPointer(
                normalHandle, 3, GLES20.GL_FLOAT, false, 0, buffer
            )
        }
        
        // Draw the Earth
        earthModel?.let { model ->
            indexBuffer?.let { buffer ->
                buffer.position(0)
                Log.d(TAG, "Drawing ${model.indexCount} indices")
                GLES20.glDrawElements(
                    GLES20.GL_TRIANGLES,
                    model.indexCount,
                    GLES20.GL_UNSIGNED_SHORT,
                    buffer
                )
                TextureUtils.checkGlError("glDrawElements")
            } ?: Log.e(TAG, "indexBuffer is null!")
        } ?: Log.e(TAG, "earthModel is null in onDrawFrame!")
        
        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
        
        // Performance monitoring
        updateFPS()
        
        TextureUtils.checkGlError("onDrawFrame")
    }
    
    private fun initializeResources() {
        try {
            // Create shader program
            shaderProgram = ShaderUtils.createProgram(
                ShaderUtils.VERTEX_SHADER_SOURCE,
                ShaderUtils.FRAGMENT_SHADER_SOURCE
            )
            
            if (shaderProgram == 0) {
                Log.e(TAG, "Failed to create shader program")
                return
            }
            
            // Get shader locations
            positionHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Position")
            texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "a_TexCoord")
            normalHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Normal")
            mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "u_MVPMatrix")
            modelMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "u_ModelMatrix")
            lightDirectionHandle = GLES20.glGetUniformLocation(shaderProgram, "u_LightDirection")
            textureHandle = GLES20.glGetUniformLocation(shaderProgram, "u_Texture")
            ambientColorHandle = GLES20.glGetUniformLocation(shaderProgram, "u_AmbientColor")
            
            // Load Earth model (now using beautiful sphere!)
            Log.d(TAG, "Creating beautiful Earth sphere")
            earthModel = SphereGenerator.createSphere(1.5f, 32, 16)
            
            // TODO: Uncomment this to use OBJ loading once we verify rendering works
            /*
            val modelLoader = ModelLoader()
            earthModel = modelLoader.loadModel(context, "earth.obj")
            
            if (earthModel == null) {
                Log.w(TAG, "Failed to load Earth model, generating procedural sphere")
                earthModel = SphereGenerator.createSphere(1.5f, 32, 16)
            }
            */
            
            earthModel?.let { model ->
                Log.d(TAG, "Earth model loaded: ${model.vertexCount} vertices, ${model.indexCount} indices")
                Log.d(TAG, "First few vertices: ${model.vertices.take(9).joinToString()}")
                Log.d(TAG, "First few indices: ${model.indices.take(6).joinToString()}")
            } ?: run {
                Log.e(TAG, "ERROR: earthModel is null!")
                return
            }
            
            // Create vertex buffers
            createVertexBuffers(earthModel!!)
            
            // Load Earth texture
            earthTexture = TextureUtils.loadTexture(context, "earth_clouds.jpg")
            
            if (earthTexture == 0) {
                Log.e(TAG, "Failed to load Earth texture - creating white texture")
                earthTexture = createWhiteTexture()
            }
            
            Log.d(TAG, "Using texture ID: $earthTexture")
            
            isInitialized = true
            Log.d(TAG, "Renderer initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing renderer", e)
        }
    }
    
    private fun createVertexBuffers(model: ModelLoader.Model) {
        // Vertex buffer
        vertexBuffer = ByteBuffer.allocateDirect(model.vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(model.vertices)
                position(0)
            }
        
        // Texture coordinate buffer
        texCoordBuffer = ByteBuffer.allocateDirect(model.texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(model.texCoords)
                position(0)
            }
        
        // Normal buffer
        normalBuffer = ByteBuffer.allocateDirect(model.normals.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(model.normals)
                position(0)
            }
        
        // Index buffer
        indexBuffer = ByteBuffer.allocateDirect(model.indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(model.indices)
                position(0)
            }
    }
    
    private fun updateAnimation() {
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastFrameTime).toFloat()
        lastFrameTime = currentTime
        
        // Update rotation based on elapsed time for consistent speed
        rotationAngle += ROTATION_SPEED * (deltaTime / FRAME_TIME_MS)
        if (rotationAngle >= 360f) {
            rotationAngle -= 360f
        }
    }
    
    private fun updateFPS() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - fpsTimer >= 5000) { // Log FPS every 5 seconds
            val fps = frameCount * 1000f / (currentTime - fpsTimer)
            Log.d(TAG, "FPS: %.1f".format(fps))
            frameCount = 0
            fpsTimer = currentTime
        }
    }
    
    /**
     * Clean up OpenGL resources
     */
    fun cleanup() {
        if (shaderProgram != 0) {
            GLES20.glDeleteProgram(shaderProgram)
            shaderProgram = 0
        }
        
        if (earthTexture != 0) {
            TextureUtils.deleteTexture(earthTexture)
            earthTexture = 0
        }
        
        earthModel?.cleanup()
        earthModel = null
        
        vertexBuffer = null
        texCoordBuffer = null
        normalBuffer = null
        indexBuffer = null
        
        isInitialized = false
        Log.d(TAG, "Renderer cleaned up")
    }
    
    /**
     * Creates a simple white texture as fallback
     */
    private fun createWhiteTexture(): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)
        
        if (textureHandle[0] != 0) {
            val pixels = intArrayOf(0xFFFFFFFF.toInt()) // White pixel
            
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
                java.nio.ByteBuffer.allocateDirect(4).put(byteArrayOf(-1, -1, -1, -1)).flip()
            )
        }
        
        Log.d(TAG, "Created white fallback texture: ${textureHandle[0]}")
        return textureHandle[0]
    }
} 