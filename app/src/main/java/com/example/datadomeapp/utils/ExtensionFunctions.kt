package com.example.datadomeapp.utils

import android.text.InputFilter
import android.text.Spanned

// Extension functions for better performance and safety
fun Double.isInteger(): Boolean = this % 1 == 0.0

fun Double.roundToTwoDecimals(): Double {
    return "%.2f".format(this).toDoubleOrNull() ?: 0.0
}

fun Double.safeMax(other: Double): Double {
    val safeThis = if (this.isNaN() || this.isInfinite()) 0.0 else this
    return maxOf(safeThis, other)
}

fun Double.safePercentage(maxPoints: Double): Double {
    if (this.isNaN() || this.isInfinite() || maxPoints <= 0) return 50.0
    return (this / maxPoints) * 100.0
}

// Safe string to double conversion
fun String.toDoubleSafe(default: Double = 0.0): Double {
    return if (this.isBlank() || this == ".") default
    else this.toDoubleOrNull() ?: default
}

// Safe average calculation
fun List<Double>.safeAverage(): Double {
    if (this.isEmpty()) return 50.0
    val validValues = this.filter { !it.isNaN() && !it.isInfinite() }
    return if (validValues.isEmpty()) 50.0 else validValues.average()
}

// Input filter for decimal numbers
class DecimalInputFilter(private val maxDecimalPlaces: Int = 1) : InputFilter {
    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        val newText = dest.toString().replaceRange(dstart, dend, source.subSequence(start, end))

        if (newText.isEmpty()) return null

        // Allow only numbers and decimal point
        if (!newText.matches(Regex("^\\d*\\.?\\d*$"))) {
            return ""
        }

        // Check decimal places
        if (newText.contains('.')) {
            val decimalPart = newText.substringAfter('.')
            if (decimalPart.length > maxDecimalPlaces) {
                return ""
            }
        }

        return null
    }
}