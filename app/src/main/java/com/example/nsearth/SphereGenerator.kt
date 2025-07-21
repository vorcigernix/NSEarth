package com.example.nsearth

import android.util.Log
import kotlin.math.*

/**
 * Procedural sphere generator for creating perfect spherical geometry
 */
object SphereGenerator {
    
    private const val TAG = "SphereGenerator"
    
    /**
     * Creates a UV-mapped sphere with proper texture coordinates and normals
     * @param radius The radius of the sphere
     * @param latitudeDivisions Number of divisions along latitude (horizontal rings)
     * @param longitudeDivisions Number of divisions along longitude (vertical slices)
     */
    fun createSphere(radius: Float, latitudeDivisions: Int, longitudeDivisions: Int): ModelLoader.Model {
        val vertices = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        
        // Generate vertices, texture coordinates, and normals
        for (lat in 0..latitudeDivisions) {
            val theta = lat * PI.toFloat() / latitudeDivisions  // 0 to π
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)
            
            for (lon in 0..longitudeDivisions) {
                val phi = lon * 2 * PI.toFloat() / longitudeDivisions  // 0 to 2π
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)
                
                // Spherical to Cartesian conversion
                val x = sinTheta * cosPhi
                val y = cosTheta
                val z = sinTheta * sinPhi
                
                // Position (scaled by radius)
                vertices.add(x * radius)
                vertices.add(y * radius)
                vertices.add(z * radius)
                
                // Normal (same as normalized position for a sphere)
                normals.add(x)
                normals.add(y)
                normals.add(z)
                
                // Texture coordinates (U,V mapping)
                val u = lon.toFloat() / longitudeDivisions
                val v = lat.toFloat() / latitudeDivisions
                texCoords.add(u)
                texCoords.add(v)
            }
        }
        
        // Generate indices for triangles - simple working indices
        for (lat in 0 until latitudeDivisions - 1) {
            for (lon in 0 until longitudeDivisions - 1) {
                val idx0 = (lat * longitudeDivisions + lon).toShort()
                val idx1 = (lat * longitudeDivisions + lon + 1).toShort()
                val idx2 = ((lat + 1) * longitudeDivisions + lon).toShort()
                val idx3 = ((lat + 1) * longitudeDivisions + lon + 1).toShort()
                
                indices.add(idx0)
                indices.add(idx1)
                indices.add(idx2)
                
                indices.add(idx1)
                indices.add(idx2)
                indices.add(idx3)
            }
        }
        
        val vertexCount = vertices.size / 3
        val indexCount = indices.size
        
        Log.d(TAG, "Generated sphere: ${vertexCount} vertices, ${indexCount} indices, radius: $radius")
        
        return ModelLoader.Model(
            vertices = vertices.toFloatArray(),
            texCoords = texCoords.toFloatArray(),
            normals = normals.toFloatArray(),
            indices = indices.toShortArray(),
            vertexCount = vertexCount,
            indexCount = indexCount
        )
    }
} 