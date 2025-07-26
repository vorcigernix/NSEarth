package com.example.nsearth

import kotlin.math.cos
import kotlin.math.sin

object BeaconGenerator {
    fun createBeacon(
        baseRadius: Float,
        height: Float,
        segments: Int
    ): ModelLoader.Model {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        // Apex of the cone
        vertices.addAll(listOf(0f, height, 0f))
        normals.addAll(listOf(0f, 1f, 0f)) // Normal pointing up

        // Base vertices
        for (i in 0..segments) {
            val angle = i.toFloat() / segments * 2f * Math.PI.toFloat()
            val x = baseRadius * cos(angle)
            val z = baseRadius * sin(angle)

            vertices.addAll(listOf(x, 0f, z))
            // Normals for the side of the cone
            val normal = floatArrayOf(x, baseRadius, z)
            val len =
                kotlin.math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2])
            normals.addAll(listOf(normal[0] / len, normal[1] / len, normal[2] / len))
        }

        // Indices for the cone sides, wound for outward-facing normals
        for (i in 1..segments) {
            indices.addAll(listOf(0, i + 1, i))
        }

        return ModelLoader.Model(
            vertices = vertices.toFloatArray(),
            texCoords = floatArrayOf(),
            normals = normals.toFloatArray(),
            indices = indices.map { it.toShort() }.toShortArray(),
            vertexCount = vertices.size / 3,
            indexCount = indices.size
        )
    }
} 