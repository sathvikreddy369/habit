package com.habit1.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit1.app.ui.theme.HabitColors
import com.habit1.app.ui.theme.HabitIcons

data class CategorySuggestion(
    val name: String,
    val iconKey: String,
    val colorHex: String
)

val CATEGORY_SUGGESTIONS = listOf(
    CategorySuggestion("Health", "health", "#EC407A"),
    CategorySuggestion("Fitness", "fitness", "#F08A6C"),
    CategorySuggestion("Work", "work", "#42A5F5"),
    CategorySuggestion("Learning", "book", "#7E57C2"),
    CategorySuggestion("Morning", "morning", "#FFA726"),
    CategorySuggestion("Evening", "evening", "#5C6BC0"),
    CategorySuggestion("Chores", "home", "#26A69A"),
    CategorySuggestion("Mindfulness", "meditation", "#26C6DA")
)

/**
 * Dialog for creating or editing a habit category.
 * Provides name input, quick suggestion chips, curated 16-color palette,
 * 36 standard vector icons, and custom emoji support.
 */
@Composable
fun CategoryPickerDialog(
    initialName: String = "",
    initialColorHex: String? = null,
    initialIcon: String? = null,
    isEdit: Boolean = false,
    onConfirm: (name: String, colorHex: String?, icon: String?) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColorHex by remember {
        mutableStateOf(initialColorHex ?: HabitColors.DEFAULT_COLOR_HEX)
    }
    var selectedIcon by remember { mutableStateOf(initialIcon ?: "fitness") }
    var customEmoji by remember {
        mutableStateOf(if (HabitIcons.isEmoji(initialIcon)) initialIcon ?: "" else "")
    }
    var selectedTab by remember {
        mutableIntStateOf(if (HabitIcons.isEmoji(initialIcon)) 1 else 0)
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val nameValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEdit) "Edit Category" else "New Category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isEdit && onDelete != null) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Category",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Quick Suggestion Chips
                if (!isEdit) {
                    Text(
                        text = "Quick suggestions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CATEGORY_SUGGESTIONS.forEach { suggestion ->
                            AssistChip(
                                onClick = {
                                    name = suggestion.name
                                    selectedColorHex = suggestion.colorHex
                                    selectedIcon = suggestion.iconKey
                                    selectedTab = 0
                                },
                                label = {
                                    Text(
                                        suggestion.name,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    isError = name.isNotEmpty() && name.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Color Selection
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HabitColors.PALETTE.forEach { (hex, color) ->
                        val isSelected = hex.equals(selectedColorHex, ignoreCase = true)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedColorHex = hex }
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Icon Selection
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Icons") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Emoji") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    // 36 Vector icons grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(HabitIcons.ICONS) { item ->
                            val isSelected = selectedIcon == item.key
                            val tintColor = if (isSelected) {
                                HabitColors.parseColor(selectedColorHex)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) {
                                            tintColor.copy(alpha = 0.15f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                1.5.dp,
                                                tintColor,
                                                RoundedCornerShape(8.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { selectedIcon = item.key }
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = tintColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Emoji / Symbol Input
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = customEmoji,
                            onValueChange = { input ->
                                customEmoji = input.trim()
                                if (customEmoji.isNotEmpty()) {
                                    selectedIcon = customEmoji
                                }
                            },
                            label = { Text("Enter emoji or symbol") },
                            placeholder = { Text("e.g. 🧘, 🏃, 📚, 💡") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (customEmoji.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = "Preview: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = customEmoji,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalIcon = if (selectedTab == 1 && customEmoji.isNotBlank()) {
                        customEmoji
                    } else {
                        selectedIcon.ifBlank { null }
                    }
                    onConfirm(name.trim(), selectedColorHex, finalIcon)
                    onDismiss()
                },
                enabled = nameValid
            ) {
                Text(if (isEdit) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Category?") },
            text = {
                Text("Habits in this category will become Uncategorized. No habits or historical records will be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
