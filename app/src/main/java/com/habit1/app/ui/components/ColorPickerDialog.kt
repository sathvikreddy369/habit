package com.habit1.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit1.app.ui.theme.HabitColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Material 3 dialog allowing the user to select a habit color
 * from curated presets or from an interactive RGB/HSV color wheel with RGB sliders.
 */
@Composable
fun ColorPickerDialog(
    selectedColorHex: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Presets, 1 = RGB Wheel
    var tempSelectedHex by remember { mutableStateOf(selectedColorHex) }
    var customHexInput by remember { mutableStateOf(selectedColorHex.removePrefix("#")) }

    // Parse initial color into HSV
    val initialColor = remember(selectedColorHex) { HabitColors.parseColor(selectedColorHex) }
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.rgb(
                (initialColor.red * 255).toInt(),
                (initialColor.green * 255).toInt(),
                (initialColor.blue * 255).toInt()
            ),
            hsv
        )
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }

    fun updateFromHsv(h: Float, s: Float, v: Float) {
        hue = h
        saturation = s
        brightness = v
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val hex = String.format("%02X%02X%02X", r, g, b)
        tempSelectedHex = "#$hex"
        customHexInput = hex
    }

    fun updateFromHex(hexStr: String) {
        val clean = hexStr.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
        customHexInput = clean
        if (clean.length == 6) {
            tempSelectedHex = "#$clean"
            val color = HabitColors.parseColor("#$clean")
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(
                android.graphics.Color.rgb(
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt()
                ),
                hsv
            )
            hue = hsv[0]
            saturation = hsv[1]
            brightness = hsv[2]
        }
    }

    val currentColor = remember(tempSelectedHex) { HabitColors.parseColor(tempSelectedHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select habit color",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // Live preview chip
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                // Tab Row for Presets vs Wheel
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Presets", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("RGB Wheel", fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // TAB 1: Presets Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp)
                    ) {
                        items(HabitColors.PALETTE) { (hex, color) ->
                            val isSelected = (hex.equals(tempSelectedHex, ignoreCase = true))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(color)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = Color.White,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        } else {
                                            Modifier.border(
                                                width = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                    )
                                    .clickable {
                                        updateFromHex(hex)
                                    }
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (hex == "#E0E0E0" || hex == "#FFCA28" || hex == "#D4E157") Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // TAB 2: Interactive HSV Wheel & Sliders
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Color Wheel Canvas
                        val wheelSize = 170.dp
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(wheelSize)
                                .padding(4.dp)
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .size(wheelSize)
                                    .pointerInput(Unit) {
                                        detectTapGestures { offset ->
                                            val centerX = size.width / 2f
                                            val centerY = size.height / 2f
                                            val dx = offset.x - centerX
                                            val dy = offset.y - centerY
                                            val radius = size.width / 2f
                                            val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
                                            val angle = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f)
                                            val s = (dist / radius).coerceIn(0f, 1f)
                                            updateFromHsv(angle, s, brightness)
                                        }
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, _ ->
                                            change.consume()
                                            val centerX = size.width / 2f
                                            val centerY = size.height / 2f
                                            val dx = change.position.x - centerX
                                            val dy = change.position.y - centerY
                                            val radius = size.width / 2f
                                            val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
                                            val angle = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f)
                                            val s = (dist / radius).coerceIn(0f, 1f)
                                            updateFromHsv(angle, s, brightness)
                                        }
                                    }
                            ) {
                                val radius = size.width / 2f
                                val center = Offset(radius, radius)

                                val sweepBrush = Brush.sweepGradient(
                                    listOf(
                                        Color.Red,
                                        Color.Yellow,
                                        Color.Green,
                                        Color.Cyan,
                                        Color.Blue,
                                        Color.Magenta,
                                        Color.Red
                                    ),
                                    center = center
                                )
                                val radialBrush = Brush.radialGradient(
                                    listOf(Color.White, Color.Transparent),
                                    center = center,
                                    radius = radius
                                )

                                drawCircle(brush = sweepBrush, radius = radius, center = center)
                                drawCircle(brush = radialBrush, radius = radius, center = center)

                                // Selector Handle
                                val handleAngleRad = Math.toRadians(hue.toDouble())
                                val handleDist = saturation * radius
                                val handleX = center.x + (handleDist * cos(handleAngleRad)).toFloat()
                                val handleY = center.y + (handleDist * sin(handleAngleRad)).toFloat()

                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    radius = 10.dp.toPx(),
                                    center = Offset(handleX, handleY),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 8.dp.toPx(),
                                    center = Offset(handleX, handleY),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Brightness Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Brightness",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(76.dp)
                            )
                            Slider(
                                value = brightness,
                                onValueChange = { updateFromHsv(hue, saturation, it) },
                                valueRange = 0.05f..1.0f,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // RGB Sliders for Precision
                        val curRed = (currentColor.red * 255).toInt()
                        val curGreen = (currentColor.green * 255).toInt()
                        val curBlue = (currentColor.blue * 255).toInt()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "R: $curRed",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Red,
                                modifier = Modifier.width(54.dp)
                            )
                            Slider(
                                value = curRed.toFloat(),
                                onValueChange = { r ->
                                    val newR = r.toInt()
                                    val hex = String.format("%02X%02X%02X", newR, curGreen, curBlue)
                                    updateFromHex(hex)
                                },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "G: $curGreen",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.width(54.dp)
                            )
                            Slider(
                                value = curGreen.toFloat(),
                                onValueChange = { g ->
                                    val newG = g.toInt()
                                    val hex = String.format("%02X%02X%02X", curRed, newG, curBlue)
                                    updateFromHex(hex)
                                },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF2E7D32), activeTrackColor = Color(0xFF2E7D32)),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "B: $curBlue",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1976D2),
                                modifier = Modifier.width(54.dp)
                            )
                            Slider(
                                value = curBlue.toFloat(),
                                onValueChange = { b ->
                                    val newB = b.toInt()
                                    val hex = String.format("%02X%02X%02X", curRed, curGreen, newB)
                                    updateFromHex(hex)
                                },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF1976D2), activeTrackColor = Color(0xFF1976D2)),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Custom hex input row (always visible)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = customHexInput,
                        onValueChange = { updateFromHex(it) },
                        label = { Text("Hex Code (#)") },
                        prefix = { Text("#") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalHex = if (customHexInput.length == 6) "#$customHexInput" else tempSelectedHex
                    onColorSelected(finalHex)
                    onDismiss()
                }
            ) {
                Text("Select", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
