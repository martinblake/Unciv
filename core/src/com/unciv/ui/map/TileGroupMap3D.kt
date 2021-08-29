package com.unciv.ui.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.unciv.logic.HexMath3D
import com.unciv.logic.circumradius
import com.unciv.logic.map.TileInfo
import com.unciv.ui.tilegroups.TileGroup

import kotlin.math.*


val defaultCameraScale = 1f
val maxAbsCameraElevation = 80f * (PI.toFloat() / 180f)

val cameraFieldOfViewYDeg = 67f
val cameraHeightDistanceRatio = 2f * tan(cameraFieldOfViewYDeg * (PI.toFloat() / 180f) / 2f)

/**
 * Define key points for mapping a hexagonal tile.
 * 'pCenter' is the center of the hexagon.
 * 'pTop' and 'pBottom' define the centers of the top and bottom edges of the hexagon.
 * The vertices are named according to positions on a clock face.
 */
data class HexagonPoints<T>(val pCenter: T, val pTop: T, val pBottom: T, val p1: T, val p3: T, val p5: T, val p7: T, val p9: T, val p11: T)

abstract class TilePoints<T>(hexPoints: HexagonPoints<T>, val topStretchFactor: Float) {
    val pCenter = hexPoints.pCenter
    val pTop = hexPoints.pTop
    val pBottom = hexPoints.pBottom
    val p1 = hexPoints.p1
    val p3 = hexPoints.p3
    val p5 = hexPoints.p5
    val p7 = hexPoints.p7
    val p9 = hexPoints.p9
    val p11 = hexPoints.p11
    abstract val p1Outer: T
    abstract val p11Outer: T
    abstract val pTopOuter: T
    abstract val pTopLeftOuter: T
    abstract val pTopRightOuter: T
}

class WorldTilePoints(hexPoints: HexagonPoints<Vector3>, topStretchFactor: Float) : TilePoints<Vector3>(hexPoints, topStretchFactor) {
    override val p1Outer = p1.cpy().sub(pCenter).scl(topStretchFactor).add(pCenter)
    override val p11Outer = p11.cpy().sub(pCenter).scl(topStretchFactor).add(pCenter)
    override val pTopOuter = pTop.cpy().sub(pCenter).scl(topStretchFactor).add(pCenter)
    override val pTopLeftOuter = p9.cpy().sub(pCenter).scl(1f - (0.5f * topStretchFactor)).add(p11Outer)
    override val pTopRightOuter = p3.cpy().sub(pCenter).scl(1f - (0.5f * topStretchFactor)).add(p1Outer)
}

class TextureTilePoints(hexPoints: HexagonPoints<Vector2>, topStretchFactor: Float) : TilePoints<Vector2>(hexPoints, topStretchFactor) {
    override val p1Outer = p1.cpy().sub(pCenter).scl(topStretchFactor).add(pCenter)
    override val p11Outer = p11.cpy().sub(pCenter).scl(topStretchFactor).add(pCenter)
    override val pTopOuter = pTop.cpy().sub(pCenter).scl(topStretchFactor).add(pCenter)
    override val pTopLeftOuter = p9.cpy().sub(pCenter).scl(1f - (0.5f * topStretchFactor)).add(p11Outer)
    override val pTopRightOuter = p3.cpy().sub(pCenter).scl(1f - (0.5f * topStretchFactor)).add(p1Outer)
}

class TileVertexInfo(world: WorldTilePoints, texture: TextureTilePoints) {
    val pCenter = vertex(world.pCenter, texture.pCenter)
    val pTop = vertex(world.pTop, texture.pTop)
    val pBottom = vertex(world.pBottom, texture.pBottom)
    val p1 = vertex(world.p1, texture.p1)
    val p3 = vertex(world.p3, texture.p3)
    val p5 = vertex(world.p5, texture.p5)
    val p7 = vertex(world.p7, texture.p7)
    val p9 = vertex(world.p9, texture.p9)
    val p11 = vertex(world.p11, texture.p11)
    val p1Outer = vertex(world.p1Outer, texture.p1Outer)
    val p11Outer = vertex(world.p11Outer, texture.p11Outer)
    val pTopOuter = vertex(world.pTopOuter, texture.pTopOuter)
    val pTopLeftOuter = vertex(world.pTopLeftOuter, texture.pTopLeftOuter)
    val pTopRightOuter = vertex(world.pTopRightOuter, texture.pTopRightOuter)

    private fun vertex(worldPoint: Vector3, texturePoint: Vector2): VertexInfo {
        return VertexInfo().setPos(worldPoint).setUV(texturePoint)
    }
}

class HexagonalTileMesh(private val hexMap: HexMath3D, private val hexCoords: Vector2) {

    private val worldHexagonPoints = calcWorldHexagonPoints()

