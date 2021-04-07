package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

import kotlin.math.*

@RunWith(GdxTestRunner::class)
class HexMath3DTests {

    private val tolerance = 1e-6f

    /**
     * Check that the distance between adjacent icosahedron vertices is correct.
     */
    @Test
    fun icosahedronVertices() {
        val edgeLength = 2f

        // Top vertex connected to all upper vertices
        for (vertex in upperVertices) {
            Assert.assertEquals(edgeLength, topVertex.dst(vertex), tolerance)
        }
        // Bottom vertex connected to all lower vertices
        for (vertex in lowerVertices) {
            Assert.assertEquals(edgeLength, bottomVertex.dst(vertex), tolerance)
        }
        for (i in 0 until 5) {
            val upperRight = upperVertices[i]
            val upperLeft = upperVertices[(i + 1) % 5]
            val lowerRight = lowerVertices[i]
            val lowerLeft = lowerVertices[(i + 1) % 5]

            // Sequential upper vertices are connected
            Assert.assertEquals(edgeLength, upperLeft.dst(upperRight), tolerance)

            // Sequential lower vertices are connected
            Assert.assertEquals(edgeLength, lowerLeft.dst(lowerRight), tolerance)

            // Each upper vertex is connected to two adjacent lower vertices
            Assert.assertEquals(edgeLength, upperRight.dst(lowerRight), tolerance)
            Assert.assertEquals(edgeLength, upperRight.dst(lowerLeft), tolerance)
        }
    }

    /**
     * Check that the correct numbers of tiles are generated for a range of edge lengths
     * based on the geometry of the icosahedron the are mapped onto
     */
    @Test
    fun numTiles() {
        for (edgeLength in 1..10) {

            // Calculate the number of hexagonal tiles that fit in one triangular face
            val numTilesPerFace = 1.5f * edgeLength * edgeLength

            // Multiply by the number of faces and add 2 to account for the 12 pentagonal
            // tiles at the vertices that take up an area equivalent to 10 hexagonal tiles
            val expectedNumTiles = (numTilesPerFace * 20).toInt() + 2

            Assert.assertEquals(expectedNumTiles, HexMath3D.getAllVectors(edgeLength).size)
        }
    }

    /**
     * Check that each ordinary hexagonal tile has 6 neighbours and each pentagonal tile
     * has 5 neighbours
     */
    @Test
    fun numNeighbours() {
        val edgeLength = 3

        val vertexCoords: List<Pair<Int, Int>> = listOf(
                Pair(0, 0),
                Pair(3, 6), Pair(9, 6), Pair(15, 6), Pair(21, 6), Pair(27, 6),
                Pair(3, 9), Pair(9, 9), Pair(15, 9), Pair(21, 9), Pair(27, 9),
                Pair(0, 15),
        )

        val testCoords = HexMath3D.getAllVectors(3)

        for (coords in testCoords) {
            val isVertexCoord = vertexCoords.any {
                (x, y) -> (x == coords.x.roundToInt()) && (y == coords.y.roundToInt())
            }
            val expectedNumNeighbours = if (isVertexCoord) 5 else 6

            val neighbours = HexMath3D.neighbouringHexCoords(edgeLength, coords)
            Assert.assertEquals(
                    "Incorrect number of neighbours for point ${coords}",
                    expectedNumNeighbours,
                    neighbours.size
            )
        }
    }

    /**
     * Check the distance between the positions specified by two sets of coordinates
     */
    fun checkDistance(edgeLength: Int, expDistance: Float, relTol: Float, c1: Vector2, c2: Vector2) {
        val p1 = HexMath3D.hex2WorldCoords(edgeLength, c1)
        val p2 = HexMath3D.hex2WorldCoords(edgeLength, c2)
        val distance = p1.cpy().sub(p2).len()
        val msg = "Distance $distance is not close to $expDistance for points $c1 and $c2"
        Assert.assertTrue(msg, distance > expDistance * (1f - relTol))
        Assert.assertTrue(msg, distance < expDistance * (1f + relTol))
    }

    /**
     * Check that the distances between adjacent tiles are approximately correct
     */
    @Test
    fun tileSeparation() {
        val edgeLength = 3

        // The exact distance on the icosahedron surface, before projecting onto the sphere
        val expectedDistance = 2f / edgeLength / sqrt(3f)

        // Allow up to 30% variation in distance after projecting onto the sphere.
        val margin = 0.3f

        val testCoords = HexMath3D.getAllVectors(3)

        for (coords in testCoords) {
            val neighbourCoordsList = HexMath3D.neighbouringHexCoords(edgeLength, coords)
            for ((i, neighbourCoords) in neighbourCoordsList.withIndex()) {
                val nextNeighbourCoords = neighbourCoordsList[(i + 1) % neighbourCoordsList.size]

                checkDistance(edgeLength, expectedDistance, margin, coords, neighbourCoords)
                checkDistance(edgeLength, expectedDistance, margin, neighbourCoords, nextNeighbourCoords)
            }
        }
    }
}