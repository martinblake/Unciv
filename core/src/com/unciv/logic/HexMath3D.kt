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
    Vector3(1f / circumradius, goldenRatio, goldenRatio / circumradius),
    Vector3(-(goldenRatio + 1) / circumradius, 1f, goldenRatio / circumradius),
    Vector3(-(goldenRatio + 1) / circumradius, -1f, goldenRatio / circumradius),
    Vector3(1f / circumradius, -goldenRatio, goldenRatio / circumradius),
    Vector3(2f * goldenRatio / circumradius, 0f, goldenRatio / circumradius),
)

// Five vertices nearest the bottom vertex
val lowerVertices = arrayOf<Vector3>(
    Vector3(-1f / circumradius, goldenRatio, -goldenRatio / circumradius),
    Vector3(-2f * goldenRatio / circumradius, 0f, -goldenRatio / circumradius),
    Vector3(-1f / circumradius, -goldenRatio, -goldenRatio / circumradius),
    Vector3((goldenRatio + 1) / circumradius, -1f, -goldenRatio / circumradius),
    Vector3((goldenRatio + 1) / circumradius, 1f, -goldenRatio / circumradius),
)


/**
 * Return a value wrapped to the range 0 to +base.
 */
private fun unsignedMod(value: Int, base: Int): Int {
    return (((value % base) + base) % base)
}

/**
 * Return a value wrapped to the range -(base / 2) to +(base / 2).
 */
private fun signedMod(value: Int, base: Int): Int {
    return unsignedMod(value + (base / 2), base) - (base / 2)
}

private fun integerDiv(value: Int, base: Int): Int {
    return (value - unsignedMod(value, base)) / base
}

enum class Rotation {
    CLOCKWISE_60
    {
        override fun apply(vector: Vector2): Vector2 = Vector2(vector.x - vector.y, vector.x)
    },
    CLOCKWISE_120
    {
        override fun apply(vector: Vector2): Vector2 = Vector2(-vector.y, vector.x - vector.y)
    },
    ANTICLOCKWISE_60
    {
        override fun apply(vector: Vector2): Vector2 = Vector2(vector.y, vector.y - vector.x)
    },
    ANTICLOCKWISE_120
    {
        override fun apply(vector: Vector2): Vector2 = Vector2(vector.y - vector.x, -vector.x)
    };

    /**
     * Apply rotation to a hex-coordinate vector (with x pointing in 10 o'clock and
     * y in 2 o'clock directions)
     */
    abstract fun apply(vector: Vector2): Vector2
}

private class Face(val latitude: Latitude, val longitude: Int) {

    enum class Latitude {
        POLAR_NORTH,
        EQUATORIAL_NORTH,
        EQUATORIAL_SOUTH,
        POLAR_SOUTH
    }

    fun centerX(): Int {
        return when (latitude) {
            Latitude.POLAR_NORTH -> (6 - longitude)
            Latitude.EQUATORIAL_NORTH -> (5 - longitude)
            Latitude.EQUATORIAL_SOUTH -> (4 - longitude)
            Latitude.POLAR_SOUTH -> (3 - longitude)
        }
    }

    fun centerY(): Int {
        return when (latitude) {
            Latitude.POLAR_NORTH -> (longitude + 1)
            Latitude.EQUATORIAL_NORTH -> longitude
            Latitude.EQUATORIAL_SOUTH -> longitude
            Latitude.POLAR_SOUTH -> (longitude - 1)
        }
    }
}


/**
 * Location on the surface of an icosahedron.
 *  face -- the triangular face
 *  coords -- the (x, y) hex-coordinates of the location relative to the face center
 */
private class SurfaceLocation(val face: Face, val coords: Vector2) {

