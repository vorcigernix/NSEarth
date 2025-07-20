package com.example.nsearth

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.collections.ArrayList

/**
 * Simple OBJ model loader optimized for the Earth sphere model
 */
class ModelLoader {
    
    companion object {
        private const val TAG = "ModelLoader"
    }
    
    data class Model(
        val vertices: FloatArray,
        val texCoords: FloatArray,
        val normals: FloatArray,
        val indices: ShortArray,
        val vertexCount: Int,
        val indexCount: Int
    ) {
        fun cleanup() {
            // Model data will be garbage collected
        }
    }
    
    /**
     * Loads an OBJ model from assets
     */
    fun loadModel(context: Context, filename: String): Model? {
        try {
            val inputStream = context.assets.open(filename)
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            val vertices = ArrayList<Float>()
            val texCoords = ArrayList<Float>()
            val normals = ArrayList<Float>()
            val faces = ArrayList<Face>()
            
            val tempVertices = ArrayList<Triple<Float, Float, Float>>()
            val tempTexCoords = ArrayList<Pair<Float, Float>>()
            val tempNormals = ArrayList<Triple<Float, Float, Float>>()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { l ->
                    val tokens = l.trim().split("\\s+".toRegex())
                    if (tokens.isEmpty()) return@let
                    
                    when (tokens[0]) {
                        "v" -> {
                            // Vertex position
                            if (tokens.size >= 4) {
                                tempVertices.add(Triple(
                                    tokens[1].toFloat(),
                                    tokens[2].toFloat(),
                                    tokens[3].toFloat()
                                ))
                            }
                        }
                        "vt" -> {
                            // Texture coordinate
                            if (tokens.size >= 3) {
                                tempTexCoords.add(Pair(
                                    tokens[1].toFloat(),
                                    tokens[2].toFloat()
                                ))
                            }
                        }
                        "vn" -> {
                            // Normal
                            if (tokens.size >= 4) {
                                tempNormals.add(Triple(
                                    tokens[1].toFloat(),
                                    tokens[2].toFloat(),
                                    tokens[3].toFloat()
                                ))
                            }
                        }
                        "f" -> {
                            // Face (triangle or quad)
                            if (tokens.size >= 4) {
                                parseFaces(tokens, faces)
                            }
                        }
                    }
                }
            }
            
            reader.close()
            inputStream.close()
            
            Log.d(TAG, "Parsed ${tempVertices.size} vertices, ${tempTexCoords.size} texture coords, ${tempNormals.size} normals, ${faces.size} faces")
            
            // Convert to indexed arrays
            return convertToIndexedModel(tempVertices, tempTexCoords, tempNormals, faces)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model: $filename", e)
            return null
        }
    }
    
    private data class Face(
        val v1: FaceVertex,
        val v2: FaceVertex,
        val v3: FaceVertex
    )
    
    private data class FaceVertex(
        val vertexIndex: Int,
        val texCoordIndex: Int,
        val normalIndex: Int
    )
    
    private fun parseFaces(tokens: List<String>, faces: MutableList<Face>) {
        try {
            // Parse vertices of the face
            val faceVertices = mutableListOf<FaceVertex>()
            for (i in 1 until tokens.size) {
                val faceVertex = parseFaceVertex(tokens[i])
                if (faceVertex != null) {
                    faceVertices.add(faceVertex)
                }
            }
            
            // Triangulate the face (convert quads to triangles)
            if (faceVertices.size >= 3) {
                // For triangles: use as-is
                if (faceVertices.size == 3) {
                    faces.add(Face(faceVertices[0], faceVertices[1], faceVertices[2]))
                }
                // For quads: split into two triangles
                else if (faceVertices.size == 4) {
                    faces.add(Face(faceVertices[0], faceVertices[1], faceVertices[2]))
                    faces.add(Face(faceVertices[0], faceVertices[2], faceVertices[3]))
                }
                // For polygons with more vertices: fan triangulation
                else {
                    for (i in 1 until faceVertices.size - 1) {
                        faces.add(Face(faceVertices[0], faceVertices[i], faceVertices[i + 1]))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing face: ${tokens.joinToString(" ")}", e)
        }
    }
    
    private fun parseFaceVertex(token: String): FaceVertex? {
        val parts = token.split("/")
        return try {
            val vertexIndex = parts[0].toInt() - 1 // OBJ indices are 1-based
            val texCoordIndex = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toInt() - 1 else 0
            val normalIndex = if (parts.size > 2 && parts[2].isNotEmpty()) parts[2].toInt() - 1 else 0
            
            FaceVertex(vertexIndex, texCoordIndex, normalIndex)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun convertToIndexedModel(
        tempVertices: List<Triple<Float, Float, Float>>,
        tempTexCoords: List<Pair<Float, Float>>,
        tempNormals: List<Triple<Float, Float, Float>>,
        faces: List<Face>
    ): Model {
        val vertices = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        
        val vertexMap = mutableMapOf<String, Short>()
        var currentIndex: Short = 0
        
        for (face in faces) {
            for (faceVertex in listOf(face.v1, face.v2, face.v3)) {
                val key = "${faceVertex.vertexIndex}/${faceVertex.texCoordIndex}/${faceVertex.normalIndex}"
                
                val index = vertexMap[key]
                if (index != null) {
                    indices.add(index)
                } else {
                    // Add new vertex
                    val vertex = tempVertices.getOrNull(faceVertex.vertexIndex)
                    val texCoord = tempTexCoords.getOrNull(faceVertex.texCoordIndex)
                    val normal = tempNormals.getOrNull(faceVertex.normalIndex)
                    
                    if (vertex != null) {
                        vertices.add(vertex.first)
                        vertices.add(vertex.second)
                        vertices.add(vertex.third)
                    }
                    
                    if (texCoord != null) {
                        texCoords.add(texCoord.first)
                        texCoords.add(1.0f - texCoord.second) // Flip V coordinate
                    } else {
                        texCoords.add(0f)
                        texCoords.add(0f)
                    }
                    
                    if (normal != null) {
                        normals.add(normal.first)
                        normals.add(normal.second)
                        normals.add(normal.third)
                    } else {
                        normals.add(0f)
                        normals.add(1f)
                        normals.add(0f)
                    }
                    
                    vertexMap[key] = currentIndex
                    indices.add(currentIndex)
                    currentIndex++
                }
            }
        }
        
        Log.d(TAG, "OBJ parsing complete:")
        Log.d(TAG, "- Raw vertices: ${tempVertices.size}, texCoords: ${tempTexCoords.size}, normals: ${tempNormals.size}")
        Log.d(TAG, "- Faces: ${faces.size}")
        Log.d(TAG, "- Final vertices: ${vertices.size / 3}, indices: ${indices.size}")
        
        if (vertices.isEmpty() || indices.isEmpty()) {
            Log.e(TAG, "ERROR: Model has no geometry! Creating fallback cube")
            // Return a simple fallback cube if parsing fails
            return createFallbackCube()
        }
        
        return Model(
            vertices = vertices.toFloatArray(),
            texCoords = texCoords.toFloatArray(),
            normals = normals.toFloatArray(),
            indices = indices.toShortArray(),
            vertexCount = vertices.size / 3,
            indexCount = indices.size
        )
    }
    
    private fun createFallbackCube(): Model {
        val size = 1f
        val vertices = floatArrayOf(
            -size, -size,  size,   size, -size,  size,   size,  size,  size,  -size,  size,  size, // front
            -size, -size, -size,  -size,  size, -size,   size,  size, -size,   size, -size, -size  // back
        )
        val texCoords = floatArrayOf(
            0f, 0f,  1f, 0f,  1f, 1f,  0f, 1f, // front
            1f, 0f,  1f, 1f,  0f, 1f,  0f, 0f  // back
        )
        val normals = floatArrayOf(
            0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f, // front
            0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f // back
        )
        val indices = shortArrayOf(0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4)
        
        return Model(vertices, texCoords, normals, indices, 8, 12)
    }
} 