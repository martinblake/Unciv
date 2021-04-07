package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import kotlin.math.*

/**
 * The following derived dimensions relate to an icosahedron with an edge length of 2
 */

// Standard mathematical quantity
val goldenRatio = (1f + sqrt(5f)) / 2f

// Radius of the sphere that passes through each vertex
val circumradius = sqrt(goldenRatio + 2f)

// Single vertex at the top (north pole)
val topVertex = Vector3(0f, 0f, circumradius)

// Single vertex at the bottom (south pole)
val bottomVertex = Vector3(0f, 0f, -sqrt(goldenRatio + 2f))

// Five vertices nearest the top vertex
val upperVertices = arrayOf<Vector3>(
    Vector3(-(goldenRatio + 1) / circumradius, -1f, goldenRatio / circumradius),
    Vector3(-(goldenRatio + 1) / circumradius, 1f, goldenRatio / circumradius),
    Vector3(1f / circumradius, goldenRatio, goldenRatio / circumradius),
    Vector3(2f * goldenRatio / circumradius, 0f, goldenRatio / circumradius),
    Vector3(1f / circumradius, -goldenRatio, goldenRatio / circumradius),
)

// Five vertices nearest the bottom vertex
val lowerVertices = arrayOf<Vector3>(
    Vector3(-1f / circumradius, -goldenRatio, -goldenRatio / circumradius),
    Vector3(-2f * goldenRatio / circumradius, 0f, -goldenRatio / circumradius),
    Vector3(-1f / circumradius, goldenRatio, -goldenRatio / circumradius),
    Vector3((goldenRatio + 1) / circumradius, 1f, -goldenRatio / circumradius),
    Vector3((goldenRatio + 1) / circumradius, -1f, -goldenRatio / circumradius),
)

/**
 * Return a value wrapped to the range 0 to +base.
 */
fun unsignedMod(value: Float, base: Float): Float {
    return (((value % base) + base) % base)
}

/**
 * Return a value wrapped to the range -(base / 2) to +(base / 2).
 */
fun signedMod(value: Float, base: Float): Float {
    return unsignedMod(value + (base / 2), base) - (base / 2)
}


/**
 * Functions for interpreting a coordinate system comprising hexagons arranged around
 * an icosahedron (with pentagons, rather than hexagons, at each vertex).
 *
 * The y-component starts as zero at the north pole and increases positively with
 * decreasing latitude.  Lines of constant y-component start as rings around the north
 * pole, expand to zig-zags near the equator, then eventually converge symmetrically on
 * the south pole.
 *
 * The x-component has a range of values that increases by 5 hexagons per row near the
 * north pole, stays the same near the equator, then decreases by 5 hexagons per row
 * near the south pole.  The line of zero-x moves vertically down the centre of the first
 * 1 + 1/3 triangular faces, then proceeds to cross the equator in the 4 o'clock direction
 * in order to approach the south pole similarly along the centre of a triangular face.
 */
object HexMath3D {

    /**
     * Return the triangular face side length for an icosahedron approximation to a sphere
     * with a similar number of tiles to the given number.
     *
     * An icosahedron has 20 triangular faces, each with 1.5 n^2 hexagons, where
     * n is the number of complete hexagons lining the edge of each face.
     */
    fun getIcosahedronEdgeLength(nTiles: Int): Int {
        return round(sqrt((2f / 3f) * nTiles / 20f)).toInt()
    }

    /**
     * Return the number of tiles in the given row, counting from north to south pole.
     */
    private fun getTilesPerRow(edgeLength: Int, row: Int): Int {
        val maxRow = 5 * edgeLength
        return when {
            (row == 0) -> 1  // North pole
            (row < (2 * edgeLength)) -> (5 * row)  // Top increasing diameter section
            (row < (3 * edgeLength)) -> (10 * edgeLength)  // Middle constant diameter section
            (row < (5 * edgeLength)) -> (5 * (maxRow - row) )  // Bottom decreasing diameter section
            (row == maxRow) -> 1  // South pole
            else -> 0
        }
    }

