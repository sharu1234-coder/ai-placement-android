package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.data.remote.ResumeUploadResponseDto

interface ResumeRepository {
    /** [idempotencyKey] names the logical operation, so a transport replay of it is free. */
    suspend fun uploadResume(
        fileName: String,
        bytes: ByteArray,
        idempotencyKey: String? = null,
    ): ResumeUploadResponseDto
    suspend fun getLatestResume(): ResumeSummaryDto?
    /** The student's own resume PDF, for in-app preview. Returns the raw bytes; the storage
     * location stays server-side and is never exposed to the client. */
    suspend fun downloadResumeFile(resumeId: Int): ByteArray

    suspend fun deleteResume(resumeId: Int)
}
