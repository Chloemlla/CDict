package com.chloemlla.cdict.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 背词 / 推荐共用的取样范围：课程标签 + 雅思频率组。null 表示「不限制」。 */
data class StudyScope(
    val curriculumTag: String? = null,
    val frequencyGroup: Int? = null,
)

internal const val SCOPE_ALL_GROUPS_LABEL = "全部组"
private const val SCOPE_ALL_WORDS_LABEL = "全部词表"
private val SCOPE_FREQUENCY_OPTIONS: List<Pair<String, Int?>> = listOf(
    SCOPE_ALL_GROUPS_LABEL to null,
    "组 1" to 1,
    "组 2" to 2,
    "组 3" to 3,
    "组 4" to 4,
    "组 5" to 5,
    "组 6" to 6,
    "组 7" to 7,
)

/**
 * 背词 / 推荐页顶部的一排取样范围筛选：课程标签下拉 + 雅思频率组下拉。
 * 与词典页的筛选交互保持一致（下拉选择），选择由各自 ViewModel 持久化。
 */
@Composable
fun ScopeFilterRow(
    scope: StudyScope,
    availableCurriculumTags: List<String>,
    onScopeChange: (StudyScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val curriculumOptions: List<Pair<String, String?>> = remember(availableCurriculumTags) {
        buildList {
            add(SCOPE_ALL_WORDS_LABEL to null)
            availableCurriculumTags.forEach { add(it to it) }
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScopeDropdown(
            label = scope.curriculumTag ?: SCOPE_ALL_WORDS_LABEL,
            options = curriculumOptions,
            selected = scope.curriculumTag,
            onSelect = { tag -> onScopeChange(scope.copy(curriculumTag = tag)) },
            active = scope.curriculumTag != null,
            modifier = Modifier.weight(1f),
        )
        ScopeDropdown(
            label = scope.frequencyGroup?.let { "组 $it" } ?: SCOPE_ALL_GROUPS_LABEL,
            options = SCOPE_FREQUENCY_OPTIONS,
            selected = scope.frequencyGroup,
            onSelect = { group ->
                onScopeChange(scope.copy(frequencyGroup = group))
            },
            active = scope.frequencyGroup != null,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 通用下拉：一项选中项 + 下拉选项表；选中项以勾选标记（与词典页 FilterDropdownMenu 同一交互）。 */
@Composable
private fun <T> ScopeDropdown(
    label: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val filterState = if (active) "已筛选，当前为 $label" else "未筛选，当前为 $label"
    val triggerModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .semantics {
            contentDescription = "范围筛选：$label"
            role = Role.Button
            stateDescription = filterState
        }
    Box(modifier = modifier) {
        val trigger: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (active) {
            FilledTonalButton(
                onClick = { expanded = true },
                modifier = triggerModifier,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { trigger() }
        } else {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = triggerModifier,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { trigger() }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (optionLabel, option) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                )
            }
        }
    }
}