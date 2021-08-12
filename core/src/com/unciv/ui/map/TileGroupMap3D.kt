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


class HexagonalTileMesh(private val hexMap: HexMath3D, private val hexCoords: Vector2) {

    // Central hexagon center
    private val p_center = hexMap.hex2WorldCoords(hexCoords)

    // Adjacent hexagon centers
    private val p_0_outer =  worldCoordsOffset(1f, 1f)
    private val p_2_outer =  worldCoordsOffset(0f, 1f)
    private val p_4_outer =  worldCoordsOffset(-1f, 0f)
    private val p_6_outer =  worldCoordsOffset(-1f, -1f)
    private val p_8_outer =  worldCoordsOffset(0f, -1f)
    private val p_10_outer =  worldCoordsOffset(1f, 0f)

    // Interpolated vertices
    private val p_1_outer = sphericalAverage(worldCoordsOffset(1f, 2f), p_0_outer, p_2_outer)
    private val p_1_inner = sphericalAverage(p_center, p_0_outer, p_2_outer)
    private val p_3_inner = sphericalAverage(p_center, p_2_outer, p_4_outer)
    private val p_5_inner = sphericalAverage(p_center, p_4_outer, p_6_outer)
    private val p_7_inner = sphericalAverage(p_center, p_6_outer, p_8_outer)
    private val p_9_inner = sphericalAverage(p_center, p_8_outer, p_10_outer)
    private val p_11_inner = sphericalAverage(p_center, p_10_outer, p_0_outer)
    private val p_11_outer = sphericalAverage(worldCoordsOffset(2f, 1f), p_10_outer, p_0_outer)

    private fun worldCoordsOffset(dx: Float, dy: Float): Vector3 {
        val offsetHexCoords = hexMap.hexCoordsOffset(hexCoords, dx, dy)!!
        return hexMap.hex2WorldCoords(offsetHexCoords)
    }

    private fun sphericalAverage(v1: Vector3, v2: Vector3, v3: Vector3): Vector3 {
        return v1.cpy().add(v2).add(v3).scl(1f / 3f).nor().scl(v1.len())
    }

    /**
     * Elevate a surface vector by a given amount, specified as a fraction of the globe radius.
     */
    private fun elevate(vec: Vector3, fraction: Float): Vector3 {
        return vec.cpy().scl(1f + fraction)
    }

