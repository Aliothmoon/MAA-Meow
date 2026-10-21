package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.OperAvatar
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup
import com.aliothmoon.maameow.presentation.components.inputFocusGroup
import com.aliothmoon.maameow.presentation.view.panel.common.bringIntoViewOnExpand
import sh.calvin.reorderable.ReorderableCollectionItemScope

/** 与战斗列表共用拖拽状态，靠前缀区分 */
internal const val USER_ADDITIONAL_KEY_PREFIX = "user_additional:"

private const val SUGGESTION_LIMIT = 8

@Composable
internal fun ReorderableCollectionItemScope.UserAdditionalRow(
    operId: String,
    displayName: String,
    skill: Int,
    isDragging: Boolean,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    onSkillChange: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shadowElevation = if (isDragging) 4.dp else 0.dp,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = stringResource(R.string.panel_autobattle_drag_handle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .draggableHandle(
                        onDragStarted = { onDragStarted() },
                        onDragStopped = onDragStopped,
                    )
                    .padding(6.dp)
            )
            OperAvatar(operId = operId, modifier = Modifier.size(36.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.panel_autobattle_user_additional_skill),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val defaultSkill = stringResource(R.string.panel_autobattle_user_additional_skill_default)
                    SelectableChipGroup(
                        label = "",
                        selectedValue = skill.coerceIn(0, 3),
                        options = (0..3).map { it to if (it == 0) defaultSkill else it.toString() },
                        onSelected = onSkillChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 只能从候选里选，名字打错 core 会让整个任务提交失败 */
@Composable
internal fun UserAdditionalSearch(
    added: Set<String>,
    clientType: String,
    displayName: (CharacterInfo) -> String,
    onAdd: (CharacterInfo) -> Unit,
    resourceDataManager: ResourceDataManager,
) {
    val operators by resourceDataManager.operators.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val keyword = query.trim()
    val suggestions = remember(keyword, operators, added, clientType) {
        if (keyword.isEmpty()) return@remember emptyList()
        fun CharacterInfo.names() = listOfNotNull(name, nameEn, nameTw, nameJp, nameKr)
        operators.values
            .filter { oper ->
                oper.name !in added &&
                    resourceDataManager.isCharacterAvailableInClient(oper, clientType) &&
                    oper.names().any { it.contains(keyword, ignoreCase = true) }
            }
            // core 只按名字取干员，同名变体（如三个阿米娅）只留一个
            .distinctBy { it.name }
            .sortedWith(
                compareByDescending<CharacterInfo> { oper ->
                    oper.names().any { it.equals(keyword, ignoreCase = true) }
                }.thenByDescending { it.rarity }
            )
            .take(SUGGESTION_LIMIT)
    }

    // 悬浮窗点候选不清焦，可以连着加
    Column(
        modifier = Modifier.inputFocusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ITextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.panel_autobattle_user_additional_search),
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_clear),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                null
            }
        )
        if (keyword.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnExpand(true)
            ) {
                if (suggestions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.panel_autobattle_user_additional_no_match),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                } else {
                    Column {
                        suggestions.forEach { oper ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAdd(oper)
                                        query = ""
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OperAvatar(operId = oper.id, modifier = Modifier.size(28.dp))
                                Text(
                                    text = displayName(oper),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
