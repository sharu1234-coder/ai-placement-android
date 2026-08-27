package com.aiplacementos.app.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.aiplacementos.app.database.entity.AlarmConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmConfigDao {
    @Query("SELECT * FROM alarm_configs ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmConfigEntity>>

    @Query("SELECT * FROM alarm_configs WHERE enabled = 1")
    suspend fun getAllEnabled(): List<AlarmConfigEntity>

    @Query("SELECT * FROM alarm_configs WHERE id = :id")
    suspend fun getById(id: Long): AlarmConfigEntity?

    @Insert
    suspend fun insert(alarm: AlarmConfigEntity): Long

    @Update
    suspend fun update(alarm: AlarmConfigEntity)

    @Delete
    suspend fun delete(alarm: AlarmConfigEntity)
}
