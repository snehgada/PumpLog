package com.workouttracker.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
    @TypeConverter fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun instantToEpochMilli(instant: Instant?): Long? = instant?.toEpochMilli()
    @TypeConverter fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
