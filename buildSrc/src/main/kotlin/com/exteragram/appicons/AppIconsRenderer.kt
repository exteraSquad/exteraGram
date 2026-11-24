package com.exteragram.appicons

import org.gradle.api.GradleException
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

internal class AppIconsRenderer(
    private val tempDir: File,
    private val overrides: Map<String, String>
) {

    private val executables = HashMap<String, File>()
    private val decoded = HashMap<File, BufferedImage>()
    private val layerQuality = 90
    private var counter = 0

    fun render(source: File, size: Int): BufferedImage = when (source.extension.lowercase()) {
        "svg" -> {
            val png = temp("png")
            run("rsvg-convert", "librsvg", "-w", size.toString(), "-h", size.toString(), "-f", "png", "-o", png.absolutePath, source.absolutePath)
            ImageIO.read(png) ?: throw GradleException("rsvg-convert produced no image for ${source.name}")
        }
        else -> scale(natural(source), size)
    }

    fun emit(source: File, size: Int, target: File, lossless: Boolean) {
        target.parentFile.mkdirs()
        if (source.extension.equals("webp", true)) {
            val natural = natural(source)
            if (natural.width == size && natural.height == size) {
                if (!lossless) {
                    encode(natural, target, false, layerQuality)
                    if (target.length() < source.length()) return
                }
                source.copyTo(target, overwrite = true)
                return
            }
        }
        encode(render(source, size), target, lossless, layerQuality)
    }

    fun encode(image: BufferedImage, target: File, lossless: Boolean, quality: Int = 95) {
        target.parentFile.mkdirs()
        val png = temp("png")
        ImageIO.write(image, "png", png)

        val losslessFile = temp("webp")
        run("cwebp", "webp", "-quiet", "-lossless", png.absolutePath, "-o", losslessFile.absolutePath)
        if (lossless) {
            losslessFile.copyTo(target, overwrite = true)
            return
        }

        val lossyFile = temp("webp")
        val tuning = if (quality < 95) arrayOf("-m", "6", "-sharp_yuv") else emptyArray()
        run("cwebp", "webp", "-quiet", "-q", quality.toString(), *tuning, "-alpha_q", "100", png.absolutePath, "-o", lossyFile.absolutePath)
        val best = if (lossyFile.length() < losslessFile.length()) lossyFile else losslessFile
        best.copyTo(target, overwrite = true)
    }

    fun legacy(background: BufferedImage?, backgroundColor: Color?, foreground: BufferedImage, size: Int): BufferedImage {
        val canvasSize = size * 3 / 2
        val canvas = BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB)
        val canvasGraphics = canvas.createGraphics()
        canvasGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        if (backgroundColor != null) {
            canvasGraphics.color = backgroundColor
            canvasGraphics.fillRect(0, 0, canvasSize, canvasSize)
        }
        if (background != null) {
            canvasGraphics.drawImage(scale(background, canvasSize), 0, 0, null)
        }
        canvasGraphics.drawImage(scale(foreground, canvasSize), 0, 0, null)
        canvasGraphics.dispose()

        val visible = canvas.getSubimage(size / 4, size / 4, size, size)
        val masked = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = masked.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color.WHITE
        graphics.fill(Ellipse2D.Float(0f, 0f, size.toFloat(), size.toFloat()))
        graphics.composite = AlphaComposite.SrcIn
        graphics.drawImage(visible, 0, 0, null)
        graphics.dispose()
        return masked
    }

    fun solid(color: Color, size: Int): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(0, 0, size, size)
        graphics.dispose()
        return image
    }

    private fun natural(source: File): BufferedImage = decoded.getOrPut(source) {
        when (source.extension.lowercase()) {
            "webp" -> {
                val png = temp("png")
                run("dwebp", "webp", "-quiet", source.absolutePath, "-o", png.absolutePath)
                ImageIO.read(png) ?: throw GradleException("dwebp produced no image for ${source.name}")
            }
            else -> ImageIO.read(source) ?: throw GradleException("cannot read ${source.absolutePath}")
        }
    }

    private fun scale(image: BufferedImage, size: Int): BufferedImage {
        if (image.width == size && image.height == size) return image
        var current = image
        while (current.width / 2 > size) {
            current = resize(current, current.width / 2)
        }
        return resize(current, size)
    }

    private fun resize(image: BufferedImage, size: Int): BufferedImage {
        val result = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.drawImage(image, 0, 0, size, size, null)
        graphics.dispose()
        return result
    }

    private fun temp(extension: String): File {
        tempDir.mkdirs()
        return File(tempDir, "tmp${counter++}.$extension")
    }

    private fun run(tool: String, brewPackage: String, vararg arguments: String) {
        val executable = executables.getOrPut(tool) { locate(tool, brewPackage) }
        val process = ProcessBuilder(listOf(executable.absolutePath) + arguments)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8).trim()
        val code = process.waitFor()
        if (code != 0) {
            throw GradleException("$tool failed ($code): $output")
        }
    }

    private fun locate(tool: String, brewPackage: String): File {
        overrides[tool]?.let { path ->
            val file = File(path)
            if (!file.canExecute()) throw GradleException("appicons.$tool points at $path, which is not executable")
            return file
        }
        val candidates = (System.getenv("PATH") ?: "").split(File.pathSeparatorChar) + listOf("/opt/homebrew/bin", "/usr/local/bin")
        val found = candidates.map { File(it, tool) }.firstOrNull { it.isFile && it.canExecute() }
        return found ?: throw GradleException(
            "$tool not found in PATH. Install it with \"brew install $brewPackage\" (or \"apt install $brewPackage\"), " +
                "or point at it with -Pappicons.$tool=/path/to/$tool"
        )
    }
}
