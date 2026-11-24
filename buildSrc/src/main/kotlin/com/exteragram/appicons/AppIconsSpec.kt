package com.exteragram.appicons

import com.google.gson.Gson
import org.gradle.api.GradleException
import java.io.File

internal class IconsFile {
    var defaultMonochrome: String? = null
    var icons: List<IconJson> = emptyList()
}

internal class IconJson {
    var id: String? = null
    var author: String? = null
    var authorName: String? = null
    var title: String? = null
    var titleRes: String? = null
    var description: String? = null
    var descriptionRes: String? = null
    var color: String? = null
    var component: String? = null
    var resource: String? = null
    var background: String? = null
    var foreground: String? = null
    var monochrome: String? = null
    var availability: String? = null
    var round: Boolean? = null
    var legacy: Boolean? = null
    var lossless: Boolean? = null
    var assetsOnly: Boolean? = null
}

internal sealed class Layer {
    data class Ref(val reference: String) : Layer()
    data class Color(val hex: String) : Layer()
    data class Asset(val file: File) : Layer()
    object None : Layer()
}

internal class Icon(
    val id: String,
    val dir: File,
    val owner: String,
    val resource: String,
    val component: String,
    val title: String?,
    val titleRes: String?,
    val description: String?,
    val descriptionRes: String?,
    val color: String?,
    val authorName: String?,
    val authorUsername: String?,
    val availability: String,
    val round: Boolean,
    val legacy: Boolean,
    val lossless: Boolean,
    val assetsOnly: Boolean,
    val background: Layer,
    val foreground: Layer,
    val monochrome: Layer
) {
    val colorResource: String get() = "ic_background_$id"
    val backgroundResource: String get() = "${resource}_background"
    val foregroundResource: String get() = "${resource}_foreground"
    val monochromeResource: String get() = "ic_monochrome_$id"

    fun reference(layer: Layer, kind: String): String? = when (layer) {
        is Layer.Ref -> layer.reference
        is Layer.Color -> "@color/$colorResource"
        is Layer.Asset -> when (kind) {
            "background" -> "@mipmap/$backgroundResource"
            "foreground" -> "@mipmap/$foregroundResource"
            else -> if (layer.file.extension.equals("svg", true)) "@drawable/$monochromeResource" else "@mipmap/$monochromeResource"
        }
        Layer.None -> null
    }
}

internal object AppIconsSpec {

    private val ID_NAME = Regex("[a-z0-9][a-z0-9_]*")
    private val RESOURCE_NAME = Regex("[a-z][a-z0-9_]*")
    private val COMPONENT_NAME = Regex("[A-Za-z][A-Za-z0-9_]*")
    private val COLOR = Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")
    private val LAYER_FILES = listOf("svg", "png", "webp")
    private val AVAILABILITY = listOf("always", "winter", "monet")

    fun parse(sourceDir: File): List<Icon> {
        val file = File(sourceDir, "icons.json")
        if (!file.isFile) {
            throw GradleException("${file.absolutePath} not found")
        }
        val spec = Gson().fromJson(file.readText(), IconsFile::class.java)
            ?: throw GradleException("${file.name} is empty")
        val defaultMonochrome = spec.defaultMonochrome

        val icons = spec.icons.map { resolve(sourceDir, it, defaultMonochrome) }

        icons.groupBy { it.id }.forEach { (id, list) ->
            if (list.size > 1) throw GradleException("duplicate icon id \"$id\"")
        }
        icons.groupBy { it.component }.forEach { (component, list) ->
            if (list.size > 1) throw GradleException("duplicate component name \"$component\"")
        }
        icons.groupBy { it.resource }.forEach { (resource, list) ->
            if (list.size > 1) throw GradleException("duplicate resource name \"$resource\"")
        }
        return icons
    }

    private fun resolve(sourceDir: File, json: IconJson, defaultMonochrome: String?): Icon {
        val id = json.id?.trim().orEmpty()
        if (!ID_NAME.matches(id)) {
            throw GradleException("icon id \"$id\" must match ${ID_NAME.pattern}")
        }

        val owner = (json.author?.trim().takeUnless { it.isNullOrEmpty() } ?: "exteragram").lowercase()
        val dir = File(File(sourceDir, owner), id)

        val resource = json.resource?.trim().takeUnless { it.isNullOrEmpty() } ?: "ic_launcher_$id"
        if (!RESOURCE_NAME.matches(resource)) {
            throw GradleException("$id: resource name \"$resource\" must match ${RESOURCE_NAME.pattern}")
        }

        val component = json.component?.trim().takeUnless { it.isNullOrEmpty() } ?: (pascalCase(id) + "Icon")
        if (!COMPONENT_NAME.matches(component)) {
            throw GradleException("$id: component name \"$component\" must match ${COMPONENT_NAME.pattern}, set \"component\" explicitly")
        }

        val assetsOnly = json.assetsOnly ?: false
        if (!assetsOnly && json.title == null && json.titleRes == null) {
            throw GradleException("$id: either \"title\" or \"titleRes\" is required")
        }

        val color = json.color?.trim()?.takeUnless { it.isEmpty() }
        if (color != null && !COLOR.matches(color)) {
            throw GradleException("$id: \"$color\" is not a valid color")
        }

        val availability = json.availability?.trim().takeUnless { it.isNullOrEmpty() } ?: "always"
        if (availability !in AVAILABILITY) {
            throw GradleException("$id: \"$availability\" is not one of ${AVAILABILITY.joinToString()}")
        }

        val background = layer(id, dir, json.background, "background", null)
        val foreground = layer(id, dir, json.foreground, "foreground", null)
        val monochrome = layer(id, dir, json.monochrome, "monochrome", defaultMonochrome)

        if (background is Layer.None) throw GradleException("$id: background is missing")
        if (foreground is Layer.None) throw GradleException("$id: foreground is missing")

        return Icon(
            id = id,
            dir = dir,
            owner = owner,
            resource = resource,
            component = component,
            title = json.title,
            titleRes = json.titleRes,
            description = json.description,
            descriptionRes = json.descriptionRes,
            color = color,
            authorName = json.authorName,
            authorUsername = json.author?.trim()?.removePrefix("@").takeUnless { it.isNullOrEmpty() },
            availability = availability,
            round = json.round ?: false,
            legacy = json.legacy ?: true,
            lossless = json.lossless ?: false,
            assetsOnly = assetsOnly,
            background = background,
            foreground = foreground,
            monochrome = monochrome
        )
    }

    private fun layer(id: String, dir: File, value: String?, kind: String, fallback: String?): Layer {
        val declared = value?.trim()
        if (declared == "none") return Layer.None
        if (declared != null && declared.startsWith("@")) return Layer.Ref(declared)
        if (declared != null && declared.startsWith("#")) {
            if (!COLOR.matches(declared)) throw GradleException("$id: \"$declared\" is not a valid color")
            return Layer.Color(declared)
        }
        if (declared != null && declared.isNotEmpty()) {
            val file = File(dir, declared)
            if (!file.isFile) throw GradleException("$id: ${file.absolutePath} not found")
            return Layer.Asset(file)
        }

        val found = LAYER_FILES.map { File(dir, "ic_launcher_$kind.$it") }.firstOrNull { it.isFile }
            ?: LAYER_FILES.map { File(dir, "$kind.$it") }.firstOrNull { it.isFile }
        if (found != null) return Layer.Asset(found)

        if (fallback != null) return Layer.Ref(fallback)
        return Layer.None
    }

    private fun pascalCase(id: String): String = id.split('_')
        .filter { it.isNotEmpty() }
        .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
}