    /**
     * Return the face corresponding to the given displacement from this face's center.
     * The displacement is specified in hex-coordinates as fractions of the edge length of a face.
     */
    fun add(diff: Vector2): SurfaceLocation? {
        val newCoords = coords.cpy().add(diff)
        val x = newCoords.x + (1e-6f * newCoords.y)  // Rotate a consistent very small amount, so that
        val y = newCoords.y - (1e-6f * newCoords.x)  // points along edges aren't double-counted
        val eastLongitude = unsignedMod(face.longitude + 1, 5)
        val westLongitude = unsignedMod(face.longitude - 1, 5)
        val farEastLongitude = unsignedMod(face.longitude + 2, 5)
        val farWestLongitude = unsignedMod(face.longitude - 2, 5)
        val x_dist = 2f * x - y
        val y_dist = 2f * y - x
        val z_dist = -(x + y)
        val t1 = 1f
        val t2 = 2f
        return when (face.latitude) {
            Face.Latitude.POLAR_NORTH -> when {
                /**
                 * North-pointing triangular face
                 *          .    .
                 *         / \  / \
                 *        /___\/___\
                 *      / \  / \  / \
                 *     /___\/___\/___\
                 *        / \  / \
                 *       /___\/___\
                 */
                (x_dist <= t1) && (y_dist <= t1) && (z_dist <= t1) -> SurfaceLocation(
                        Face(face.latitude, face.longitude),
                        Vector2(0f, 0f).add(x, y)
                )
                (x_dist > t1) && (y_dist <= t1) && (z_dist <= t1) -> when {
                    (y_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_NORTH, westLongitude),
                            Rotation.ANTICLOCKWISE_60.apply(Vector2(-1f, 1f).add(x, y))
                    )
                    (z_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_NORTH, farWestLongitude),
                            Rotation.ANTICLOCKWISE_120.apply(Vector2(-2f, -1f).add(x, y))
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_NORTH, westLongitude),
                            Rotation.ANTICLOCKWISE_60.apply(Vector2(-1f, 0f).add(x, y))
                    )
                }
                (y_dist > t1) && (z_dist <= t1) && (x_dist <= t1) -> when {
                    (z_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_NORTH, farEastLongitude),
                            Rotation.CLOCKWISE_120.apply(Vector2(-1f, -2f).add(x, y))
                    )
                    (x_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_NORTH, eastLongitude),
                            Rotation.CLOCKWISE_60.apply(Vector2(1f, -1f).add(x, y))
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_NORTH, eastLongitude),
                            Rotation.CLOCKWISE_60.apply(Vector2(0f, -1f).add(x, y))
                    )
                }
                (z_dist > t1) && (x_dist <= t1) && (y_dist <= t1) -> when {
                    (x_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_SOUTH, face.longitude),
                            Vector2(2f, 1f).add(x, y)
                    )
                    (y_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_SOUTH, westLongitude),
                            Vector2(1f, 2f).add(x, y)
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_NORTH, face.longitude),
                            Vector2(1f, 1f).add(x, y)
                    )
                }
                else -> null
            }
            Face.Latitude.EQUATORIAL_NORTH -> when {
                /**
                 * South-pointing triangular face
                 *        .___..___.
                 *        \  / \  /
                 *     .___\/___\/___.
                 *     \  / \  / \  /
                 *      \/___\/___\/
                 *       \  / \  /
                 *        \/   \/
                 */
                (x_dist >= -t1) && (y_dist >= -t1) && (z_dist >= -t1) -> SurfaceLocation(
                        Face(face.latitude, face.longitude),
                        Vector2(0f, 0f).add(x, y)
                )
                (x_dist < -t1) && (y_dist >= -t1) && (z_dist >= -t1) -> when {
                    (y_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_NORTH, eastLongitude),
                            Vector2(1f, -1f).add(x, y)
                    )
                    (z_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_SOUTH, face.longitude),
                            Vector2(2f, 1f).add(x, y)
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_SOUTH, face.longitude),
                            Vector2(1f, 0f).add(x, y)
                    )
                }
                (y_dist < -t1) && (z_dist >= -t1) && (x_dist >= -t1) -> when {
                    (z_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_SOUTH, westLongitude),
                            Vector2(1f, 2f).add(x, y)
                    )
                    (x_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_NORTH, westLongitude),
                            Vector2(-1f, 1f).add(x, y)
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_SOUTH, westLongitude),
                            Vector2(0f, 1f).add(x, y)
                    )
                }
                (z_dist < -t1) && (x_dist >= -t1) && (y_dist >= -t1) -> when {
                    (x_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_NORTH, westLongitude),
                            Rotation.ANTICLOCKWISE_60.apply(Vector2(-2f, -1f).add(x, y))
                    )
                    (y_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_NORTH, eastLongitude),
                            Rotation.CLOCKWISE_60.apply(Vector2(-1f, -2f).add(x, y))
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_NORTH, face.longitude),
                            Vector2(-1f, -1f).add(x, y)
                    )
                }
                else -> null
            }
            Face.Latitude.EQUATORIAL_SOUTH -> when {
                /**
                 * North-pointing triangular face
                 *          .    .
                 *         / \  / \
                 *        /___\/___\
                 *      / \  / \  / \
                 *     /___\/___\/___\
                 *        / \  / \
                 *       /___\/___\
                 */
                (x_dist <= t1) && (y_dist <= t1) && (z_dist <= t1) -> SurfaceLocation(
                        Face(face.latitude, face.longitude),
                        Vector2(0f, 0f).add(x, y)
                )
                (x_dist > t1) && (y_dist <= t1) && (z_dist <= t1) -> when {
                    (y_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_SOUTH, westLongitude),
                            Vector2(-1f, 1f).add(x, y)
                    )
                    (z_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_NORTH, face.longitude),
                            Vector2(-2f, -1f).add(x, y)
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_NORTH, face.longitude),
                            Vector2(-1f, 0f).add(x, y)
                    )
                }
                (y_dist > t1) && (z_dist <= t1) && (x_dist <= t1) -> when {
                    (z_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_NORTH, eastLongitude),
                            Vector2(-1f, -2f).add(x, y)
                    )
                    (x_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_SOUTH, eastLongitude),
                            Vector2(1f, -1f).add(x, y)
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_NORTH, eastLongitude),
                            Vector2(0f, -1f).add(x, y)
                    )
                }
                (z_dist > t1) && (x_dist <= t1) && (y_dist <= t1) -> when {
                    (x_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_SOUTH, eastLongitude),
                            Rotation.ANTICLOCKWISE_60.apply(Vector2(2f, 1f).add(x, y))
                    )
                    (y_dist <= -t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_SOUTH, westLongitude),
                            Rotation.CLOCKWISE_60.apply(Vector2(1f, 2f).add(x, y))
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_SOUTH, face.longitude),
                            Vector2(1f, 1f).add(x, y)
                    )
                }
                else -> null
            }
            Face.Latitude.POLAR_SOUTH -> when {
                /**
                 * South-pointing triangular face
                 *        .___..___.
                 *        \  / \  /
                 *     .___\/___\/___.
                 *     \  / \  / \  /
                 *      \/___\/___\/
                 *       \  / \  /
                 *        \/   \/
                 */
                (x_dist >= -t1) && (y_dist >= -t1) && (z_dist >= -t1) -> SurfaceLocation(
                        Face(face.latitude, face.longitude),
                        Vector2(0f, 0f).add(x, y)
                )
                (x_dist < -t1) && (y_dist >= -t1) && (z_dist >= -t1) -> when {
                    (y_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_SOUTH, eastLongitude),
                            Rotation.ANTICLOCKWISE_60.apply(Vector2(1f, -1f).add(x, y))
                    )
                    (z_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_SOUTH, farEastLongitude),
                            Rotation.ANTICLOCKWISE_120.apply(Vector2(2f, 1f).add(x, y))
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_SOUTH, eastLongitude),
                            Rotation.ANTICLOCKWISE_60.apply(Vector2(1f, 0f).add(x, y))
                    )
                }
                (y_dist < -t1) && (z_dist >= -t1) && (x_dist >= -t1) -> when {
                    (z_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_SOUTH, farWestLongitude),
                            Rotation.CLOCKWISE_120.apply(Vector2(1f, 2f).add(x, y))
                    )
                    (x_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_SOUTH, westLongitude),
                            Rotation.CLOCKWISE_60.apply(Vector2(-1f, 1f).add(x, y))
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.POLAR_SOUTH, westLongitude),
                            Rotation.CLOCKWISE_60.apply(Vector2(0f, 1f).add(x, y))
                    )
                }
                (z_dist < -t1) && (x_dist >= -t1) && (y_dist >= -t1) -> when {
                    (x_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_NORTH, face.longitude),
                            Vector2(-2f, -1f).add(x, y)
                    )
                    (y_dist >= t2) -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_NORTH, eastLongitude),
                            Vector2(-1f, -2f).add(x, y)
                    )
                    else -> SurfaceLocation(
                            Face(Face.Latitude.EQUATORIAL_SOUTH, face.longitude),
                            Vector2(-1f, -1f).add(x, y)
                    )
                }
                else -> null
            }
        }
    }
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
class HexMath3D(nTilesApprox: Int) {

