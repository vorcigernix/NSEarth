package com.example.nsearth

import android.util.Log
import kotlin.math.*

/**
 * Procedural sphere generator for creating perfect spherical geometry
 */
object SphereGenerator {
    
    private const val TAG = "SphereGenerator"
    
    /**
     * Creates a simple UV-mapped sphere with proper texture coordinates
     * @param radius The radius of the sphere
     * @param segments Number of segments (longitude divisions)
     * @param rings Number of rings (latitude divisions)
     */
    fun createSphere(radius: Float, segments: Int, rings: Int): ModelLoader.Model {
        val vertices = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Int>()
        
        // Generate vertices using standard UV-sphere approach
        for (ring in 0..rings) {
            val theta = ring * PI.toFloat() / rings  // 0 to π (top to bottom)
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)
            val v = ring.toFloat() / rings  // V coordinate (0 to 1, top to bottom)
            
            for (segment in 0..segments) {
                val phi = segment * 2 * PI.toFloat() / segments  // 0 to 2π (around)
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)
                // Ensure UV wrapping is seamless
                val u = (segment.toFloat() / segments).coerceIn(0f, 1f)
                
                // Spherical to Cartesian conversion
                val x = sinTheta * cosPhi
                val y = cosTheta
                val z = sinTheta * sinPhi
                
                // Position (scaled by radius)
                vertices.addAll(listOf(x * radius, y * radius, z * radius))
                
                // Normal (same as normalized position for a sphere)
                normals.addAll(listOf(x, y, z))
                
                // Texture coordinates - proper UV mapping with seamless wrapping
                texCoords.addAll(listOf(u, v))
            }
        }
        
        // Generate indices for triangles
        for (ring in 0 until rings) {
            for (segment in 0 until segments) {
                val stride = segments + 1
                val idx0 = ring * stride + segment
                val idx1 = ring * stride + segment + 1
                val idx2 = (ring + 1) * stride + segment
                val idx3 = (ring + 1) * stride + segment + 1
                
                // Two triangles per quad, wound for outward-facing normals
                indices.addAll(listOf(idx0, idx1, idx2))
                indices.addAll(listOf(idx1, idx3, idx2))
            }
        }
        
        val vertexCount = vertices.size / 3
        val indexCount = indices.size
        
        Log.d(TAG, "Generated UV-sphere: ${vertexCount} vertices, ${indexCount} indices, radius: $radius")
        
        return ModelLoader.Model(
            vertices = vertices.toFloatArray(),
            texCoords = texCoords.toFloatArray(),
            normals = normals.toFloatArray(),
            indices = indices.map { it.toShort() }.toShortArray(),
            vertexCount = vertexCount,
            indexCount = indexCount
        )
    }
} 