package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.ResumeAnalysisDto
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.data.remote.ResumeUploadResponseDto
import com.aiplacementos.app.domain.repository.ResumeRepository

class FakeResumeRepository : ResumeRepository {
    var uploadResult: Result<ResumeUploadResponseDto>? = null
    var analysisResult: Result<ResumeAnalysisDto>? = null
    var latestResumeResult: Result<ResumeSummaryDto?> = Result.success(null)
    var deleteResult: Result<Unit> = Result.success(Unit)

    var lastDeletedResumeId: Int? = null

    override suspend fun uploadResume(fileName: String, bytes: ByteArray): ResumeUploadResponseDto =
        requireNotNull(uploadResult) { "uploadResult not configured" }.getOrThrow()

    override suspend fun getAnalysis(resumeId: Int): ResumeAnalysisDto =
        requireNotNull(analysisResult) { "analysisResult not configured" }.getOrThrow()

    override suspend fun getLatestResume(): ResumeSummaryDto? = latestResumeResult.getOrThrow()

    override suspend fun deleteResume(resumeId: Int) {
        lastDeletedResumeId = resumeId
        deleteResult.getOrThrow()
    }
}
