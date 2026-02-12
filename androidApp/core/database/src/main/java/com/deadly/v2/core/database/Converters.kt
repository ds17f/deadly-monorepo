package com.deadly.v2.core.database

import androidx.room.TypeConverter

class Converters {
    
    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        return value?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
    
    @TypeConverter
    fun fromListString(list: List<String>?): String? {
        return list?.joinToString(",")
    }
}