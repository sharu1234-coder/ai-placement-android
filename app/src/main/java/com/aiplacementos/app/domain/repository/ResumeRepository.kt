package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.ResumeAnalysisDto
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.data.remote.ResumeUploadResponseDto

interface ResumeRepository {
    suspend fun uploadResume(fileName: String, bytes: ByteArray): ResumeUploadResponseDto
    suspend fun getAnalysis(resumeId: Int): ResumeAnalysisDto
    suspend fun getLatestResume(): ResumeSummaryDto?
    suspend fun deleteResume(resumeId: Int)
}
