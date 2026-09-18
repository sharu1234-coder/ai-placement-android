package com.aiplacementos.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aiplacementos.app.database.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Insert
    suspend fun insert(notification: NotificationEntity): Long

    @Query("UPDATE notifications SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE notifications SET read = 1 WHERE read = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM notifications WHERE read = 0")
    suspend fun getUnreadCount(): Int
}
