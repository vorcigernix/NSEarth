package com.example.nsearth

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import android.opengl.Matrix
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance OpenGL ES renderer for 3D Earth wallpaper using procedural sphere
 */
class EarthRenderer(private val context: Context) : GLESRenderer {

    // ... (vertexShaderCode and fragmentShaderCode remain the same)
    private val vertexShaderCode = """
        #version 100
        precision highp float;
        attribute vec3 vPosition;
        attribute vec3 vNormal;
        attribute vec2 vTexCoord;
        uniform mat4 uMVPMatrix;
        uniform mat4 uModelMatrix;
        uniform mat4 uViewMatrix;
        varying vec3 vNormalView;
        varying vec2 vTexCoordOut;
        varying vec3 vModelPos; // New varying for 3D noise
        varying vec3 v_ViewPosition; // Vertex position in view space
        uniform vec3 u_BeaconPosition;
        varying vec3 v_BeaconDirection;

        void main() {
            vec4 modelPosition = uModelMatrix * vec4(vPosition, 1.0);
            v_ViewPosition = (uViewMatrix * modelPosition).xyz;
            gl_Position = uMVPMatrix * vec4(vPosition, 1.0);
            
            vNormalView = normalize((uViewMatrix * uModelMatrix * vec4(vNormal, 0.0)).xyz);
            vTexCoordOut = vTexCoord;
            vModelPos = modelPosition.xyz;
            v_BeaconDirection = u_BeaconPosition - vModelPos;
        }
    """

    private val fragmentShaderCode = """
        #version 100
        precision highp float;
        uniform sampler2D uTexture;
        uniform sampler2D uCloudTexture; // New uniform for cloud texture
        uniform float uTime;
        uniform mat4 uModelMatrix;
        uniform mat4 uViewMatrix;
        varying vec3 vNormalView;
        varying vec2 vTexCoordOut;
        varying vec3 vModelPos;
        varying vec3 v_ViewPosition; // Vertex position in view space
        uniform vec3 u_BeaconPosition;
        varying vec3 v_BeaconDirection;
        
        void main() {
            // Sample base Earth and cloud textures
            vec3 earthColor = texture2D(uTexture, vTexCoordOut).rgb;
            vec3 finalColor = earthColor;

            // Additive glow from beacon, even more subtle
            float beaconDistance = length(v_BeaconDirection);
            float beaconGlow = smoothstep(0.1, 0.0, beaconDistance); // Even smaller falloff
            finalColor += vec3(1.0, 1.0, 0.8) * beaconGlow * 0.6; // Further reduced intensity

            // Apply lighting using the view direction as the primary light source ("headlight")
            vec3 normal = normalize(vNormalView);
            vec3 viewDir = normalize(-v_ViewPosition); // Direction from surface to camera
            
            // Ambient component provides a soft, global light
            float ambient = 0.2;
            
            // Diffuse component is based on the view angle
            float diff = max(dot(normal, viewDir), 0.0);
            
            // Combine for final lighting
            vec3 litEarth = finalColor * (ambient + diff);

            // Add atmospheric halo (rim lighting)
            float rim = 1.0 - max(dot(normal, viewDir), 0.0);
            rim = smoothstep(0.6, 1.0, rim);
            vec3 rimColor = vec3(0.3, 0.5, 1.0);

            gl_FragColor = vec4(litEarth + rimColor * rim * 0.2, 1.0);
        }
    """

    private var mProgram = 0
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private var angle = 220f // Start with the beacon on the left side of the screen
    private var frameCount = 0
    private var time = 0f

    private companion object {
        private const val EARTH_RADIUS = 1.5f
        private const val MAIN_BEACON_LAT = 1.3521f
        private const val MAIN_BEACON_LON = 103.8198f
    }

    private lateinit var modelVertexBuffer: FloatBuffer
    private lateinit var modelNormalBuffer: FloatBuffer
    private lateinit var modelTexCoordBuffer: FloatBuffer
    private lateinit var modelIndexBuffer: ShortBuffer
    private var modelIndexCount = 0
    private var textureId: Int = 0
    private var cloudTextureId: Int = 0

    private lateinit var beaconRenderer: BeaconRenderer
    private val earthRadius = 1.5f
    
    // Visibility state for optimizations
    private var isVisible = true

    // Pre-allocated arrays to avoid allocation in onDrawFrame
    private val beaconModelMatrix = FloatArray(16)
    private val orientationMatrix = FloatArray(16)
    private val finalBeaconMatrix = FloatArray(16)
    private val beaconMvpMatrix = FloatArray(16)
    private val lightDirection = floatArrayOf(0.7f, 0.0f, -0.7f)
    private val rotatedBeaconPositionVec4 = FloatArray(4)
    private val rotatedBeaconPosition = FloatArray(3)
    private val beaconPosition = FloatArray(3)
    private val up = FloatArray(3)
    private val right = FloatArray(3)
    private val forward = FloatArray(3)
    private val arbitraryVector = floatArrayOf(0f, 1f, 0f)

