package com.rostrum.ui.main

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Basic ANSI Escape Code Parser for Compose
 */
object AnsiUtils {
    
    fun parseAnsi(text: String): AnnotatedString {
        return buildAnnotatedString {
            var currentIndex = 0
            var currentColor = Color.Green // Default terminal text color
            var currentBackground = Color.Transparent
            var isBold = false

            // ANSI Escape Regex (matches \u001B[...m)
            val regex = Regex("\u001B\\[[0-9;]*m")
            
            val matches = regex.findAll(text)
            
            for (match in matches) {
                // Append text before the code
                if (match.range.first > currentIndex) {
                    val segment = text.substring(currentIndex, match.range.first)
                    append(AnnotatedString(
                        text = segment,
                        spanStyle = SpanStyle(
                            color = currentColor,
                            background = currentBackground,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    ))
                }
                
                // Process the code
                val content = match.value.drop(2).dropLast(1) // Remove \u001B[ and m
                val codes = if (content.isEmpty()) listOf(0) else content.split(";").mapNotNull { it.toIntOrNull() }
                
                for (code in codes) {
                    when (code) {
                        0 -> { // Reset
                            currentColor = Color.Green
                            currentBackground = Color.Transparent
                            isBold = false
                        }
                        1 -> isBold = true
                        // Foreground Colors
                        30 -> currentColor = Color.Black
                        31 -> currentColor = Color(0xFFE74856) // Red
                        32 -> currentColor = Color(0xFF16C60C) // Green
                        33 -> currentColor = Color(0xFFF9F1A5) // Yellow
                        34 -> currentColor = Color(0xFF3B78FF) // Blue
                        35 -> currentColor = Color(0xFFB4009E) // Magenta
                        36 -> currentColor = Color(0xFF61D6D6) // Cyan
                        37 -> currentColor = Color(0xFFF2F2F2) // White
                        // Permission/File types specific defaults
                        90 -> currentColor = Color.Gray // Bright Black
                        91 -> currentColor = Color(0xFFFF4D4D) // Bright Red
                        92 -> currentColor = Color(0xFF33FF00) // Bright Green
                        94 -> currentColor = Color(0xFF00BFFF) // Bright Blue
                    }
                }
                
                currentIndex = match.range.last + 1
            }
            
            // Append remaining text
            if (currentIndex < text.length) {
                append(AnnotatedString(
                    text = text.substring(currentIndex),
                    spanStyle = SpanStyle(
                        color = currentColor,
                        background = currentBackground,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                ))
            }
        }
    }
}
