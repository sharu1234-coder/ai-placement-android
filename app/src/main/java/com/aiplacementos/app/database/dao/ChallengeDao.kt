package com.aiplacementos.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aiplacementos.app.database.entity.ChallengeEntity
import com.aiplacementos.app.database.entity.ChallengeQuestionEntity

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges WHERE clientAlarmId = :clientAlarmId")
    suspend fun getChallenge(clientAlarmId: String): ChallengeEntity?

    @Query(
        "SELECT * FROM challenges WHERE alarmConfigId = :alarmConfigId AND status = 'in_progress' " +
            "ORDER BY createdAtEpochMillis DESC LIMIT 1"
    )
    suspend fun getActiveChallengeForAlarm(alarmConfigId: Long): ChallengeEntity?

    @Query("SELECT * FROM challenges WHERE status = 'in_progress'")
    suspend fun getAllInProgress(): List<ChallengeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChallenge(challenge: ChallengeEntity)

    @Update
    suspend fun updateChallenge(challenge: ChallengeEntity)

    @Query("SELECT * FROM challenge_questions WHERE clientAlarmId = :clientAlarmId ORDER BY questionIndex")
    suspend fun getQuestions(clientAlarmId: String): List<ChallengeQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestions(questions: List<ChallengeQuestionEntity>)

    @Query(
        "UPDATE challenge_questions SET isCorrect = 1 " +
            "WHERE clientAlarmId = :clientAlarmId AND backendQuestionId = :backendQuestionId"
    )
    suspend fun markCorrect(clientAlarmId: String, backendQuestionId: Int)

    @Query("SELECT COUNT(*) FROM challenge_questions WHERE clientAlarmId = :clientAlarmId AND isCorrect = 1")
    suspend fun correctCount(clientAlarmId: String): Int
}
