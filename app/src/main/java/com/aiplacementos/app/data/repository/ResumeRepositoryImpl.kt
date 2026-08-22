package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.ResumeAnalysisDto
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

    override suspend fun getAnalysis(resumeId: Int): ResumeAnalysisDto =
        resumesApi.getResumeAnalysis(resumeId)
}
