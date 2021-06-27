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
    Vector3(1f / circumradius, -goldenRatio, goldenRatio / circumradius),
    Vector3(-(goldenRatio + 1) / circumradius, -1f, goldenRatio / circumradius),
    Vector3(-(goldenRatio + 1) / circumradius, 1f, goldenRatio / circumradius),
    Vector3(1f / circumradius, goldenRatio, goldenRatio / circumradius),
    Vector3(2f * goldenRatio / circumradius, 0f, goldenRatio / circumradius),
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
 * Return a value wrapped to the range 0 to +base.
 */
fun unsignedModInt(value: Int, base: Int): Int {
    return (((value % base) + base) % base)
}

/**
 * Return a value wrapped to the range -(base / 2) to +(base / 2).
 */
fun signedMod(value: Float, base: Float): Float {
    return unsignedMod(value + (base / 2), base) - (base / 2)
}

/**
 * Return a value wrapped to the range -(base / 2) to +(base / 2).
 */
fun signedModInt(value: Int, base: Int): Int {
    return unsignedModInt(value + (base / 2), base) - (base / 2)
}


/**
 * A class for interpreting a coordinate system comprising hexagons arranged around an icosahedron
 * (with pentagons, rather than hexagons, at each vertex).
 *
 * The coordinate system is similar to the 2D equivalent, with x pointing in the 10 o'clock
 * direction and y in the 2 o'clock direction, but arranged on the net of an icosahedron, as
 * follows:
 *
 *         .    .    .    .    .
 *        / \  / \  / \  / \  / \
 *       /___\/___\/___\/___\/___\
 *       \  / \  / \  / \  / \  / \
 *        \/___\/___\/___\/___\/___\
 *         \  / \  / \  / \  / \  /
 *          \/   \/   \/   \/   \/
 *
 * Tiles along outer edges are mapped to the western-most edge on which they would appear, and
 * the north and south poles are mapped to the central-most vertex of the 5, as shown:
 *
 *                  @             @           / @ \           @             @
 *                 __            __           \__/           __            __
 *             __/   \       __/   \       __/   \       __/   \       __/   \
 *           / . \__/ .    / . \__/ .    / . \__/ .    / . \__/ .    / . \__/ .
 *           \__/   \__    \__/   \__    \__/   \__    \__/   \__    \__/   \__
 *          /   \__/   \  /   \__/   \  /   \__/   \  /   \__/   \  /   \__/   \
 *      / @ \__/ . \__/ @ \__/ . \__/ @ \__/ . \__/ @ \__/ . \__/ @ \__/ . \__/ @
 *      \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__
 *         \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \
 *        / . \__/ . \__/ . \__/ . \__/ . \__/ . \__/ . \__/ . \__/ . \__/ . \__/ .
 *        \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__
 *           \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \
 *          / @ \__/ . \__/ @ \__/ . \__/ @ \__/ . \__/ @ \__/ . \__/ @ \__/   \__/ @
 *          \  /   \__/   \  /   \__/   \  /   \__/   \  /   \__/   \  /   \__/   \
 *             \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/   \__/
 *            / . \__/ .    / . \__/ .    / . \__/ .    / . \__/ .    / . \__/ .
 *            \__/   \      \__/   \      \__/   \      \__/   \      \__/   \
 *               \__/          \__/          \__/          \__/          \__/
 *                @             @           / @ \           @             @
 *                                          \  /
 *
 * (Note that `@` indicates vertices and `.` indicates edges, i.e. lines along which the net would
 *  be folded to form an icosahedron)
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
     * Return true if the given point lies on the net of the iosohedron
     */
    private fun isPointOnNet(edgeLength: Int, latitude: Int, longitude: Int): Boolean {
        val maxLatitude = 9 * edgeLength
        val nearSouthPole = latitude < (3 * edgeLength)
        val nearNorthPole = latitude > (6 * edgeLength)
        return when {
            ((latitude + longitude) % 2) != 0 -> false // Remove points that are not at the center of a hexagon
            nearSouthPole -> {
                val faceOffset = signedModInt(longitude, 2 * edgeLength)
                (3 * faceOffset < latitude) && (3 * faceOffset >= -latitude)
            }
            nearNorthPole -> {
                val faceOffset = signedModInt(longitude - edgeLength, 2 * edgeLength)
                (3 * faceOffset < (maxLatitude - latitude)) && (3 * faceOffset >= -(maxLatitude - latitude))
            }
            else -> true
        }
    }

    private fun getMinLongitude(edgeLength: Int, latitude: Int): Int {
        return ceil(-(4f * edgeLength) - (latitude / 3f)).toInt()
    }

    private fun getMaxLongitude(edgeLength: Int, latitude: Int): Int {
        return ceil((6f * edgeLength) - (latitude / 3f)).toInt() - 1
    }

    private fun mapToNet(edgeLength: Int, latLong: Pair<Int, Int>, baseLatLong: Pair<Int, Int>): Pair<Int, Int> {
        val (latitude, longitude) = latLong
        var (baseLatitude, baseLongitude) = baseLatLong

        val maxLatitude = (9 * edgeLength)
        val isSouthPole = latitude == 0
        val isNorthPole = latitude == maxLatitude

        if (isSouthPole) return Pair(0, 0)
        if (isNorthPole) return Pair(maxLatitude, -edgeLength)

        var outputLat = latitude
        var outputLong = longitude

        if (latitude == -1) {
            outputLat = 2
            outputLong = 4 * edgeLength * longitude
            baseLongitude = outputLong
        }
        if (latitude == -2) {
            outputLat = 2
            outputLong = 6 * edgeLength
            baseLongitude = outputLong
        }

        if (latitude == maxLatitude + 1) {
            outputLat = maxLatitude - 2
            outputLong = 4 * edgeLength * (edgeLength + longitude) - edgeLength
            baseLongitude = outputLong
        }
        if (latitude == maxLatitude + 2) {
            outputLat = maxLatitude - 2
            outputLong = 5 * edgeLength
            baseLongitude = outputLong
        }

        val nearSouthPole = outputLat < (3 * edgeLength)
        val nearNorthPole = outputLat > (6 * edgeLength)

        if (nearSouthPole) {
            val faceCenterLongitude = baseLongitude - signedModInt(baseLongitude, 2 * edgeLength)
            val faceOffset = outputLong - faceCenterLongitude
            val rightEdgeExcess = (3 * faceOffset) - outputLat
            val leftEdgeExcess = (-3 * faceOffset) - outputLat
            when {
                (rightEdgeExcess >= 0) -> {
                    outputLat += (rightEdgeExcess / 2)
                    outputLong += ((2 * edgeLength) + (rightEdgeExcess / 2) - (2 * faceOffset))
                }
                (leftEdgeExcess > 0) -> {
                    outputLat += (leftEdgeExcess / 2)
                    outputLong -= ((2 * edgeLength) + (leftEdgeExcess / 2) + (2 * faceOffset))
                }
                else -> {/* No change required */}
            }
        }

        if (nearNorthPole) {
            val faceCenterLongitude = baseLongitude - signedModInt(baseLongitude - edgeLength, 2 * edgeLength)
            val faceOffset = outputLong - faceCenterLongitude
            val rightEdgeExcess = (3 * faceOffset) - (maxLatitude - outputLat)
            val leftEdgeExcess = (-3 * faceOffset) - (maxLatitude - outputLat)
            when {
                (rightEdgeExcess >= 0) -> {
                    outputLat -= (rightEdgeExcess / 2)
                    outputLong += ((2 * edgeLength) + (rightEdgeExcess / 2) - (2 * faceOffset))
                }
                (leftEdgeExcess > 0) -> {
                    outputLat -= (leftEdgeExcess / 2)
                    outputLong -= ((2 * edgeLength) + (leftEdgeExcess / 2) + (2 * faceOffset))
                }
                else -> {/* No change required */}
            }
        }

        val minLongitude = getMinLongitude(edgeLength, outputLat)
        val maxLongitude = getMaxLongitude(edgeLength, outputLat)
        when {
            (outputLong < minLongitude) -> {
                outputLong += (10 * edgeLength)
            }
            (outputLong > maxLongitude) -> {
                outputLong -= (10 * edgeLength)
            }
            else -> {/* No change required */}
        }

        return Pair(outputLat, outputLong)
    }

    /**
     * Return a list of all vectors defining points on the mapped surface of the icosahedron.
     */
    fun getAllVectors(edgeLength: Int): List<Vector2> {
        val vectors = mutableListOf<Vector2>()
        val maxLatitude = (9 * edgeLength)
        vectors.add(latLong2Hex(0, 0))
        for (latitude in 1 until maxLatitude) {
            val minLongitude = getMinLongitude(edgeLength, latitude)
            val maxLongitude = getMaxLongitude(edgeLength, latitude)
            for (longitude in minLongitude..maxLongitude) {
                if (isPointOnNet(edgeLength, latitude, longitude)) {
                    vectors.add(latLong2Hex(latitude, longitude))
                }
            }
        }
        vectors.add(latLong2Hex(maxLatitude, -edgeLength))
        return vectors
    }

    private fun interpolateVertices(edgeLength: Int, left: Vector3, right: Vector3, other: Vector3, x: Float, y: Float): Vector3 {
        val yRatio = y / (edgeLength * 1.5f)
        val leftEdgeRatio = (1 - yRatio + x / edgeLength) * 0.5f
        val rightEdgeRatio = (1 - yRatio - x / edgeLength) * 0.5f
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

    private fun hex2LatLong(hexCoords: Vector2): Pair<Int, Int> {
        val latitude = hexCoords.x + hexCoords.y
        val longitude = hexCoords.y - hexCoords.x
        return Pair(latitude.roundToInt(), longitude.roundToInt())
    }

    private fun latLong2Hex(latitude: Int, longitude: Int): Vector2 {
        val x = 0.5f * (latitude.toFloat() - longitude.toFloat())
        val y = 0.5f * (latitude.toFloat() + longitude.toFloat())
        return Vector2(x, y)
    }

    fun hex2WorldCoords(edgeLength: Int, hexCoords: Vector2): Vector3 {

        val (latitude, longitude) = hex2LatLong(hexCoords)

        val nearSouthPole = latitude < (3 * edgeLength)
        val nearNorthPole = latitude > (6 * edgeLength)

        return when {
            nearSouthPole -> {
                val tileIdx = (longitude + (5 * edgeLength)) / (2 * edgeLength)
                val leftVertex = lowerVertices[tileIdx % 5]
                val rightVertex = lowerVertices[(tileIdx + 1) % 5]
                val otherVertex = bottomVertex
                val xOffset = signedModInt(longitude, 2 * edgeLength)
                projectOntoSphere(
                        edgeLength,
                        interpolateVertices(
                                edgeLength,
                                leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                                xOffset.toFloat(),
                                0.5f * ((3 * edgeLength) - latitude).toFloat()
                        )
                )
            }
            nearNorthPole -> {
                val tileIdx = (longitude + (6 * edgeLength)) / (2 * edgeLength)
                val leftVertex = upperVertices[tileIdx % 5]
                val rightVertex = upperVertices[(tileIdx + 1) % 5]
                val otherVertex = topVertex
                val xOffset = signedModInt(longitude + edgeLength, 2 * edgeLength)
                projectOntoSphere(
                        edgeLength,
                        interpolateVertices(
                                edgeLength,
                                leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                                xOffset.toFloat(),
                                0.5f * (latitude - (6 * edgeLength)).toFloat()
                        )
                )
            }
            else -> {
                val xOffset = signedModInt(longitude, 2 * edgeLength)
                val distBelowUpperCircle = (6 * edgeLength - latitude)
                if ((3 * xOffset < distBelowUpperCircle) && (3 * xOffset >= -distBelowUpperCircle)) {
                    val tileIdx = (longitude + (5 * edgeLength)) / (2 * edgeLength)
                    val leftVertex = lowerVertices[tileIdx % 5]
                    val rightVertex = lowerVertices[(tileIdx + 1) % 5]
                    val otherVertex = upperVertices[(tileIdx + 1) % 5]
                    val xOffset = signedModInt(longitude, 2 * edgeLength)
                    projectOntoSphere(
                            edgeLength,
                            interpolateVertices(
                                    edgeLength,
                                    leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                                    xOffset.toFloat(),
                                    0.5f * (latitude - (3 * edgeLength)).toFloat()
                            )
                    )
                } else {
                    val tileIdx = (longitude + (6 * edgeLength)) / (2 * edgeLength)
                    val leftVertex = upperVertices[tileIdx % 5]
                    val rightVertex = upperVertices[(tileIdx + 1) % 5]
                    val otherVertex = lowerVertices[tileIdx % 5]
                    val xOffset = signedModInt(longitude + edgeLength, 2 * edgeLength)
                    projectOntoSphere(
                            edgeLength,
                            interpolateVertices(
                                    edgeLength,
                                    leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                                    xOffset.toFloat(),
                                    0.5f * ((6 * edgeLength) - latitude).toFloat()
                            )
                    )
                }
            }
        }
    }

    fun neighbouringHexCoords(edgeLength: Int, hexCoords: Vector2): List<Vector2> {
        return HexMath.getAdjacentVectors(hexCoords).map{
            mapToNet(edgeLength, hex2LatLong(it), hex2LatLong(hexCoords))
        }.distinct().map{
            val (latitude, longitude) = it
            latLong2Hex(latitude, longitude)
        }
    }
}