    private val edgeLength = getIcosahedronEdgeLength(nTilesApprox)

    /**
     * Return the triangular face side length for an icosahedron approximation to a sphere
     * with a similar number of tiles to the given number.
     *
     * An icosahedron has 20 triangular faces, each with 1.5 n^2 hexagons, where
     * n is the number of complete hexagons lining the edge of each face.
     */
    private fun getIcosahedronEdgeLength(nTilesApprox: Int): Int {
        return round(sqrt((2f / 3f) * nTilesApprox / 20f)).toInt()
    }

    /**
     * Return true if the given point lies on the net of the iosohedron
     */
    private fun isPointOnNet(latitude: Int, longitude: Int): Boolean {
        val maxLatitude = 9 * edgeLength
        val nearSouthPole = latitude < (3 * edgeLength)
        val nearNorthPole = latitude > (6 * edgeLength)
        return when {
            ((latitude + longitude) % 2) != 0 -> false // Remove points that are not at the center of a hexagon
            nearSouthPole -> {
                val faceOffset = signedMod(longitude, 2 * edgeLength)
                (3 * faceOffset < latitude) && (3 * faceOffset >= -latitude)
            }
            nearNorthPole -> {
                val faceOffset = signedMod(longitude - edgeLength, 2 * edgeLength)
                (3 * faceOffset < (maxLatitude - latitude)) && (3 * faceOffset >= -(maxLatitude - latitude))
            }
            else -> true
        }
    }

