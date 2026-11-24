package com.exteragram.appicons

import com.android.ide.common.vectordrawable.Svg2Vector
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.File

abstract class GenerateAppIconsTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val colorFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val resDir: DirectoryProperty

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @get:OutputFile
    abstract val registryFile: RegularFileProperty

    @get:Input
    abstract val componentPackage: Property<String>

    @get:Input
    abstract val registryPackage: Property<String>

    @get:Input
    abstract val resourcePackage: Property<String>

    @get:Input
    @get:Optional
    abstract val toolOverrides: MapProperty<String, String>

    @TaskAction
    fun generate() {
        val source = sourceDir.get().asFile
        val res = resDir.get().asFile
        val icons = AppIconsSpec.parse(source)
        val colors = parseColors()
        val assets = HashMap<String, File>()
        for (icon in icons) {
            (icon.background as? Layer.Asset)?.let { assets["@mipmap/${icon.backgroundResource}"] = it.file }
            (icon.foreground as? Layer.Asset)?.let { assets["@mipmap/${icon.foregroundResource}"] = it.file }
        }

        res.deleteRecursively()
        res.mkdirs()

        val renderer = AppIconsRenderer(File(temporaryDir, "render"), toolOverrides.getOrElse(emptyMap()))
        val generatedColors = LinkedHashMap<String, String>()

        for (icon in icons) {
            logger.lifecycle("app icon: ${icon.owner}/${icon.id}")

            (icon.background as? Layer.Color)?.let { generatedColors[icon.colorResource] = it.hex }
            (icon.background as? Layer.Asset)?.let { layer ->
                ADAPTIVE.forEach { (density, size) ->
                    renderer.emit(layer.file, size, File(res, "mipmap-$density/${icon.backgroundResource}.webp"), icon.lossless)
                }
            }
            (icon.foreground as? Layer.Asset)?.let { layer ->
                ADAPTIVE.forEach { (density, size) ->
                    renderer.emit(layer.file, size, File(res, "mipmap-$density/${icon.foregroundResource}.webp"), lossless = true)
                }
            }
            (icon.monochrome as? Layer.Asset)?.let { layer ->
                if (layer.file.extension.equals("svg", true)) {
                    writeVector(layer.file, File(res, "drawable/${icon.monochromeResource}.xml"))
                } else {
                    ADAPTIVE.forEach { (density, size) ->
                        renderer.emit(layer.file, size, File(res, "mipmap-$density/${icon.monochromeResource}.webp"), lossless = true)
                    }
                }
            }

            writeAdaptive(icon, File(res, "mipmap-anydpi-v26/${icon.resource}.xml"))
            if (icon.round) {
                writeAdaptive(icon, File(res, "mipmap-anydpi-v26/${icon.resource}_round.xml"))
            }

            if (icon.legacy) {
                val foreground = resolveAsset(icon, icon.foreground, assets, "foreground")
                val background = when (val layer = icon.background) {
                    is Layer.Color -> null
                    is Layer.Ref -> if (layer.reference.startsWith("@color/")) null else resolveAsset(icon, layer, assets, "background")
                    else -> resolveAsset(icon, icon.background, assets, "background")
                }
                val backgroundColor = backgroundColor(icon, colors)
                if (background == null && backgroundColor == null) {
                    throw GradleException("${icon.id}: cannot build the pre-26 icon, background ${icon.background} is not resolvable, set \"legacy\": false")
                }
                LEGACY.forEach { (density, size) ->
                    val canvas = size * 3 / 2
                    val image = renderer.legacy(
                        background?.let { renderer.render(it, canvas) },
                        backgroundColor,
                        renderer.render(foreground, canvas),
                        size
                    )
                    renderer.encode(image, File(res, "mipmap-$density/${icon.resource}.webp"), icon.lossless)
                    if (icon.round) {
                        renderer.encode(image, File(res, "mipmap-$density/${icon.resource}_round.webp"), icon.lossless)
                    }
                }
            }
        }

        if (generatedColors.isNotEmpty()) {
            writeColors(generatedColors, File(res, "values/appicons.xml"))
        }
        writeManifest(icons.filterNot { it.assetsOnly }, manifestFile.get().asFile)
        writeRegistry(icons.filterNot { it.assetsOnly }, registryFile.get().asFile)
    }

    private fun resolveAsset(icon: Icon, layer: Layer, assets: Map<String, File>, kind: String): File = when (layer) {
        is Layer.Asset -> layer.file
        is Layer.Ref -> assets[layer.reference]
            ?: throw GradleException("${icon.id}: $kind ${layer.reference} is not generated from appicons/, set \"legacy\": false")
        else -> throw GradleException("${icon.id}: $kind cannot be rendered")
    }

    private fun backgroundColor(icon: Icon, colors: Map<String, String>): Color? {
        val hex = when (val layer = icon.background) {
            is Layer.Color -> layer.hex
            is Layer.Ref -> if (layer.reference.startsWith("@color/")) colors[layer.reference.removePrefix("@color/")] else null
            else -> null
        } ?: return null
        val value = hex.removePrefix("#")
        return if (value.length == 8) {
            Color(value.substring(2).toInt(16) or (value.substring(0, 2).toInt(16) shl 24), true)
        } else {
            Color(value.toInt(16))
        }
    }

    private fun parseColors(): Map<String, String> {
        val pattern = Regex("<color\\s+name=\"([^\"]+)\"\\s*>\\s*(#[0-9a-fA-F]{6,8})\\s*</color>")
        val colors = HashMap<String, String>()
        colorFiles.files.filter { it.isFile }.forEach { file ->
            pattern.findAll(file.readText()).forEach { colors[it.groupValues[1]] = it.groupValues[2] }
        }
        return colors
    }

    private fun writeVector(svg: File, target: File) {
        target.parentFile.mkdirs()
        val output = ByteArrayOutputStream()
        val errors = Svg2Vector.parseSvgToXml(svg.toPath(), output)
        if (errors.isNotEmpty()) {
            logger.warn("${svg.name}: $errors")
        }
        target.writeText(output.toString(Charsets.UTF_8.name()))
    }

    private fun writeAdaptive(icon: Icon, target: File) {
        target.parentFile.mkdirs()
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        builder.append("<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n")
        builder.append("    <background android:drawable=\"${icon.reference(icon.background, "background")}\" />\n")
        builder.append("    <foreground android:drawable=\"${icon.reference(icon.foreground, "foreground")}\" />\n")
        icon.reference(icon.monochrome, "monochrome")?.let {
            builder.append("    <monochrome android:drawable=\"$it\" />\n")
        }
        builder.append("</adaptive-icon>\n")
        target.writeText(builder.toString())
    }

    private fun writeColors(colors: Map<String, String>, target: File) {
        target.parentFile.mkdirs()
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        builder.append("<resources>\n")
        colors.forEach { (name, value) -> builder.append("    <color name=\"$name\">$value</color>\n") }
        builder.append("</resources>\n")
        target.writeText(builder.toString())
    }

    private fun writeManifest(icons: List<Icon>, target: File) {
        target.parentFile.mkdirs()
        val prefix = componentPackage.get()
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        builder.append("<!-- Generated by generateAppIcons from appicons/icons.json. Do not edit. -->\n")
        builder.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n\n")
        builder.append("    <application>\n\n")
        icons.forEach { icon ->
            builder.append("        <activity-alias\n")
            builder.append("            android:name=\"$prefix.${icon.component}\"\n")
            builder.append("            android:enabled=\"${icon.id == DEFAULT_ID}\"\n")
            builder.append("            android:exported=\"true\"\n")
            if (icon.id != DEFAULT_ID) {
                builder.append("            android:icon=\"@mipmap/${icon.resource}\"\n")
                builder.append("            android:roundIcon=\"@mipmap/${icon.resource}${if (icon.round) "_round" else ""}\"\n")
            }
            builder.append("            android:targetActivity=\"org.telegram.ui.LaunchActivity\">\n\n")
            builder.append("            <intent-filter>\n")
            builder.append("                <action android:name=\"android.intent.action.MAIN\" />\n\n")
            builder.append("                <category android:name=\"android.intent.category.LAUNCHER\" />\n")
            builder.append("                <category android:name=\"android.intent.category.MULTIWINDOW_LAUNCHER\" />\n")
            builder.append("            </intent-filter>\n\n")
            builder.append("            <meta-data\n")
            builder.append("                android:name=\"android.app.shortcuts\"\n")
            builder.append("                android:resource=\"@xml/shortcuts\" />\n")
            builder.append("        </activity-alias>\n\n")
        }
        builder.append("    </application>\n\n")
        builder.append("</manifest>\n")
        target.writeText(builder.toString())
    }

    private fun writeRegistry(icons: List<Icon>, target: File) {
        target.parentFile.mkdirs()
        val pkg = registryPackage.get()
        val prefix = componentPackage.get()
        val builder = StringBuilder()
        builder.append("package ").append(pkg).append(";\n\n")
        builder.append("import ").append(resourcePackage.get()).append(".R;\n\n")
        builder.append("/** Generated by generateAppIcons from appicons/icons.json. Do not edit. */\n")
        builder.append("public final class GeneratedAppIcons {\n\n")
        builder.append("    private GeneratedAppIcons() {\n    }\n\n")
        builder.append("    public static AppIcon[] create() {\n")
        builder.append("        return new AppIcon[]{\n")
        icons.forEach { icon ->
            val background = icon.reference(icon.background, "background") ?: "0"
            val monochrome = icon.reference(icon.monochrome, "monochrome")
            builder.append("            AppIcon.of(")
            builder.append(quote(icon.id)).append(", ")
            builder.append(quote("$prefix.${icon.component}")).append(", ")
            builder.append(quote(icon.availability)).append(", ")
            builder.append(icon.titleRes?.let { "R.string.$it" } ?: "0").append(", ")
            builder.append(quote(icon.title)).append(", ")
            builder.append(icon.descriptionRes?.let { "R.string.$it" } ?: "0").append(", ")
            builder.append(quote(icon.description)).append(", ")
            builder.append(icon.color?.let { color(it) } ?: "0").append(", ")
            builder.append(quote(icon.authorUsername)).append(", ")
            builder.append(quote(icon.authorName)).append(", ")
            builder.append(resource(background)).append(", ")
            builder.append(background.startsWith("@color/")).append(", ")
            builder.append(resource(icon.reference(icon.foreground, "foreground") ?: "0")).append(", ")
            builder.append(monochrome?.let { resource(it) } ?: "0")
            builder.append("),\n")
        }
        builder.append("        };\n")
        builder.append("    }\n")
        builder.append("}\n")
        target.writeText(builder.toString())
    }

    private fun resource(reference: String): String =
        if (reference.startsWith("@")) "R." + reference.removePrefix("@").replace('/', '.') else reference

    private fun quote(value: String?): String = if (value == null) "null" else "\"$value\""

    private fun color(value: String): String {
        val hex = value.removePrefix("#")
        return if (hex.length == 8) "0x$hex" else "0xFF$hex"
    }

    private companion object {
        const val DEFAULT_ID = "default"
        val ADAPTIVE = listOf("mdpi" to 108, "hdpi" to 162, "xhdpi" to 216, "xxhdpi" to 324, "xxxhdpi" to 432)
        val LEGACY = listOf("xxhdpi" to 144, "xxxhdpi" to 192)
    }
}
