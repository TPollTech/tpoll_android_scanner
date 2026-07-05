package com.tpoll.scanner.data

import androidx.room.TypeConverter
import com.tpoll.scanner.model.RiskLevel

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> = if (value.isBlank()) emptyList() else value.split(",")

    @TypeConverter
    fun fromRiskLevel(level: RiskLevel): String = level.name

    @TypeConverter
    fun toRiskLevel(value: String): RiskLevel = RiskLevel.valueOf(value)
}