    private fun getMinLongitude(latitude: Int): Int {
        return ceil(-(4f * edgeLength) - (latitude / 3f)).toInt()
    }

    private fun getMaxLongitude(latitude: Int): Int {
        return ceil((6f * edgeLength) - (latitude / 3f)).toInt() - 1
    }

    /**
     * Return a list of all vectors defining points on the mapped surface of the icosahedron.
     */
    fun getAllVectors(): List<Vector2> {
        val vectors = mutableListOf<Vector2>()
        val maxLatitude = (9 * edgeLength)
        vectors.add(latLong2Hex(0, 0))
        for (latitude in 1 until maxLatitude) {
            val minLongitude = getMinLongitude(latitude)
            val maxLongitude = getMaxLongitude(latitude)
            for (longitude in minLongitude..maxLongitude) {
                if (isPointOnNet(latitude, longitude)) {
                    vectors.add(latLong2Hex(latitude, longitude))
                }
            }
        }
        vectors.add(latLong2Hex(maxLatitude, -edgeLength))
        return vectors
    }

    private fun interpolateVertices(left: Vector3, right: Vector3, other: Vector3, x: Float, y: Float): Vector3 {
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

    private fun projectOntoSphere(position: Vector3): Vector3 {
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

    fun hex2WorldCoords(hexCoords: Vector2): Vector3 {

        val (latitude, longitude) = hex2LatLong(hexCoords)

        val nearSouthPole = latitude < (3 * edgeLength)
        val nearNorthPole = latitude > (6 * edgeLength)

        return when {
            nearSouthPole -> {
                val tileIdx = unsignedMod(integerDiv(longitude + (5 * edgeLength), (2 * edgeLength)), 5)
                val leftVertex = lowerVertices[tileIdx % 5]
                val rightVertex = lowerVertices[(tileIdx + 1) % 5]
                val otherVertex = bottomVertex
                val xOffset = signedMod(longitude, 2 * edgeLength)
                projectOntoSphere(
                        interpolateVertices(
                                leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                                xOffset.toFloat(),
                                0.5f * ((3 * edgeLength) - latitude).toFloat()
                        )
                )
            }
            nearNorthPole -> {
                val tileIdx = unsignedMod(integerDiv(longitude + (6 * edgeLength), (2 * edgeLength)), 5)
                val leftVertex = upperVertices[tileIdx % 5]
                val rightVertex = upperVertices[(tileIdx + 1) % 5]
                val otherVertex = topVertex
                val xOffset = signedMod(longitude + edgeLength, 2 * edgeLength)
                projectOntoSphere(
                        interpolateVertices(
                                leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                                xOffset.toFloat(),
                                0.5f * (latitude - (6 * edgeLength)).toFloat()
                        )
                )
            }
            else -> {
                val xOffset = signedMod(longitude, 2 * edgeLength)
                val distBelowUpperCircle = (6 * edgeLength - latitude)
                if ((3 * xOffset < distBelowUpperCircle) && (3 * xOffset >= -distBelowUpperCircle)) {
                    val tileIdx = unsignedMod(integerDiv(longitude + (5 * edgeLength), (2 * edgeLength)), 5)
                    val leftVertex = lowerVertices[tileIdx % 5]
                    val rightVertex = lowerVertices[(tileIdx + 1) % 5]
                    val otherVertex = upperVertices[(tileIdx + 1) % 5]
                    val xOffset = signedMod(longitude, 2 * edgeLength)
                    projectOntoSphere(
                            interpolateVertices(
                                    leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                                    xOffset.toFloat(),
                                    0.5f * (latitude - (3 * edgeLength)).toFloat()
                            )
                    )
                } else {
                    val tileIdx = unsignedMod(integerDiv(longitude + (6 * edgeLength), (2 * edgeLength)), 5)
                    val leftVertex = upperVertices[tileIdx % 5]
                    val rightVertex = upperVertices[(tileIdx + 1) % 5]
                    val otherVertex = lowerVertices[tileIdx % 5]
                    val xOffset = signedMod(longitude + edgeLength, 2 * edgeLength)
                    projectOntoSphere(
                            interpolateVertices(
                                    leftVertex.cpy(), rightVertex.cpy(), otherVertex.cpy(),
                                    xOffset.toFloat(),
                                    0.5f * ((6 * edgeLength) - latitude).toFloat()
                            )
                    )
                }
            }
        }
    }

    private fun hexCoords2SurfaceLoc(hexCoords: Vector2): SurfaceLocation {
        val x = hexCoords.x / edgeLength.toFloat()
        val y = hexCoords.y / edgeLength.toFloat()
        val z = x + y
        val xFaceIdx = floor((2f * x - y - sign(z - 4.5f) * 1e-6f) / 3f).roundToInt()
        val yFaceIdx = floor((2f * y - x - sign(z - 4.5f) * 1e-6f) / 3f).roundToInt()
        val faceLatitude = when {
            z < 3f -> Face.Latitude.POLAR_SOUTH
            z > 6f -> Face.Latitude.POLAR_NORTH
            (xFaceIdx + yFaceIdx) % 2 == 0 -> Face.Latitude.EQUATORIAL_SOUTH
            else -> Face.Latitude.EQUATORIAL_NORTH
        }
        val face = Face(faceLatitude, yFaceIdx + 2)
        return SurfaceLocation(face, Vector2(x - face.centerX(), y - face.centerY()))
    }

    private fun surfaceLoc2HexCoords(surfaceLoc: SurfaceLocation): Vector2 {
        val x = (surfaceLoc.coords.x + surfaceLoc.face.centerX()) * edgeLength.toFloat()
        val y = (surfaceLoc.coords.y + surfaceLoc.face.centerY()) * edgeLength.toFloat()
        return Vector2(x, y)
    }

    private fun addVector(hexCoords: Vector2, vector: Vector2): Vector2? {
        val surfaceLocIn = hexCoords2SurfaceLoc(hexCoords)
        val surfaceLocOut = surfaceLocIn.add(vector.cpy().scl(1f / edgeLength.toFloat()))
        val output = if (surfaceLocOut == null) null else {
            surfaceLoc2HexCoords(surfaceLocOut)
        }
        return output
    }

    fun neighbouringHexCoords(hexCoords: Vector2): List<Vector2> {
        return listOf(
                Vector2(1f, 0f),
                Vector2(1f, 1f),
                Vector2(0f, 1f),
                Vector2(-1f, 0f),
                Vector2(-1f, -1f),
                Vector2(0f, -1f)
        ).map{
            addVector(hexCoords, it)
        }.filterNotNull()
    }

    fun outerNeighbouringHexCoords(hexCoords: Vector2): List<Vector2> {
        return listOf(
                Vector2(2f, 1f),
                Vector2(1f, 2f),
                Vector2(-1f, 1f),
                Vector2(-2f, -1f),
                Vector2(-1f, -2f),
                Vector2(1f, -1f),
        ).map{
            addVector(hexCoords, it)
        }.filterNotNull()
    }
}