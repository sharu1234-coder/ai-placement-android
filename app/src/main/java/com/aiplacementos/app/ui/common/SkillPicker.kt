package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.ui.theme.Spacing

/** Domain-grouped suggested skills, shared by onboarding and Profile's Skills section --
 * "Databases" is already its own category here, so a separate backend "tools" field isn't
 * needed to satisfy a tools/databases split. */
val SUGGESTED_SKILLS: List<Pair<String, List<String>>> = listOf(
    "Programming" to listOf("Python", "Java", "C", "C++", "JavaScript", "TypeScript", "Kotlin", "Go"),
    "Computer Science" to listOf(
        "Data Structures & Algorithms", "OOP", "DBMS", "Operating Systems", "Computer Networks", "System Design",
    ),
    "Databases" to listOf("SQL", "MySQL", "PostgreSQL", "MongoDB", "Redis"),
    "Data" to listOf("Data Science", "Machine Learning", "Deep Learning", "Statistics", "Data Analytics"),
    "Web" to listOf("HTML", "CSS", "React", "Node.js", "Django", "Spring Boot"),
    "Cloud & DevOps" to listOf("AWS", "Azure", "GCP", "Docker", "Kubernetes", "Git"),
    "Security" to listOf("Cyber Security", "Network Security", "Ethical Hacking"),
    "Testing" to listOf("Manual Testing", "Selenium", "API Testing", "Automation Testing"),
)

/** Stateless picker over the student's current skill list -- add/remove is entirely
 * caller-driven via [onToggle] (both onboarding and Profile back it with the same
 * OnboardingSkillsViewModel, which already implements toggle-by-name add/delete). Only the
 * custom-skill text field's draft value is local UI state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillPicker(skills: List<SkillDto>, busy: Boolean, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    var customSkill by rememberSaveable { mutableStateOf("") }
    val selectedNames = skills.map { it.name.lowercase() }.toSet()
    val suggestedNamesLower = SUGGESTED_SKILLS.flatMap { it.second }.map { it.lowercase() }.toSet()
    val customAdded = skills.filter { it.name.lowercase() !in suggestedNamesLower }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SUGGESTED_SKILLS.forEach { (category, categorySkills) ->
            Column {
                Text(text = category, style = MaterialTheme.typography.titleSmall)
                // chipGap (not xs) between these -- see Spacing.chipGap's own doc comment.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                ) {
                    categorySkills.forEach { skill ->
                        FilterChip(
                            selected = skill.lowercase() in selectedNames,
                            onClick = { onToggle(skill) },
                            enabled = !busy,
                            label = { Text(skill) },
                        )
                    }
                }
            }
        }
        if (customAdded.isNotEmpty()) {
            Column {
                Text(text = "Your custom skills", style = MaterialTheme.typography.titleSmall)
                // chipGap (not xs) between these -- see Spacing.chipGap's own doc comment.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                ) {
                    customAdded.forEach { skill ->
                        FilterChip(
                            selected = true,
                            onClick = { onToggle(skill.name) },
                            enabled = !busy,
                            label = { Text(skill.name) },
                        )
                    }
                }
            }
        }
        Column {
            OutlinedTextField(
                value = customSkill,
                onValueChange = { customSkill = it },
                label = { Text("Add a custom skill") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = "Add Custom Skill",
                onClick = {
                    if (customSkill.isNotBlank()) {
                        onToggle(customSkill.trim())
                        customSkill = ""
                    }
                },
                enabled = !busy && customSkill.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
        }
    }
}