    override fun onSurfaceCreated(config: EGLConfig?) {
        // ... (onSurfaceCreated setup remains the same)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LESS)
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
        textureId = TextureUtils.loadTexture(context, "earth_cloudless.jpg")
        cloudTextureId = TextureUtils.loadTexture(context, "earth_clouds.jpg")
        Log.d("NSEarthDebug", "Loaded cloudless texture ID: $textureId")
        Log.d("NSEarthDebug", "Loaded cloud texture ID: $cloudTextureId")
        
        // Setup model index buffer
        val ib = ByteBuffer.allocateDirect(model.indices.size * 2)
        ib.order(ByteOrder.nativeOrder())
        modelIndexBuffer = ib.asShortBuffer()
        modelIndexBuffer.put(model.indices)
        modelIndexBuffer.position(0)
        modelIndexCount = model.indices.size
        
        // Setup shaders
        val vertexShader = ShaderUtils.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = ShaderUtils.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            
            // Check for linking errors
            val linked = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linked, 0)
            if (linked[0] == 0) {
                val error = GLES20.glGetProgramInfoLog(it)
                Log.e("NSEarthDebug", "Program linking error: $error")
                GLES20.glDeleteProgram(it)
                throw RuntimeException("Program linking failed: $error")
            }
        }
        Matrix.setIdentityM(mvpMatrix, 0)
        
        Log.d("NSEarthDebug", "=== Setup complete: Procedural sphere ready ===")
        beaconRenderer = BeaconRenderer(context)
        beaconRenderer.setup()
    }

    override fun onDrawFrame() {
        // Early exit if not visible - this shouldn't happen often due to GL thread optimizations,
        // but it's a safety net for any edge cases
        if (!isVisible) return
        
        frameCount++
        // Increment angle and time at the beginning
        angle += 0.1f
        if (angle > 360f) angle -= 360f
        time += 0.02f
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(mProgram)
        
        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        val textureHandle = GLES20.glGetUniformLocation(mProgram, "uTexture")
        GLES20.glUniform1i(textureHandle, 0)

        // Bind the cloud texture to texture unit 1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cloudTextureId)
        val cloudTextureHandle = GLES20.glGetUniformLocation(mProgram, "uCloudTexture")
        GLES20.glUniform1i(cloudTextureHandle, 1)
        
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

        // Set uniforms
        val mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        val modelMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uModelMatrix")
        val viewMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uViewMatrix")
        val timeHandle = GLES20.glGetUniformLocation(mProgram, "uTime")
        val beaconPositionHandle = GLES20.glGetUniformLocation(mProgram, "u_BeaconPosition")
        
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, -90f, 0f, 1f, 0f) // Initial rotation to align texture
        Matrix.rotateM(modelMatrix, 0, angle, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(viewMatrixHandle, 1, false, viewMatrix, 0)
        GLES20.glUniform1f(timeHandle, time)

        val cartesianPosition = MathUtils.gpsToCartesian(MAIN_BEACON_LAT, MAIN_BEACON_LON, EARTH_RADIUS, beaconPosition)
        
        // Apply Earth's rotation to beacon position for surface glow
        Matrix.multiplyMV(rotatedBeaconPositionVec4, 0, modelMatrix, 0, floatArrayOf(cartesianPosition[0], cartesianPosition[1], cartesianPosition[2], 1f), 0)
        rotatedBeaconPosition[0] = rotatedBeaconPositionVec4[0]
        rotatedBeaconPosition[1] = rotatedBeaconPositionVec4[1]
        rotatedBeaconPosition[2] = rotatedBeaconPositionVec4[2]
        
        // Pass the already-rotated beacon position to avoid double transformation in shader
        GLES20.glUniform3fv(beaconPositionHandle, 1, rotatedBeaconPosition, 0)

        modelIndexBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, modelIndexCount, GLES20.GL_UNSIGNED_SHORT, modelIndexBuffer)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        drawBeacons()
    }

    // ... (onSurfaceChanged and loadShader remain the same)
    override fun onSurfaceChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        // Set up a perspective projection matrix
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 1f, 15f)
        // Set up a camera/view matrix with a slight tilt
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 10f, 0f, 0f, 0f, 0f, 0.9848f, 0.1736f) // 10 degree tilt
        Log.d("NSEarthDebug", "=== Surface changed: ${width}x${height}, ratio=$ratio ===")
        Log.d("NSEarthDebug", "Projection matrix: ${projectionMatrix.take(4)}")
        Log.d("NSEarthDebug", "View matrix: ${viewMatrix.take(4)}")
    }

    private fun drawBeacons() {
        // Draw the main beacon with a gentle pulse
        val mainBeaconPulse = 1.0f + 0.1f * sin(time * 3.0f)
        drawBeacon(MAIN_BEACON_LAT, MAIN_BEACON_LON, true, 1.0f, mainBeaconPulse)

        // Draw the dynamic city beacons
        CityData.cities.forEachIndexed { index, city ->
            val cityTime = time + index * 1.5f // Increase offset for more randomness
            val sineWave = 0.5f + 0.5f * sin(cityTime * 0.5f)
            val alpha = MathUtils.smoothstep(0.0f, 0.2f, sineWave) * (1.0f - MathUtils.smoothstep(0.8f, 1.0f, sineWave))
            drawBeacon(city.latitude, city.longitude, false, alpha, 1.0f) // No pulse for city beacons
        }
    }

    private fun drawBeacon(latitude: Float, longitude: Float, isMainBeacon: Boolean, alpha: Float, pulse: Float) {
        // 1. Get beacon's base position in model space
        MathUtils.gpsToCartesian(latitude, longitude, earthRadius, beaconPosition)

        // 2. Create the beacon's transformation matrix
        Matrix.setIdentityM(beaconModelMatrix, 0)

        // 3. Orient the beacon to point "up" from the sphere's surface
        up[0] = beaconPosition[0]
        up[1] = beaconPosition[1]
        up[2] = beaconPosition[2]
        MathUtils.normalize(up)
        
        if (up[1] > 0.99f || up[1] < -0.99f) {
            arbitraryVector[0] = 1f; arbitraryVector[1] = 0f; arbitraryVector[2] = 0f
        } else {
            arbitraryVector[0] = 0f; arbitraryVector[1] = 1f; arbitraryVector[2] = 0f
        }
        MathUtils.crossProduct(up, arbitraryVector, right)
        MathUtils.normalize(right)
        MathUtils.crossProduct(right, up, forward)
        MathUtils.normalize(forward)
        
        Matrix.setIdentityM(orientationMatrix, 0)
        orientationMatrix[0] = right[0]; orientationMatrix[4] = up[0];    orientationMatrix[8] = forward[0];
        orientationMatrix[1] = right[1]; orientationMatrix[5] = up[1];    orientationMatrix[9] = forward[1];
        orientationMatrix[2] = right[2]; orientationMatrix[6] = up[2];    orientationMatrix[10] = forward[2];

        // 4. Translate the beacon to its position on the sphere
        Matrix.translateM(beaconModelMatrix, 0, beaconPosition[0], beaconPosition[1], beaconPosition[2])
        Matrix.multiplyMM(beaconModelMatrix, 0, beaconModelMatrix, 0, orientationMatrix, 0)

        // 5. Apply the same rotation as the Earth
        Matrix.multiplyMM(finalBeaconMatrix, 0, modelMatrix, 0, beaconModelMatrix, 0)

        // 6. Create MVP matrix and draw
        Matrix.multiplyMM(beaconMvpMatrix, 0, viewMatrix, 0, finalBeaconMatrix, 0)
        Matrix.multiplyMM(beaconMvpMatrix, 0, projectionMatrix, 0, beaconMvpMatrix, 0)
        
        // Set up additive blending for a glowing effect
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        
        // With all underlying issues fixed, the standard depth test will now work correctly.
        beaconRenderer.draw(beaconMvpMatrix, finalBeaconMatrix, lightDirection, time, isMainBeacon, alpha, pulse)
        
        // Restore the original blending mode
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onVisibilityChanged(visible: Boolean) {
        isVisible = visible
        
        if (visible) {
            // Reload textures only if they were released
            if (textureId == 0) {
                textureId = TextureUtils.loadTexture(context, "earth_cloudless.jpg")
                Log.d("NSEarthDebug", "Reloaded cloudless texture ID: $textureId")
            }
            if (cloudTextureId == 0) {
                cloudTextureId = TextureUtils.loadTexture(context, "earth_clouds.jpg")
                Log.d("NSEarthDebug", "Reloaded cloud texture ID: $cloudTextureId")
            }
        } else {
            // Release textures to free GPU memory when not visible
            if (textureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                textureId = 0
            }
            if (cloudTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(cloudTextureId), 0)
                cloudTextureId = 0
            }
            Log.d("NSEarthDebug", "Released textures on visibility change")
        }
        
        // Propagate visibility to beacon renderer if it exists
        if (::beaconRenderer.isInitialized) {
            beaconRenderer.onVisibilityChanged(visible)
        }
    }

    override fun release() {
        // Clean up shader program
        if (mProgram != 0) {
            GLES20.glDeleteProgram(mProgram)
            mProgram = 0
        }
        
        // Clean up textures
        if (textureId != 0 || cloudTextureId != 0) {
            GLES20.glDeleteTextures(2, intArrayOf(textureId, cloudTextureId), 0)
            textureId = 0
            cloudTextureId = 0
        }
        
        // Clean up beacon renderer
        if (::beaconRenderer.isInitialized) {
            beaconRenderer.release()
        }
        
        Log.d("NSEarthDebug", "EarthRenderer resources released")
    }
}