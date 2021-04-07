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
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.logic.HexMath3D
import com.unciv.logic.circumradius
import com.unciv.logic.map.TileInfo
import com.unciv.ui.map.TileGroupMapBase
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

    private fun getTextureCoords(region: TextureRegion): List<Vector2> {
        val left = region.getU()
        val right = region.getU2()
        val bottom = region.getV2()
        val top = region.getV2() - (right - left) * sqrt(3f) / 2f
        return listOf(
                Vector2(0.25f * left + 0.75f * right, top),
                Vector2(0.75f * left + 0.25f * right, top),
                Vector2(left, 0.5f * (bottom + top)),
                Vector2(0.75f * left + 0.25f * right, bottom),
                Vector2(0.25f * left + 0.75f * right, bottom),
                Vector2(right, 0.5f * (bottom + top)),
        )
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

        for(tileGroup in tileGroups) {
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
                )
            }
            val locations = tileGroup.getTileBaseImageLocations(null)
            for (location in locations) {
                val region = atlas.findRegion(location)
                val textureCoords = getTextureCoords(region)
                for (i in vertices.indices) {
                    val v1 = VertexInfo().setPos(position).setUV(
                            0.5f * (region.getU() + region.getU2()),
                            0.5f * (region.getV() + region.getV2())
                    )
                    val v2 = VertexInfo().setPos(vertices[i]).setUV(textureCoords[i])
                    val v3 = VertexInfo().setPos(
                            vertices[(i + 1) % vertices.size]
                    ).setUV(
                            textureCoords[(i + 1) % textureCoords.size]
                    )
                    meshBuilder.triangle(v1, v2, v3)
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