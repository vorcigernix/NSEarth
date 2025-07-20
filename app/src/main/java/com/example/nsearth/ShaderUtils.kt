package com.example.nsearth

import android.opengl.GLES20
import android.util.Log

/**
 * Shader compilation and management utilities
 */
object ShaderUtils {
    
    private const val TAG = "ShaderUtils"
    
    // Vertex shader for 3D Earth rendering
    const val VERTEX_SHADER_SOURCE = """
        precision mediump float;
        
        attribute vec3 a_Position;
        attribute vec2 a_TexCoord;
        attribute vec3 a_Normal;
        
        uniform mat4 u_MVPMatrix;
        uniform mat4 u_ModelMatrix;
        uniform vec3 u_LightDirection;
        
        varying vec2 v_TexCoord;
        varying vec3 v_Normal;
        varying float v_LightIntensity;
        
        void main() {
            gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
            v_TexCoord = a_TexCoord;
            
            // Transform normal to world space
            vec3 worldNormal = normalize(mat3(u_ModelMatrix) * a_Normal);
            v_Normal = worldNormal;
            
            // Calculate lighting intensity
            v_LightIntensity = max(dot(worldNormal, normalize(-u_LightDirection)), 0.2);
        }
    """
    
    // Fragment shader for textured Earth with lighting
    const val FRAGMENT_SHADER_SOURCE = """
        precision mediump float;
        
        uniform sampler2D u_Texture;
        uniform vec3 u_AmbientColor;
        
        varying vec2 v_TexCoord;
        varying vec3 v_Normal;
        varying float v_LightIntensity;
        
        void main() {
            // Sample the earth texture
            vec4 texColor = texture2D(u_Texture, v_TexCoord);
            
            // Apply lighting
            vec3 ambient = u_AmbientColor * texColor.rgb;
            vec3 diffuse = texColor.rgb * v_LightIntensity;
            
            // Combine ambient and diffuse lighting
            vec3 finalColor = ambient + diffuse * 0.8;
            
            gl_FragColor = vec4(finalColor, texColor.a);
        }
    """
    
    /**
     * Compiles a shader from source code
     */
    fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader")
            return 0
        }
        
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        
        if (compileStatus[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compilation failed: $error")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
    
    /**
     * Creates a shader program from vertex and fragment shaders
     */
    fun createProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        if (vertexShader == 0) return 0
        
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }
        
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Failed to create program")
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }
        
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        
        if (linkStatus[0] == 0) {
            val error = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Program linking failed: $error")
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }
        
        // Clean up individual shaders as they're now part of the program
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        
        Log.d(TAG, "Successfully created shader program")
        return program
    }
    
    /**
     * Validates a shader program
     */
    fun validateProgram(program: Int): Boolean {
        GLES20.glValidateProgram(program)
        val validateStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_VALIDATE_STATUS, validateStatus, 0)
        
        if (validateStatus[0] == 0) {
            val error = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Program validation failed: $error")
            return false
        }
        
        return true
    }
} 