    /**
     * Return a list of all vectors defining points on the mapped surface of the icosahedron.
     */
    fun getAllVectors(edgeLength: Int): List<Vector2> {
        val vectors = mutableListOf<Vector2>()
        val maxY = (5 * edgeLength)
        for (y in 0 until (maxY + 1)) {
            val numX = getTilesPerRow(edgeLength, y)
            for (x in 0 until numX) {
                vectors.add(Vector2(x.toFloat(), y.toFloat()))
            }
        }
        return vectors
    }

    private fun interpolateVertices(edgeLength: Int, left: Vector3, right: Vector3, other: Vector3, x: Float, y: Float): Vector3 {
        val yRatio = y / (edgeLength * 1.5f)
        val leftEdgeRatio = (1 - yRatio - x / edgeLength) * 0.5f
        val rightEdgeRatio = (1 - yRatio + x / edgeLength) * 0.5f
        return (
            right.scl(leftEdgeRatio)
        ).add(
            left.scl(rightEdgeRatio)
        ).add(
            other.scl(yRatio)
        )
    }

    private fun projectOntoSphere(edgeLength: Int, position: Vector3): Vector3 {
        return position.nor().scl(circumradius)
    }

    fun hex2WorldCoords(edgeLength: Int, hexCoords: Vector2): Vector3 {
        val tilesPerRow = getTilesPerRow(edgeLength, hexCoords.y.roundToInt())
        val tilesPerPeriod = tilesPerRow / 5

        if (hexCoords.y.roundToInt() == 0)  {
            return projectOntoSphere(edgeLength, topVertex.cpy())
        } else if (hexCoords.y.roundToInt() <= (2 * edgeLength)) {
            // Calculate x displacement from the nearest face centre line, in hexagons
            val xOffset = signedMod(hexCoords.x, tilesPerPeriod.toFloat())

            // Calculate y displacement from north pole, in hexagons
            val yOffset = hexCoords.y - (0.5f * abs(xOffset))

            // Identify the horizontally neighbouring vertices for the face on which
            // this tile is found
            val tileIdx = (hexCoords.x / tilesPerPeriod).roundToInt()
            val rightVertex = upperVertices[tileIdx % 5]
            val leftVertex = upperVertices[(tileIdx + 1) % 5]

            // Identify the other vertex
            val otherVertex = if (yOffset <= (edgeLength * 1.5)) {
                topVertex
            } else {
                lowerVertices[(tileIdx + 1) % 5]
            }

            // Determine whether we're above or below the horizontal edge
            val ySignFromEdge = if (yOffset <= (edgeLength * 1.5)) {
                +1f
            } else {
                -1f
            }

            // Calculate the tile position
            return projectOntoSphere(
                    edgeLength,
                    interpolateVertices(
                            edgeLength,
                            leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                            xOffset, ySignFromEdge * ((edgeLength * 1.5f) - yOffset)
                    )
            )
        } else if (hexCoords.y.roundToInt() <= (3 * edgeLength)) {
            val yInSegment = hexCoords.y - (2 * edgeLength)

            // Calculate x displacement from the nearest upper triangle face centre line, in hexagons
            val xOffset = signedMod(hexCoords.x - yInSegment, tilesPerPeriod.toFloat())

            // Calculate y displacement from top pentagonal edge, in hexagons
            val yOffset = hexCoords.y - (0.5f * abs(xOffset)) - (1.5f * edgeLength)

            if ((yOffset + (1.5f * abs(xOffset))) <= (1.5f * edgeLength)) {
                // Identify the horizontally neighbouring vertices for the face on which
                // this tile is found
                val tileIdx = ((hexCoords.x + tilesPerRow - yInSegment) / tilesPerPeriod).roundToInt()
                val rightVertex = upperVertices[tileIdx % 5]
                val leftVertex = upperVertices[(tileIdx + 1) % 5]

                // Identify the other vertex
                val otherVertex = lowerVertices[(tileIdx + 1) % 5]

                // Calculate the tile position
                return projectOntoSphere(
                        edgeLength,
                        interpolateVertices(
                                edgeLength,
                                leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                                xOffset, yOffset
                        )
                )
            } else {
                // Identify the horizontally neighbouring vertices for the face on which
                // this tile is found
                val tileIdx = ((hexCoords.x - yInSegment + edgeLength) / tilesPerPeriod).roundToInt()
                val rightVertex = lowerVertices[tileIdx % 5]
                val leftVertex = lowerVertices[(tileIdx + 1) % 5]

                // Identify the other vertex
                val otherVertex = upperVertices[tileIdx % 5]

                // Calculate the tile position
                return projectOntoSphere(
                        edgeLength,
                        interpolateVertices(
                                edgeLength,
                                leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                                signedMod(xOffset + edgeLength, tilesPerPeriod.toFloat()),
                                (edgeLength * 1.5f) - yOffset
                        )
                )
            }
        } else if (hexCoords.y.roundToInt() < (5 * edgeLength)) {
            // Calculate x displacement from the nearest face centre line, in hexagons
            val xOffset = signedMod(hexCoords.x, tilesPerPeriod.toFloat())

            // Calculate y displacement from south pole, in hexagons
            val yOffset = (5 * edgeLength) - hexCoords.y - (0.5f * abs(xOffset))

            // Identify the horizontally neighbouring vertices for the face on which
            // this tile is found
            val tileIdx = (hexCoords.x / tilesPerPeriod).roundToInt()
            val rightVertex = lowerVertices[tileIdx % 5]
            val leftVertex = lowerVertices[(tileIdx + 1) % 5]

            // Identify the other vertex
            val otherVertex = if (yOffset <= (edgeLength * 1.5)) {
                bottomVertex
            } else {
                upperVertices[tileIdx % 5]
            }

            // Determine whether we're above or below the horizontal edge
            val ySignFromEdge = if (yOffset <= (edgeLength * 1.5)) {
                +1f
            } else {
                -1f
            }

            // Calculate the tile position
            return projectOntoSphere(
                    edgeLength,
                    interpolateVertices(
                            edgeLength,
                            leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                            xOffset, ySignFromEdge * ((edgeLength * 1.5f) - yOffset)
                    )
            )
        } else {
            return projectOntoSphere(edgeLength, bottomVertex.cpy())
        }
    }

