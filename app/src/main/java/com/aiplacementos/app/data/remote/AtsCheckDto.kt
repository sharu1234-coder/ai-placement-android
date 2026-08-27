package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateAtsCheckRequestDto(
    @SerialName("target_role") val targetRole: String? = null,
)

@Serializable
data class CategoryBreakdownDto(
    val category: String,
    val score: Int,
    val summary: String,
    val strengths: List<String>,
    val improvements: List<String>,
    @SerialName("how_to_improve") val howToImprove: List<String>,
)

@Serializable
data class SectionAnalysisDto(
    val section: String,
    val status: String,
    val content: String? = null,
)

@Serializable
data class AtsIssueDto(
    val category: String,
    val severity: String,
    val title: String,
    val description: String,
    @SerialName("how_to_fix") val howToFix: String,
)

@Serializable
data class AtsRecommendationDto(
    val rank: Int,
    val category: String,
    val title: String,
    val priority: String,
    val rationale: String,
    @SerialName("fix_target") val fixTarget: String? = null,
)

@Serializable
data class AtsCheckReportDto(
    val id: Int,
    @SerialName("resume_id") val resumeId: Int,
    @SerialName("target_role") val targetRole: String? = null,
    @SerialName("overall_score") val overallScore: Int,
    @SerialName("score_category") val scoreCategory: String,
    @SerialName("category_breakdown") val categoryBreakdown: List<CategoryBreakdownDto>,
    @SerialName("section_analysis") val sectionAnalysis: List<SectionAnalysisDto>,
    val issues: List<AtsIssueDto>,
    val recommendations: List<AtsRecommendationDto>,
    @SerialName("potential_score") val potentialScore: Int? = null,
    @SerialName("potential_score_explanation") val potentialScoreExplanation: String? = null,
    @SerialName("ai_summary") val aiSummary: String,
    @SerialName("raw_text") val rawText: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AtsCheckListItemDto(
    val id: Int,
    @SerialName("resume_id") val resumeId: Int,
    @SerialName("resume_filename") val resumeFilename: String? = null,
    @SerialName("overall_score") val overallScore: Int,
    @SerialName("score_category") val scoreCategory: String,
    @SerialName("created_at") val createdAt: String,
)