    private fun calcWorldHexagonPoints(): HexagonPoints<Vector3> {
        // Central hexagon center
        val pCenter = hexMap.hex2WorldCoords(hexCoords)

        // Adjacent hexagon centers (only 12 and 6 o'clock positions can be null)
        val p0 =  worldCoordsOffset(1f, 1f)
        val p2 =  worldCoordsOffset(0f, 1f)!!
        val p4 =  worldCoordsOffset(-1f, 0f)!!
        val p6 =  worldCoordsOffset(-1f, -1f)
        val p8 =  worldCoordsOffset(0f, -1f)!!
        val p10 =  worldCoordsOffset(1f, 0f)!!

        // Interpolate to get vertices
        return when {
            (p0 == null) -> {
                // Top 3 vertices merge to 2
                val pTop = sphericalAverage(pCenter, p2, p10)
                val p3 = sphericalAverage(pCenter, p2, p4)
                val p5 = sphericalAverage(pCenter, p4, p6!!)
                val p7 = sphericalAverage(pCenter, p6!!, p8)
                val p9 = sphericalAverage(pCenter, p8, p10)
                HexagonPoints(
                        pCenter,
                        pTop,
                        linearAverage(p5, p7),
                        p3.cpy().lerp(pTop, 1f / 2f),
                        p3, p5, p7, p9,
                        p9.cpy().lerp(pTop, 1f / 2f)
                )
            }
            (p6 == null) -> {
                // Bottom 3 vertices merge to 2
                val pBottom = sphericalAverage(pCenter, p4, p8)
                val p1 = sphericalAverage(pCenter, p0, p2)
                val p3 = sphericalAverage(pCenter, p2, p4)
                val p9 = sphericalAverage(pCenter, p8, p10)
                val p11 = sphericalAverage(pCenter, p10, p0)
                HexagonPoints(
                        pCenter,
                        linearAverage(p1, p11),
                        pBottom,
                        p1, p3,
                        p3.cpy().lerp(pBottom, 1f / 2f),
                        p9.cpy().lerp(pBottom, 1f / 2f),
                        p9, p11
                )
            }
            else -> {
                val p1 = sphericalAverage(pCenter, p0, p2)
                val p3 = sphericalAverage(pCenter, p2, p4)
                val p5 = sphericalAverage(pCenter, p4, p6)
                val p7 = sphericalAverage(pCenter, p6, p8)
                val p9 = sphericalAverage(pCenter, p8, p10)
                val p11 = sphericalAverage(pCenter, p10, p0)
                HexagonPoints(
                        pCenter,
                        linearAverage(p1, p11),
                        linearAverage(p5, p7),
                        p1, p3, p5, p7, p9, p11
                )
            }
        }
    }

    private fun worldCoordsOffset(dx: Float, dy: Float): Vector3? {
        val offsetHexCoords = hexMap.hexCoordsOffset(hexCoords, dx, dy)
        return if (offsetHexCoords == null) null else hexMap.hex2WorldCoords(offsetHexCoords)
    }

    private fun linearAverage(v1: Vector2, v2: Vector2): Vector2 {
        return v1.cpy().add(v2).scl(0.5f)
    }

    private fun linearAverage(v1: Vector3, v2: Vector3): Vector3 {
        return v1.cpy().add(v2).scl(0.5f)
    }

    private fun sphericalAverage(v1: Vector3, v2: Vector3, v3: Vector3): Vector3 {
        return v1.cpy().add(v2).add(v3).scl(1f / 3f).nor().scl(v1.len())
    }

    private fun calcTextureHexagonPoints(region: TextureRegion): HexagonPoints<Vector2> {
        val radius = 0.5f * (region.getU2() - region.getU())
        val pCenter = Vector2(
                region.getU() + radius,
                region.getV2() - radius * sqrt(3f) / 2f
        )
        val p1 = Vector2(radius, 0f).rotateDeg(-60f).add(pCenter)
        val p3 = Vector2(radius, 0f).rotateDeg(0f).add(pCenter)
        val p5 = Vector2(radius, 0f).rotateDeg(60f).add(pCenter)
        val p7 = Vector2(radius, 0f).rotateDeg(120f).add(pCenter)
        val p9 = Vector2(radius, 0f).rotateDeg(180f).add(pCenter)
        val p11 = Vector2(radius, 0f).rotateDeg(-120f).add(pCenter)
        return HexagonPoints(
                pCenter,
                linearAverage(p1, p11),
                linearAverage(p5, p7),
                p1, p3, p5, p7, p9, p11
        )
    }

