package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.data.remote.ResumeUploadResponseDto
import com.aiplacementos.app.data.remote.ResumesApi
import com.aiplacementos.app.domain.repository.ResumeRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ResumeRepositoryImpl(private val resumesApi: ResumesApi) : ResumeRepository {
    override suspend fun uploadResume(fileName: String, bytes: ByteArray): ResumeUploadResponseDto {
        val requestBody = bytes.toRequestBody("application/pdf".toMediaType())
        val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
        return resumesApi.uploadResume(part)
    }

    override suspend fun getLatestResume(): ResumeSummaryDto? = resumesApi.getMyLatestResume().value

    override suspend fun downloadResumeFile(resumeId: Int): ByteArray =
        resumesApi.getResumeFile(resumeId).use { it.bytes() }

    override suspend fun deleteResume(resumeId: Int) {
        resumesApi.deleteResume(resumeId)
    }
}
