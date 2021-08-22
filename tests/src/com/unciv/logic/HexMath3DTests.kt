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
     * Calculate the expected number of tiles for the given icosahedron edge length.
     */
    private fun nTilesForEdgeLength(edgeLength: Int): Int {
        // Calculate the number of hexagonal tiles that fit in one triangular face
        val numTilesPerFace = 1.5f * edgeLength * edgeLength

        // Multiply by the number of faces and add 2 to account for the 12 pentagonal
        // tiles at the vertices that take up an area equivalent to 10 hexagonal tiles
        return (numTilesPerFace * 20).toInt() + 2
    }

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
            val upperLeft = upperVertices[i]
            val upperRight = upperVertices[(i + 1) % 5]
            val lowerLeft = lowerVertices[i]
            val lowerRight = lowerVertices[(i + 1) % 5]

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
     * based on the geometry of the icosahedron they are mapped onto
     */
    @Test
    fun numTiles() {
        for (edgeLength in 1..10) {
            val expectedNumTiles = nTilesForEdgeLength(edgeLength)
            Assert.assertEquals(expectedNumTiles, HexMath3D(expectedNumTiles).getAllVectors().size)
        }
    }

    /**
     * Check that each ordinary hexagonal tile has 6 neighbours and each pentagonal tile
     * has 5 neighbours
     */
    @Test
    fun numNeighbors() {
        val edgeLength = 3

        val vertexCoords: List<Pair<Int, Int>> = listOf(
                Pair(0, 0),
                Pair(0, 9), Pair(3, 6), Pair(6, 3), Pair(9, 0), Pair(12, -3),
                Pair(6, 12), Pair(9, 9), Pair(12, 6), Pair(15, 3), Pair(18, 0),
                Pair(15, 12),
        )

        val hexMap = HexMath3D(nTilesForEdgeLength(edgeLength))
        val testCoords = hexMap.getAllVectors()

        for (coords in testCoords) {
            val isVertexCoord = vertexCoords.any {
                (x, y) -> (x == coords.x.roundToInt()) && (y == coords.y.roundToInt())
            }
            val expectedNumNeighbors = if (isVertexCoord) 5 else 6

            val neighbors = hexMap.neighboringHexCoords(coords)
            Assert.assertEquals(
                    "Incorrect number of neighbors for point ${coords}",
                    expectedNumNeighbors,
                    neighbors.size
            )
        }
    }

    /**
     * Check the distance between the positions specified by two sets of coordinates
     */
    fun checkDistance(hexMap: HexMath3D, expDistance: Float, relTol: Float, c1: Vector2, c2: Vector2) {
        val p1 = hexMap.hex2WorldCoords(c1)
        val p2 = hexMap.hex2WorldCoords(c2)
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

        val hexMap = HexMath3D(nTilesForEdgeLength(edgeLength))
        val testCoords = hexMap.getAllVectors()

        for (coords in testCoords) {
            val neighborCoordsList = hexMap.neighboringHexCoords(coords)
            for ((i, neighborCoords) in neighborCoordsList.withIndex()) {
                val nextNeighborCoords = neighborCoordsList[(i + 1) % neighborCoordsList.size]

                checkDistance(hexMap, expectedDistance, margin, coords, neighborCoords)
                checkDistance(hexMap, expectedDistance, margin, neighborCoords, nextNeighborCoords)
            }
        }
    }

    private fun checkListsContainSameElements(left: List<Vector2>, right: List<Vector2>) {
        val leftSorted = left.sortedWith(compareBy<Vector2> { it.x }.thenBy { it.y })
        val rightSorted = right.sortedWith(compareBy<Vector2> { it.x }.thenBy { it.y })
        Assert.assertTrue(leftSorted == rightSorted)
    }

    /**
     * Check method for finding tiles a given distance away
     */
    @Test
    fun vectorsAtDistance() {
        val edgeLength = 3
        val hexMap = HexMath3D(nTilesForEdgeLength(edgeLength))

        val origin = Vector2(5f, 3f)
        val actualZeroAway = hexMap.getVectorsAtDistance(origin, 0, 10, false)
        checkListsContainSameElements(listOf(origin), actualZeroAway)
        val actualUptoZero = hexMap.getVectorsInDistance(origin, 0, false)
        checkListsContainSameElements(listOf(origin), actualUptoZero)

        val expectedOneAway = listOf(
                Vector2(6f, 3f),
                Vector2(6f, 2f),
                Vector2(6f, 4f),
                Vector2(5f, 4f),
                Vector2(4f, 3f),
                Vector2(4f, 2f),
        )
        val actualOneAway = hexMap.getVectorsAtDistance(origin, 1, 10, false)
        checkListsContainSameElements(expectedOneAway, actualOneAway)
        val actualUptoOne = hexMap.getVectorsInDistance(origin, 1, false)
        checkListsContainSameElements(listOf(origin) + expectedOneAway, actualUptoOne)

        val expectedTwoAway = listOf(
                Vector2(7f, 3f),
                Vector2(7f, 4f),
                Vector2(7f, 5f),
                Vector2(6f, 5f),
                Vector2(5f, 5f),
                Vector2(4f, 4f),
                Vector2(3f, 3f),
                Vector2(3f, 2f),
                Vector2(5f, 0f),
                Vector2(6f, 1f),
                Vector2(7f, 2f),
        )
        val actualTwoAway = hexMap.getVectorsAtDistance(origin, 2, 10, false)
        checkListsContainSameElements(expectedTwoAway, actualTwoAway)
        val actualUptoTwo = hexMap.getVectorsInDistance(origin, 2, false)
        checkListsContainSameElements(listOf(origin) + expectedOneAway + expectedTwoAway, actualUptoTwo)

        val actualOneToTwo = hexMap.getVectorsInDistanceRange(origin, 1..2, false)
        checkListsContainSameElements(expectedOneAway + expectedTwoAway, actualOneToTwo)
        val actualZeroToZero = hexMap.getVectorsInDistanceRange(origin, 0..0, false)
        checkListsContainSameElements(listOf(origin), actualZeroToZero)
        val actualInvalidRange = hexMap.getVectorsInDistanceRange(origin, 2..1, false)
        checkListsContainSameElements(emptyList<Vector2>(), actualInvalidRange)
    }
}