    fun addTexture(meshBuilder: MeshPartBuilder, region: TextureRegion) {
        val textureRadius = 0.5f * (region.getU2() - region.getU())
        val t_center = Vector2(
                region.getU() + textureRadius,
                region.getV2() - textureRadius * sqrt(3f) / 2f
        )
        val overflowRatio = min(1f, max(0f, ((t_center.y - region.getV()) / (region.getV2() - t_center.y)) - 1f))

        val t_1_inner = Vector2(textureRadius, 0f).rotateDeg(-60f).add(t_center)
        val t_3_inner = Vector2(textureRadius, 0f).rotateDeg(0f).add(t_center)
        val t_5_inner = Vector2(textureRadius, 0f).rotateDeg(60f).add(t_center)
        val t_7_inner = Vector2(textureRadius, 0f).rotateDeg(120f).add(t_center)
        val t_9_inner = Vector2(textureRadius, 0f).rotateDeg(180f).add(t_center)
        val t_11_inner = Vector2(textureRadius, 0f).rotateDeg(-120f).add(t_center)

        val t_9_0 = Vector2(region.getU(), region.getV2() - textureRadius * sqrt(3f))
        val t_3_0 = Vector2(region.getU2(), region.getV2() - textureRadius * sqrt(3f))
        val t_9_0_0 = Vector2(region.getU(), region.getV())
        val t_3_0_0 = Vector2(region.getU2(), region.getV())
        val t_11_11 = t_11_inner.cpy().add(t_11_inner.cpy().sub(t_center).scl(overflowRatio))
        val t_1_1 = t_1_inner.cpy().add(t_1_inner.cpy().sub(t_center).scl(overflowRatio))
        val t_11_1 = t_11_inner.cpy().add(t_1_inner.cpy().sub(t_center).scl(overflowRatio))
        val t_1_11 = t_1_inner.cpy().add(t_11_inner.cpy().sub(t_center).scl(overflowRatio))

        val p_9_0 = p_11_inner.cpy().lerp(p_10_outer, 0.5f)
        val p_3_0 = p_1_inner.cpy().lerp(p_2_outer, 0.5f)
        val p_9_0_0 = p_9_0.cpy().lerp(p_11_outer, overflowRatio)
        val p_3_0_0 = p_3_0.cpy().lerp(p_1_outer, overflowRatio)
        val p_11_11 = p_11_inner.cpy().lerp(p_11_outer, overflowRatio)
        val p_1_1 = p_1_inner.cpy().lerp(p_1_outer, overflowRatio)
        val p_11_1 = p_11_inner.cpy().lerp(p_0_outer, overflowRatio)
        val p_1_11 = p_1_inner.cpy().lerp(p_0_outer, overflowRatio)

        val v_center = VertexInfo().setPos(p_center).setUV(t_center)
        val v_1_inner = VertexInfo().setPos(p_1_inner).setUV(t_1_inner)
        val v_3_inner = VertexInfo().setPos(p_3_inner).setUV(t_3_inner)
        val v_5_inner = VertexInfo().setPos(p_5_inner).setUV(t_5_inner)
        val v_7_inner = VertexInfo().setPos(p_7_inner).setUV(t_7_inner)
        val v_9_inner = VertexInfo().setPos(p_9_inner).setUV(t_9_inner)
        val v_11_inner = VertexInfo().setPos(p_11_inner).setUV(t_11_inner)

        val v_9_0 = VertexInfo().setPos(elevate(p_9_0, 1e-4f)).setUV(t_9_0)
        val v_3_0 = VertexInfo().setPos(elevate(p_3_0, 1e-4f)).setUV(t_3_0)
        val v_9_0_0 = VertexInfo().setPos(elevate(p_9_0_0, 1e-4f)).setUV(t_9_0_0)
        val v_3_0_0 = VertexInfo().setPos(elevate(p_3_0_0, 1e-4f)).setUV(t_3_0_0)
        val v_11_11 = VertexInfo().setPos(elevate(p_11_11, 1e-4f)).setUV(t_11_11)
        val v_1_1 = VertexInfo().setPos(elevate(p_1_1, 1e-4f)).setUV(t_1_1)
        val v_11_1 = VertexInfo().setPos(elevate(p_11_1, 1e-4f)).setUV(t_11_1)
        val v_1_11 = VertexInfo().setPos(elevate(p_1_11, 1e-4f)).setUV(t_1_11)

        meshBuilder.triangle(v_center, v_3_inner, v_1_inner)
        meshBuilder.triangle(v_center, v_5_inner, v_3_inner)
        meshBuilder.triangle(v_center, v_7_inner, v_5_inner)
        meshBuilder.triangle(v_center, v_9_inner, v_7_inner)
        meshBuilder.triangle(v_center, v_11_inner, v_9_inner)
        meshBuilder.triangle(v_center, v_1_inner, v_11_inner)

        meshBuilder.triangle(v_9_inner, v_11_inner, v_9_0)
        meshBuilder.triangle(v_3_inner, v_3_0, v_1_inner)
        meshBuilder.triangle(v_11_inner, v_9_0_0, v_9_0)
        meshBuilder.triangle(v_1_inner, v_3_0, v_3_0_0)
        meshBuilder.triangle(v_11_inner, v_11_11, v_9_0_0)
        meshBuilder.triangle(v_1_inner, v_3_0_0, v_1_1)
        meshBuilder.triangle(v_11_inner, v_11_1, v_11_11)
        meshBuilder.triangle(v_1_inner, v_1_1, v_1_11)
        meshBuilder.rect(v_1_inner, v_1_11, v_11_1, v_11_inner)
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

        val material = sphere.materials.get(0)
        material.set(
                TextureAttribute(TextureAttribute.Diffuse, texture),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                FloatAttribute(FloatAttribute.AlphaTest, 0.1f),
        )

        modelBatch = ModelBatch()
    }

    fun createModelInstance(): ModelInstance {
        val modelBuilder = ModelBuilder()

        modelBuilder.begin()
        var meshBuilder = modelBuilder.part(
                "tiledSphere",
                GL20.GL_TRIANGLES,
                (Usage.Position or Usage.TextureCoordinates).toLong(),
                Material()
        )
        for(tileGroup in tileGroups.reversed()) {
            val hexMap = tileGroup.tileInfo.tileMap.hexMap
            if (hexMap.isVertex(tileGroup.tileInfo.position)) {
                // TODO: Implement
            } else {
                val tileMesh = HexagonalTileMesh(hexMap, tileGroup.tileInfo.position)
                val locations = tileGroup.getTileBaseImageLocations(null)
                for (location in locations) {
                    val region = atlas.findRegion(location)
                    tileMesh.addTexture(meshBuilder, region)
                }
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