package com.unciv.ui.mapeditor

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.MapSaver
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.pad
import com.unciv.ui.utils.toLabel

class MapEditorFilesTable(
    initWidth: Float,
    private val includeMods: Boolean = false,
    private val onSelect: (FileHandle) -> Unit
): Table(BaseScreen.skin) {
    private var selectedIndex = -1

    private data class ListEntry(val mod: String, val file: FileHandle)
    private var sortedFiles = ArrayList<ListEntry>()

    init {
        defaults().pad(5f).maxWidth(initWidth)
    }

    private fun markSelection(button: TextButton, row: Int) {
        for (cell in cells) {
            if (cell.actor != button && cell.actor is TextButton)
                cell.actor.color = Color.WHITE
        }
        button.color = Color.BLUE
        selectedIndex = row
        onSelect(sortedFiles[row].file)
    }

    fun moveSelection(delta: Int) {
        selectedIndex = when {
            selectedIndex + delta in sortedFiles.indices ->
                selectedIndex + delta
            selectedIndex + delta < 0 ->
                sortedFiles.size - 1
            else -> 0
        }
        val button = cells[selectedIndex].actor as TextButton
        (parent as? ScrollPane)?.let {
            it.scrollY = (height - button.y) - (it.height - button.height) / 2
        }
        markSelection(button, selectedIndex)
    }

    fun update() {
        clear()
        sortedFiles.clear()
        sortedFiles.addAll(
            MapSaver.getMaps()
                .sortedByDescending { it.lastModified() }
                .map { ListEntry("", it) }
        )
        if (includeMods) {
            for (modFolder in RulesetCache.values.mapNotNull { it.folderLocation }) {
                val mapsFolder = modFolder.child(MapSaver.mapsFolder)
                if (mapsFolder.exists())
                    sortedFiles.addAll(
                        mapsFolder.list()
                            .sortedBy { it.name() }
                            .map { ListEntry(modFolder.name(), it) }
                    )
            }
        }

        var lastMod = ""
        for ((index, entry) in sortedFiles.withIndex()) {
            val (mod, mapFile) = entry 
            if (mod != lastMod) {
                // One header per Mod
                add(Table().apply {
                    add(ImageGetter.getDot(Color.LIGHT_GRAY)).minHeight(2f).minWidth(15f)
                    add(mod.toLabel(Color.LIGHT_GRAY)).left().pad(0f,2f)
                    add(ImageGetter.getDot(Color.LIGHT_GRAY)).minHeight(2f).growX().row()
                }).growX().row()
                lastMod = mod
            }
            val mapButton = TextButton(mapFile.name(), BaseScreen.skin)
            mapButton.onClick {
                markSelection(mapButton, index)
            }
            add(mapButton).row()
        }
        layout()
    }

    fun noMapsAvailable(): Boolean {
        if (MapSaver.getMaps().any()) return false
        if (!includeMods) return true
        for (modFolder in RulesetCache.values.mapNotNull { it.folderLocation }) {
            val mapsFolder = modFolder.child(MapSaver.mapsFolder)
            if (mapsFolder.exists() && mapsFolder.list().any()) return false
        }
        return true
    }
}
