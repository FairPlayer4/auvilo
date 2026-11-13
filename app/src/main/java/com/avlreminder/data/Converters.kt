package com.avlreminder.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun fromDateTime(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun toDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)
}
