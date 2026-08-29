package com.example.vision.color

import android.graphics.Color
import com.example.vision.model.SampledColor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Standard color classification dictionary for accurate real-time color naming.
 */
object ColorDictionary {

    data class NamedColor(
        val name: String,
        val r: Int,
        val g: Int,
        val b: Int,
        val hex: String
    )

    private val STANDARD_COLORS = listOf(
        NamedColor("Pure Red", 255, 0, 0, "#FF0000"),
        NamedColor("Crimson", 220, 20, 60, "#DC143C"),
        NamedColor("Firebrick", 178, 34, 34, "#B22222"),
        NamedColor("Dark Red", 139, 0, 0, "#8B0000"),
        NamedColor("Coral", 255, 127, 80, "#FF7F50"),
        NamedColor("Tomato", 255, 99, 71, "#FF6347"),
        NamedColor("Orange Red", 255, 69, 0, "#FF4500"),
        NamedColor("Vibrant Orange", 255, 140, 0, "#FF8C00"),
        NamedColor("Dark Orange", 255, 120, 0, "#FF7800"),
        NamedColor("Amber", 255, 191, 0, "#FFBF00"),
        NamedColor("Gold", 255, 215, 0, "#FFD700"),
        NamedColor("Bright Yellow", 255, 255, 0, "#FFFF00"),
        NamedColor("Lemon Yellow", 255, 247, 0, "#FFF700"),
        NamedColor("Lime Green", 50, 205, 50, "#32CD32"),
        NamedColor("Chartreuse", 127, 255, 0, "#7FFF00"),
        NamedColor("Vibrant Green", 0, 230, 64, "#00E640"),
        NamedColor("Forest Green", 34, 139, 34, "#228B22"),
        NamedColor("Dark Green", 0, 100, 0, "#006400"),
        NamedColor("Olive Green", 128, 128, 0, "#808000"),
        NamedColor("Teal", 0, 128, 128, "#008080"),
        NamedColor("Mint Green", 62, 218, 180, "#3EDAB4"),
        NamedColor("Cyan / Aqua", 0, 255, 255, "#00FFFF"),
        NamedColor("Turquoise", 64, 224, 208, "#40E0D0"),
        NamedColor("Deep Sky Blue", 0, 191, 255, "#00BFFF"),
        NamedColor("Sky Blue", 135, 206, 235, "#87CEEB"),
        NamedColor("Cornflower Blue", 100, 149, 237, "#6495ED"),
        NamedColor("Royal Blue", 65, 105, 225, "#4169E1"),
        NamedColor("Vibrant Blue", 0, 102, 255, "#0066FF"),
        NamedColor("Navy Blue", 0, 0, 128, "#000080"),
        NamedColor("Midnight Blue", 25, 25, 112, "#191970"),
        NamedColor("Indigo", 75, 0, 130, "#4B0082"),
        NamedColor("Electric Violet", 138, 43, 226, "#8A2BE2"),
        NamedColor("Purple", 128, 0, 128, "#800080"),
        NamedColor("Magenta", 255, 0, 255, "#FF00FF"),
        NamedColor("Fuchsia", 255, 20, 147, "#FF1493"),
        NamedColor("Hot Pink", 255, 105, 180, "#FF69B4"),
        NamedColor("Light Pink", 255, 182, 193, "#FFB6C1"),
        NamedColor("Rosy Brown", 188, 143, 143, "#BC8F8F"),
        NamedColor("Saddle Brown", 139, 69, 19, "#8B4513"),
        NamedColor("Chocolate Brown", 210, 105, 30, "#D2691E"),
        NamedColor("Sandy Tan", 244, 164, 96, "#F4A460"),
        NamedColor("Beige", 245, 245, 220, "#F5F5DC"),
        NamedColor("Ivory / Warm White", 255, 255, 240, "#FFFFF0"),
        NamedColor("Pure White", 255, 255, 255, "#FFFFFF"),
        NamedColor("Silver / Light Gray", 211, 211, 211, "#D3D3D3"),
        NamedColor("Cool Gray", 160, 170, 185, "#A0AAB9"),
        NamedColor("Medium Gray", 128, 128, 128, "#808080"),
        NamedColor("Slate Gray", 112, 128, 144, "#708090"),
        NamedColor("Charcoal", 54, 69, 79, "#36454F"),
        NamedColor("Dark Slate", 47, 79, 79, "#2F4F4F"),
        NamedColor("Pure Black", 15, 15, 15, "#0F0F0F")
    )

    /**
     * Finds the closest human color name using weighted Euclidean distance in RGB space.
     */
    fun findClosestColor(r: Int, g: Int, b: Int, sampleX: Float = 0.5f, sampleY: Float = 0.5f): SampledColor {
        val clampedR = r.coerceIn(0, 255)
        val clampedG = g.coerceIn(0, 255)
        val clampedB = b.coerceIn(0, 255)

        var bestMatch = STANDARD_COLORS.first()
        var minDistance = Double.MAX_VALUE

        for (candidate in STANDARD_COLORS) {
            // Perceptually weighted Euclidean RGB distance (Redmean metric)
            val rMean = (clampedR + candidate.r) / 2.0
            val deltaR = (clampedR - candidate.r).toDouble()
            val deltaG = (clampedG - candidate.g).toDouble()
            val deltaB = (clampedB - candidate.b).toDouble()

            val weightR = 2.0 + (rMean / 256.0)
            val weightG = 4.0
            val weightB = 2.0 + ((255.0 - rMean) / 256.0)

            val distance = sqrt(weightR * deltaR.pow(2) + weightG * deltaG.pow(2) + weightB * deltaB.pow(2))
            if (distance < minDistance) {
                minDistance = distance
                bestMatch = candidate
            }
        }

        // HSV calculation
        val hsv = FloatArray(3)
        Color.RGBToHSV(clampedR, clampedG, clampedB, hsv)

        // Relative luminance
        val luminance = (0.299f * clampedR + 0.587f * clampedG + 0.114f * clampedB) / 255f
        val hexString = String.format("#%02X%02X%02X", clampedR, clampedG, clampedB)

        return SampledColor(
            name = bestMatch.name,
            red = clampedR,
            green = clampedG,
            blue = clampedB,
            hex = hexString,
            hue = hsv[0],
            saturation = hsv[1],
            value = hsv[2],
            luminance = luminance,
            isApproximate = true,
            sampleX = sampleX,
            sampleY = sampleY
        )
    }
}
