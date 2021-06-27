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
        )

        modelBatch = ModelBatch()
    }

    private fun addTile(meshBuilder: MeshPartBuilder, centralPosition: Vector3, vertexPositions: List<Vector3>, region: TextureRegion) {
        val textureRadius = 0.5f * (region.getU2() - region.getU())
        val textureCenter = Vector2(
                region.getU() + textureRadius,
                region.getV2() - textureRadius * sqrt(3f) / 2f
        )
        val topStretchFactor = min((textureCenter.y - region.getV()) / (region.getV2() - textureCenter.y), 2f)
        val centralVertex = VertexInfo().setPos(centralPosition).setUV(textureCenter)
        // Find the index of the first vertex circling anticlockwise from south
        val firstIdx = vertexPositions.indices.maxByOrNull {
            Vector3(0f, 0f, 1f).crs(
                    vertexPositions[(it + vertexPositions.size - 1) % vertexPositions.size].cpy().sub(vertexPositions[it])
            ).dot(centralPosition)
        } ?: 0
        // Define key texture coordinates
        val tSE = Vector2(textureRadius, 0f).rotateDeg(60f).add(textureCenter)
        val tE = Vector2(textureRadius, 0f).rotateDeg(0f).add(textureCenter)
        val tNE = Vector2(textureRadius, 0f).rotateDeg(-60f).add(textureCenter)
        val tNW = Vector2(textureRadius, 0f).rotateDeg(-120f).add(textureCenter)
        val tW = Vector2(textureRadius, 0f).rotateDeg(180f).add(textureCenter)
        val tSW = Vector2(textureRadius, 0f).rotateDeg(120f).add(textureCenter)
        val tNE_outer = Vector2(textureRadius * topStretchFactor, 0f).rotateDeg(-60f).add(textureCenter)
        val tNW_outer = Vector2(textureRadius * topStretchFactor, 0f).rotateDeg(-120f).add(textureCenter)
        when {
            (vertexPositions.size == 6) -> {
                val vSE = VertexInfo().setPos(
                        vertexPositions[firstIdx]
                ).setUV(tSE)
                val vE = VertexInfo().setPos(
                        vertexPositions[(firstIdx + 1) % vertexPositions.size]
                ).setUV(tE)
                val vNE = VertexInfo().setPos(
                        vertexPositions[(firstIdx + 2) % vertexPositions.size]
                ).setUV(tNE)
                val vNE_outer = VertexInfo().setPos(
                        centralPosition.cpy().add(vertexPositions[(firstIdx + 2) % vertexPositions.size].cpy().sub(centralPosition).scl(topStretchFactor))
                ).setUV(tNE_outer)
                val vNW = VertexInfo().setPos(
                        vertexPositions[(firstIdx + 3) % vertexPositions.size]
                ).setUV(tNW)
                val vNW_outer = VertexInfo().setPos(
                        centralPosition.cpy().add(vertexPositions[(firstIdx + 3) % vertexPositions.size].cpy().sub(centralPosition).scl(topStretchFactor))
                ).setUV(tNW_outer)
                val vW = VertexInfo().setPos(
                        vertexPositions[(firstIdx + 4) % vertexPositions.size]
                ).setUV(tW)
                val vSW = VertexInfo().setPos(
                        vertexPositions[(firstIdx + 5) % vertexPositions.size]
                ).setUV(tSW)
                meshBuilder.triangle(centralVertex, vSE, vE)
                meshBuilder.triangle(centralVertex, vE, vNE)
                meshBuilder.triangle(centralVertex, vNE, vNW)
                meshBuilder.triangle(centralVertex, vNW, vW)
                meshBuilder.triangle(centralVertex, vW, vSW)
                meshBuilder.triangle(centralVertex, vSW, vSE)
                //meshBuilder.triangle(vE, vNE_outer, vNE)
                meshBuilder.rect(vNE, vNE_outer, vNW_outer, vNW)
                //meshBuilder.triangle(vNW, vNW_outer, vW)
            }
            (vertexPositions.size == 5) -> {}
            else -> {}
        }
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
            val position = HexMath3D.hex2WorldCoords(
                    tileGroup.tileInfo.tileMap.icosahedronEdgeLength,
                    tileGroup.tileInfo.position
            )
            val neighbourPositions = HexMath3D.neighbouringHexCoords(
                    tileGroup.tileInfo.tileMap.icosahedronEdgeLength,
                    tileGroup.tileInfo.position
            ).map{
                HexMath3D.hex2WorldCoords(
                        tileGroup.tileInfo.tileMap.icosahedronEdgeLength,
                        it
                )
            }
            val vertices = neighbourPositions.indices.map{
                position.cpy().add(
                        neighbourPositions[it]
                ).add(
                        neighbourPositions[(it + 1) % neighbourPositions.size]
                ).scl(
                        1f / 3f
                ).nor().scl(position.len())
            }
            val locations = tileGroup.getTileBaseImageLocations(null)
            for (location in locations) {
                val region = atlas.findRegion(location)
                addTile(meshBuilder, position, vertices, region)
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