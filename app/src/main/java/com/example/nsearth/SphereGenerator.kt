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
        
        // Generate indices for triangles
        for (lat in 0 until latitudeDivisions) {
            for (lon in 0 until longitudeDivisions) {
                val first = (lat * (longitudeDivisions + 1) + lon).toShort()
                val second = (first + longitudeDivisions + 1).toShort()
                
                // First triangle
                indices.add(first)
                indices.add(second)
                indices.add((first + 1).toShort())
                
                // Second triangle
                indices.add(second)
                indices.add((second + 1).toShort())
                indices.add((first + 1).toShort())
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
    
    /**
     * Creates a simple textured cube for debugging
     */
    fun createCube(size: Float): ModelLoader.Model {
        val half = size / 2f
        
        // Define all 24 vertices (4 per face for proper texturing)
        val vertices = floatArrayOf(
            // Front face
            -half, -half,  half,   half, -half,  half,   half,  half,  half,   -half,  half,  half,
            // Back face
            -half, -half, -half,  -half,  half, -half,   half,  half, -half,    half, -half, -half,
            // Top face
            -half,  half, -half,  -half,  half,  half,   half,  half,  half,    half,  half, -half,
            // Bottom face
            -half, -half, -half,   half, -half, -half,   half, -half,  half,   -half, -half,  half,
            // Right face
             half, -half, -half,   half,  half, -half,   half,  half,  half,    half, -half,  half,
            // Left face
            -half, -half, -half,  -half, -half,  half,  -half,  half,  half,   -half,  half, -half
        )
        
        // Texture coordinates for each face
        val texCoords = floatArrayOf(
            // Front, Back, Top, Bottom, Right, Left faces (each gets 0,0 -> 1,1 mapping)
            0f, 0f,  1f, 0f,  1f, 1f,  0f, 1f,  // Front
            1f, 0f,  1f, 1f,  0f, 1f,  0f, 0f,  // Back
            0f, 1f,  0f, 0f,  1f, 0f,  1f, 1f,  // Top
            1f, 1f,  0f, 1f,  0f, 0f,  1f, 0f,  // Bottom
            1f, 0f,  1f, 1f,  0f, 1f,  0f, 0f,  // Right
            0f, 0f,  1f, 0f,  1f, 1f,  0f, 1f   // Left
        )
        
        // Normals for each face
        val normals = floatArrayOf(
            // Front face
            0f, 0f, 1f,   0f, 0f, 1f,   0f, 0f, 1f,   0f, 0f, 1f,
            // Back face  
            0f, 0f, -1f,  0f, 0f, -1f,  0f, 0f, -1f,  0f, 0f, -1f,
            // Top face
            0f, 1f, 0f,   0f, 1f, 0f,   0f, 1f, 0f,   0f, 1f, 0f,
            // Bottom face
            0f, -1f, 0f,  0f, -1f, 0f,  0f, -1f, 0f,  0f, -1f, 0f,
            // Right face
            1f, 0f, 0f,   1f, 0f, 0f,   1f, 0f, 0f,   1f, 0f, 0f,
            // Left face
            -1f, 0f, 0f,  -1f, 0f, 0f,  -1f, 0f, 0f,  -1f, 0f, 0f
        )
        
        // Triangle indices for each face
        val indices = shortArrayOf(
            0, 1, 2,    0, 2, 3,    // Front
            4, 5, 6,    4, 6, 7,    // Back  
            8, 9, 10,   8, 10, 11,  // Top
            12, 13, 14, 12, 14, 15, // Bottom
            16, 17, 18, 16, 18, 19, // Right
            20, 21, 22, 20, 22, 23  // Left
        )
        
        Log.d(TAG, "Created cube: ${vertices.size / 3} vertices, ${indices.size} indices")
        
        return ModelLoader.Model(
            vertices = vertices,
            texCoords = texCoords,
            normals = normals,
            indices = indices,
            vertexCount = vertices.size / 3,
            indexCount = indices.size
        )
    }
} 