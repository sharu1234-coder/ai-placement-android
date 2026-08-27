package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResumeEducationDto(
    val institution: String,
    val degree: String? = null,
    @SerialName("field_of_study") val fieldOfStudy: String? = null,
    @SerialName("start_year") val startYear: Int? = null,
    @SerialName("end_year") val endYear: Int? = null,
)

@Serializable
data class ResumeExperienceDto(
    val company: String,
    val title: String? = null,
    val description: String? = null,
)

@Serializable
data class ResumeProjectDto(
    val title: String,
    val description: String? = null,
    @SerialName("tech_stack") val techStack: String? = null,
)

@Serializable
data class ResumeCertificationDto(
    val name: String,
    val issuer: String? = null,
)

@Serializable
data class ResumeAnalysisDto(
    val id: Int,
    @SerialName("resume_id") val resumeId: Int,
    val skills: List<String>,
    val education: List<ResumeEducationDto>,
    val experience: List<ResumeExperienceDto>,
    val projects: List<ResumeProjectDto>,
    val certifications: List<ResumeCertificationDto>,
    @SerialName("ats_score") val atsScore: Double,
    @SerialName("weak_sections") val weakSections: List<String>,
    @SerialName("extraction_confidence") val extractionConfidence: Double,
)

@Serializable
data class ResumeUploadResponseDto(
    val id: Int,
    @SerialName("parse_status") val parseStatus: String,
    val analysis: ResumeAnalysisDto? = null,
)

@Serializable
data class ResumeSummaryDto(
    val id: Int,
    val filename: String?,
    @SerialName("parse_status") val parseStatus: String,
    @SerialName("created_at") val createdAt: String,
    // Appended rather than inserted -- 2 existing test call sites construct this
    // positionally; a defaulted trailing param keeps them compiling unchanged.
    @SerialName("file_size_bytes") val fileSizeBytes: Int? = null,
)
