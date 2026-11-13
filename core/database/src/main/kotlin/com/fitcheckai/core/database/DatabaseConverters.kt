package com.fitcheckai.core.database

import androidx.room.TypeConverter
import java.time.Instant

/** Available to all Room tables; existing TEXT/INTEGER columns stay compatible with schema 1. */
class DatabaseConverters {
    @TypeConverter fun encodeList(value: List<String>?): String? = value?.let(ListCodec::encode)
    @TypeConverter fun decodeList(value: String?): List<String>? = value?.let(ListCodec::decode)
    @TypeConverter fun encodeSet(value: Set<String>?): String? = value?.let(ListCodec::encode)
    @TypeConverter fun decodeSet(value: String?): Set<String>? = value?.let { ListCodec.decode(it).toSet() }
    @TypeConverter fun encodeInstant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun decodeInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