    fun addTexture(meshBuilder: MeshPartBuilder, region: TextureRegion) {

        val textureHexagonPoints = calcTextureHexagonPoints(region)

        val topHeight = (textureHexagonPoints.pCenter.y - region.getV())
        val bottomHeight = (region.getV2() - textureHexagonPoints.pCenter.y)
        val topStretchFactor = min(2f, max(1f, topHeight / bottomHeight))

        val worldTilePoints = WorldTilePoints(worldHexagonPoints, topStretchFactor)
        val textureTilePoints = TextureTilePoints(textureHexagonPoints, topStretchFactor)

        val vi = TileVertexInfo(worldTilePoints, textureTilePoints)

        meshBuilder.triangle(vi.pCenter, vi.p1, vi.pTop)
        meshBuilder.triangle(vi.pCenter, vi.p3, vi.p1)
        meshBuilder.triangle(vi.pCenter, vi.p5, vi.p3)
        meshBuilder.triangle(vi.pCenter, vi.pBottom, vi.p5)
        meshBuilder.triangle(vi.pCenter, vi.p7, vi.pBottom)
        meshBuilder.triangle(vi.pCenter, vi.p9, vi.p7)
        meshBuilder.triangle(vi.pCenter, vi.p11, vi.p9)
        meshBuilder.triangle(vi.pCenter, vi.pTop, vi.p11)

        meshBuilder.rect(vi.p9, vi.p11, vi.p11Outer, vi.pTopLeftOuter)
        meshBuilder.rect(vi.p3, vi.pTopRightOuter, vi.p1Outer, vi.p1)
        meshBuilder.rect(vi.p1, vi.p1Outer, vi.pTopOuter, vi.pTop)
        meshBuilder.rect(vi.pTop, vi.pTopOuter, vi.p11Outer, vi.p11)
    }
}


class TileGroupMap3D<T : TileGroup>(private val tileGroups: Collection<T>, private val leftAndRightPadding: Float, private val topAndBottomPadding: Float, worldWrap: Boolean = false, tileGroupsToUnwrap: Collection<T>? = null): TileGroupMapBase<T>(){

    var camera: PerspectiveCamera
    var modelBatch: ModelBatch
    var sphere: ModelInstance

    var cameraLatitude = 0f
    var cameraLongitude = 0f
    var cameraScale = defaultCameraScale

    val atlas = TextureAtlas("game.atlas")

    val texture = Texture("game.png")

    private val mirrorTileGroups = HashMap<TileInfo, Pair<T, T>>()

    override fun getMirrorTiles(): HashMap<TileInfo, Pair<T, T>> = mirrorTileGroups

    override fun draw(batch: Batch?, parentAlpha: Float) {
        batch?.end()

        modelBatch.begin(camera)
        modelBatch.render(sphere)
        modelBatch.end()

        batch?.begin()
        super.draw(batch, parentAlpha)
    }

    init {
        camera = createCamera()
        setCameraPosition(cameraLatitude, cameraLongitude, cameraScale)
        sphere = createModelInstance()

        for (material in sphere.materials) {
            material.set(
                    TextureAttribute(TextureAttribute.Diffuse, texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    FloatAttribute(FloatAttribute.AlphaTest, 0.1f),
            )
        }

        modelBatch = ModelBatch()
    }

    fun createModelInstance(): ModelInstance {
        val modelBuilder = ModelBuilder()

        modelBuilder.begin()
        for(tileGroup in tileGroups.reversed()) {
            val meshBuilder = modelBuilder.part(
                    "tile_${tileGroup.tileInfo.position.x}_${tileGroup.tileInfo.position.y}",
                    GL20.GL_TRIANGLES,
                    (Usage.Position or Usage.TextureCoordinates).toLong(),
                    Material()
            )
            val hexMap = tileGroup.tileInfo.tileMap.hexMath3d
            val tileMesh = HexagonalTileMesh(hexMap, tileGroup.tileInfo.position)
            val locations = tileGroup.getTileBaseImageLocations(null)
            for (location in locations) {
                val region = atlas.findRegion(location)
                tileMesh.addTexture(meshBuilder, region)
            }
        }
        val model = modelBuilder.end()
        return ModelInstance(model)
    }

    fun createCamera(): PerspectiveCamera {
        val cam = PerspectiveCamera(
                cameraFieldOfViewYDeg,
                Gdx.graphics.width.toFloat(),
                Gdx.graphics.height.toFloat()
        )
        cam.near = 1f
        cam.far = 300f
        return cam
    }

    fun setCameraPosition(latitude: Float, longitude: Float, scale: Float) {
        camera.position.set(
                Vector3(
                        cos(latitude) * cos(longitude),
                        cos(latitude) * sin(longitude),
                        sin(latitude)
                ).scl((2f * scale / cameraHeightDistanceRatio + 1f) * circumradius)
        )
        camera.up.set(0f, 0f, 1f)
        camera.lookAt(0f, 0f, 0f)
        camera.update()
    }

    /**
     * Return angle of rotation of the world sphere that moves the surface by a single pixel
     */
    private fun pixelAngleOnSphere(): Float {
        return 2f * cameraScale / Gdx.graphics.height.toFloat()
    }

    override fun scrollX(pixels: Float) {
        cameraLongitude += (pixels * pixelAngleOnSphere() / cos(cameraLatitude))
        setCameraPosition(cameraLatitude, cameraLongitude, cameraScale)
    }

    override fun scrollY(pixels: Float) {
        cameraLatitude = max(
                -maxAbsCameraElevation,
                min(
                        maxAbsCameraElevation,
                        cameraLatitude - (pixels * pixelAngleOnSphere())
                )
        )
        setCameraPosition(cameraLatitude, cameraLongitude, cameraScale)
    }

    override fun setZoom(zoomScale: Float) {
        cameraScale = zoomScale * defaultCameraScale
        setCameraPosition(cameraLatitude, cameraLongitude, cameraScale)
    }

}