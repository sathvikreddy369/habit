package com.habit1.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-defined organizational category for habits.
 */
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["display_order"])
    ]
)
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color_hex", defaultValue = "NULL")
    val colorHex: String? = null,

    @ColumnInfo(name = "icon", defaultValue = "NULL")
    val icon: String? = null,

    @ColumnInfo(name = "display_order", defaultValue = "0")
    val displayOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
