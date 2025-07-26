package com.example.nsearth

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log

/**
 * Texture loading and management utilities optimized for performance
 */
object TextureUtils {
    
    private const val TAG = "TextureUtils"
    
    /**
     * Creates a simple specular texture programmatically
     * This is used when earth_specular.jpg is not available
     */
    fun createSpecularTexture(): Int {
        val textureHandle = IntArray(1)
        
        try {
            // Generate texture
            GLES20.glGenTextures(1, textureHandle, 0)
            if (textureHandle[0] == 0) {
                Log.e(TAG, "Failed to generate specular texture")
                return 0
            }
            
            // Create a simple 64x32 specular map - much more subtle
            val width = 64
            val height = 32
            val pixels = IntArray(width * height)
            
            // Create a very subtle specular map - mostly dark with slight ocean reflectivity
            for (y in 0 until height) {
                for (x in 0 until width) {
                    // Very conservative specular values to avoid metallic look
                    val intensity = 0.1f // Much lower base intensity
                    
                    val gray = (intensity * 255).toInt().coerceIn(0, 255)
                    pixels[y * width + x] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                }
            }
            
            // Bind texture and upload data
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            
            val buffer = java.nio.ByteBuffer.allocateDirect(pixels.size * 4)
            buffer.order(java.nio.ByteOrder.nativeOrder())
            val intBuffer = buffer.asIntBuffer()
            intBuffer.put(pixels)
            buffer.position(0)
            
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
            )
            
            // Set texture parameters
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            
            Log.d(TAG, "Successfully created subtle specular texture (ID: ${textureHandle[0]})")
            return textureHandle[0]
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating specular texture", e)
            if (textureHandle[0] != 0) {
                GLES20.glDeleteTextures(1, textureHandle, 0)
            }
            return 0
        }
    }

    /**
     * Loads a texture from assets folder
     */
    fun loadTexture(context: Context, filename: String): Int {
        val textureHandle = IntArray(1)
        
        try {
            // Load bitmap from assets
            val inputStream = context.assets.open(filename)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap: $filename")
                return 0
            }
            
            // Generate texture
            GLES20.glGenTextures(1, textureHandle, 0)
            if (textureHandle[0] == 0) {
                Log.e(TAG, "Failed to generate texture")
                bitmap.recycle()
                return 0
            }
            
            // Bind texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            
            // Set texture parameters for good quality and performance
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            
            // Load the bitmap data into the texture
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            
            // Generate mipmaps for better quality at distance
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            
            // Clean up
            bitmap.recycle()
            
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "OpenGL error while loading texture: $error")
                GLES20.glDeleteTextures(1, textureHandle, 0)
                return 0
            }
            
            Log.d(TAG, "Successfully loaded texture: $filename (ID: ${textureHandle[0]})")
            return textureHandle[0]
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading texture: $filename", e)
            if (textureHandle[0] != 0) {
                GLES20.glDeleteTextures(1, textureHandle, 0)
            }
            return 0
        }
    }
    
    /**
     * Deletes a texture from GPU memory
     */
    fun deleteTexture(textureId: Int) {
        if (textureId != 0) {
            val textureIds = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textureIds, 0)
        }
    }
    
    /**
     * Checks if OpenGL operations completed successfully
     */
    fun checkGlError(operation: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$operation: glError $error")
            throw RuntimeException("$operation: glError $error")
        }
    }
} 