    fun neighbouringHexCoords(edgeLength: Int, hexCoords: Vector2): List<Vector2> {

        if (hexCoords.y.roundToInt() == 0) {
            return 4.downTo(0).map { Vector2(it.toFloat(), 1f) }
        } else if (hexCoords.y.roundToInt() == (5 * edgeLength)) {
            return (0..4).map { Vector2(it.toFloat(), ((5f * edgeLength) - 1f)) }
        } else {
            val nTiles = getTilesPerRow(edgeLength, hexCoords.y.roundToInt()).toFloat()
            val nTilesAbove = getTilesPerRow(edgeLength, hexCoords.y.roundToInt() - 1).toFloat()
            val nTilesBelow = getTilesPerRow(edgeLength, hexCoords.y.roundToInt() + 1).toFloat()

            val neighboursAbove = if (nTilesAbove < nTiles) {
                if (hexCoords.x.roundToInt() % (nTiles.roundToInt() / 5) == 0) {
                    listOf(
                            Vector2(hexCoords.x * nTilesAbove / nTiles, hexCoords.y - 1f),
                    )
                } else {
                    listOf(
                            Vector2(floor(hexCoords.x * nTilesAbove / nTiles), hexCoords.y - 1f),
                            Vector2(ceil(hexCoords.x * nTilesAbove / nTiles), hexCoords.y - 1f),
                    )
                }
            } else if (nTilesAbove > nTiles) {
                if (hexCoords.x.roundToInt() % (nTiles.roundToInt() / 5) == 0) {
                    listOf(
                            Vector2(hexCoords.x * nTilesAbove / nTiles + nTilesAbove - 1f, hexCoords.y - 1f),
                            Vector2(hexCoords.x * nTilesAbove / nTiles, hexCoords.y - 1f),
                            Vector2(hexCoords.x * nTilesAbove / nTiles + 1f, hexCoords.y - 1f),
                    )
                } else {
                    listOf(
                            Vector2(floor(hexCoords.x * nTilesAbove / nTiles), hexCoords.y - 1f),
                            Vector2(ceil(hexCoords.x * nTilesAbove / nTiles), hexCoords.y - 1f),
                    )
                }
            } else {
                val xOffset = unsignedMod(hexCoords.x - hexCoords.y + (2 * edgeLength), nTiles / 5f).roundToInt()
                if (xOffset == 0) {
                    listOf(
                            Vector2(unsignedMod(hexCoords.x - 1f, nTiles), hexCoords.y - 1f),
                    )
                } else if (xOffset < edgeLength) {
                    listOf(
                            Vector2(unsignedMod(hexCoords.x - 2f, nTiles), hexCoords.y - 1f),
                            Vector2(unsignedMod(hexCoords.x - 1f, nTiles), hexCoords.y - 1f),
                    )
                } else if (xOffset == edgeLength) {
                    listOf(
                            Vector2(unsignedMod(hexCoords.x - 2f, nTiles), hexCoords.y - 1f),
                            Vector2(unsignedMod(hexCoords.x - 1f, nTiles), hexCoords.y - 1f),
                            Vector2(hexCoords.x, hexCoords.y - 1f),
                    )
                } else {
                    listOf(
                            Vector2(unsignedMod(hexCoords.x - 1f, nTiles), hexCoords.y - 1f),
                            Vector2(hexCoords.x, hexCoords.y - 1f),
                    )
                }
            }

            val neighboursBelow = if (nTilesBelow > nTiles) {
                if (hexCoords.x.roundToInt() % (nTiles.roundToInt() / 5) == 0) {
                    listOf(
                            Vector2(hexCoords.x * nTilesBelow / nTiles + 1f, hexCoords.y + 1f),
                            Vector2(hexCoords.x * nTilesBelow / nTiles, hexCoords.y + 1f),
                            Vector2(hexCoords.x * nTilesBelow / nTiles + nTilesBelow - 1f, hexCoords.y + 1f),
                    )
                } else {
                    listOf(
                            Vector2(ceil(hexCoords.x * nTilesBelow / nTiles), hexCoords.y + 1f),
                            Vector2(floor(hexCoords.x * nTilesBelow / nTiles), hexCoords.y + 1f),
                    )
                }
            } else if (nTilesBelow < nTiles) {
                if (hexCoords.x.roundToInt() % (nTiles.roundToInt() / 5) == 0) {
                    listOf(
                            Vector2(hexCoords.x * nTilesBelow / nTiles, hexCoords.y + 1f),
                    )
                } else {
                    listOf(
                            Vector2(ceil(hexCoords.x * nTilesBelow / nTiles), hexCoords.y + 1f),
                            Vector2(floor(hexCoords.x * nTilesBelow / nTiles), hexCoords.y + 1f),
                    )
                }
            } else {
                val xOffset = unsignedMod(hexCoords.x - hexCoords.y + (2 * edgeLength), nTiles / 5f).roundToInt()
                if (xOffset == 0) {
                    listOf(
                            Vector2(unsignedMod(hexCoords.x + 2f, nTiles), hexCoords.y + 1f),
                            Vector2(unsignedMod(hexCoords.x + 1f, nTiles), hexCoords.y + 1f),
                            Vector2(hexCoords.x, hexCoords.y + 1f),
                    )
                } else if (xOffset < edgeLength) {
                    listOf(
                            Vector2(unsignedMod(hexCoords.x + 2f, nTiles), hexCoords.y + 1f),
                            Vector2(unsignedMod(hexCoords.x + 1f, nTiles), hexCoords.y + 1f),
                    )
                } else if (xOffset == edgeLength) {
                    listOf(
                            Vector2(unsignedMod(hexCoords.x + 1f, nTiles), hexCoords.y + 1f),
                    )
                } else {
                    listOf(
                            Vector2(unsignedMod(hexCoords.x + 1f, nTiles), hexCoords.y + 1f),
                            Vector2(hexCoords.x, hexCoords.y + 1f),
                    )
                }
            }

            return listOf(
                    neighboursAbove,
                    listOf(Vector2(unsignedMod(hexCoords.x + 1, nTiles), hexCoords.y)),
                    neighboursBelow,
                    listOf(Vector2(unsignedMod(hexCoords.x - 1, nTiles), hexCoords.y)),
            ).flatten()
        }
    }

}
