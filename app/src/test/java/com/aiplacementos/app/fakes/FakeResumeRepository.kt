package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.data.remote.ResumeUploadResponseDto
import com.aiplacementos.app.domain.repository.ResumeRepository

class FakeResumeRepository : ResumeRepository {
    var uploadResult: Result<ResumeUploadResponseDto>? = null
    var latestResumeResult: Result<ResumeSummaryDto?> = Result.success(null)
    var deleteResult: Result<Unit> = Result.success(Unit)
    var downloadResult: Result<ByteArray> = Result.success(ByteArray(0))

    var downloadCount: Int = 0
    var lastDownloadedResumeId: Int? = null

    var lastDeletedResumeId: Int? = null

    /** Every Idempotency-Key this fake was handed, in order. A test asserting these are
     * equal across retries is what proves the caller holds one key per operation. */
    val keys = mutableListOf<String?>()

    override suspend fun uploadResume(
        fileName: String,
        bytes: ByteArray,
        idempotencyKey: String?,
    ): ResumeUploadResponseDto {
        keys += idempotencyKey
        return requireNotNull(uploadResult) { "uploadResult not configured" }.getOrThrow()
    }

    override suspend fun getLatestResume(): ResumeSummaryDto? = latestResumeResult.getOrThrow()

    override suspend fun downloadResumeFile(resumeId: Int): ByteArray {
        downloadCount++
        lastDownloadedResumeId = resumeId
        return downloadResult.getOrThrow()
    }

    override suspend fun deleteResume(resumeId: Int) {
        lastDeletedResumeId = resumeId
        deleteResult.getOrThrow()
    }
}
