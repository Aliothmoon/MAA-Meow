package com.aliothmoon.maameow.presentation.view.panel.operprogress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.OperProgressPlan
import com.aliothmoon.maameow.data.model.coreRole
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.OperAvatar

@Composable
internal fun OperProgressOperatorPicker(
    operators: List<CharacterInfo>,
    plans: List<OperProgressPlan>,
    displayName: (CharacterInfo) -> String,
    onClose: () -> Unit,
    onSelect: (CharacterInfo) -> Unit,
    modifier: Modifier,
) {
    var search by remember { mutableStateOf("") }
    val choices = remember(operators, search) {
        operators.filter { oper ->
            listOfNotNull(oper.name, oper.nameEn, oper.nameTw, oper.nameJp, oper.nameKr)
                .any { it.contains(search.trim(), ignoreCase = true) }
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.oper_progress_add), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.common_close), Modifier.size(18.dp))
            }
        }
        ITextField(value = search, onValueChange = { search = it }, placeholder = stringResource(R.string.oper_progress_search))
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (choices.isEmpty()) item {
                Text(stringResource(R.string.oper_progress_no_operators), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(choices, key = { it.id }) { oper ->
                val added = plans.any { it.name == oper.name && it.role == oper.coreRole }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !added) { onSelect(oper) }.heightIn(min = 48.dp).padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OperAvatar(oper.id, Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(displayName(oper), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("★".repeat(oper.rarity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (added) Text(stringResource(R.string.oper_progress_added), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
    }
}
