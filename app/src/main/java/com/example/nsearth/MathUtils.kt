package com.example.nsearth

import kotlin.math.*

/**
 * Efficient 3D math utilities optimized for OpenGL ES 2.0
 */
object MathUtils {
    
    /**
     * Creates a 4x4 perspective projection matrix
     */
    fun createPerspectiveMatrix(
        fovY: Float,
        aspectRatio: Float,
        nearPlane: Float,
        farPlane: Float,
        result: FloatArray
    ) {
        val f = 1f / tan(fovY * 0.5f)
        val rangeInv = 1f / (nearPlane - farPlane)
        
        result.fill(0f)
        result[0] = f / aspectRatio
        result[5] = f
        result[10] = (nearPlane + farPlane) * rangeInv
        result[11] = -1f
        result[14] = nearPlane * farPlane * rangeInv * 2f
    }
    
    /**
     * Creates a 4x4 view matrix for a camera looking at a target
     */
    fun createLookAtMatrix(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        centerX: Float, centerY: Float, centerZ: Float,
        upX: Float, upY: Float, upZ: Float,
        result: FloatArray
    ) {
        // Forward vector
        var fx = centerX - eyeX
        var fy = centerY - eyeY
        var fz = centerZ - eyeZ
        
        // Normalize forward
        val fLength = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fLength
        fy /= fLength
        fz /= fLength
        
        // Right vector (forward × up)
        var rx = fy * upZ - fz * upY
        var ry = fz * upX - fx * upZ
        var rz = fx * upY - fy * upX
        
        // Normalize right
        val rLength = sqrt(rx * rx + ry * ry + rz * rz)
        rx /= rLength
        ry /= rLength
        rz /= rLength
        
        // Up vector (right × forward)
        val ux = ry * fz - rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy - ry * fx
        
        result[0] = rx; result[1] = ux; result[2] = -fx; result[3] = 0f
        result[4] = ry; result[5] = uy; result[6] = -fy; result[7] = 0f
        result[8] = rz; result[9] = uz; result[10] = -fz; result[11] = 0f
        result[12] = -(rx * eyeX + ry * eyeY + rz * eyeZ)
        result[13] = -(ux * eyeX + uy * eyeY + uz * eyeZ)
        result[14] = fx * eyeX + fy * eyeY + fz * eyeZ
        result[15] = 1f
    }
    
    /**
     * Creates a rotation matrix around Y-axis (for spinning the globe)
     */
    fun createRotationYMatrix(angleRadians: Float, result: FloatArray) {
        val cos = cos(angleRadians)
        val sin = sin(angleRadians)
        
        result.fill(0f)
        result[0] = cos
        result[2] = sin
        result[5] = 1f
        result[8] = -sin
        result[10] = cos
        result[15] = 1f
    }
    
    /**
     * Multiplies two 4x4 matrices: result = a * b
     */
    fun multiplyMatrices(a: FloatArray, b: FloatArray, result: FloatArray) {
        for (i in 0..3) {
            for (j in 0..3) {
                result[i * 4 + j] = 
                    a[i * 4 + 0] * b[0 * 4 + j] +
                    a[i * 4 + 1] * b[1 * 4 + j] +
                    a[i * 4 + 2] * b[2 * 4 + j] +
                    a[i * 4 + 3] * b[3 * 4 + j]
            }
        }
    }
    
    /**
     * Converts degrees to radians
     */
    fun toRadians(degrees: Float) = degrees * PI.toFloat() / 180f
    
    /**
     * Creates an identity matrix
     */
    fun createIdentityMatrix(result: FloatArray) {
        result.fill(0f)
        result[0] = 1f
        result[5] = 1f
        result[10] = 1f
        result[15] = 1f
    }
} 