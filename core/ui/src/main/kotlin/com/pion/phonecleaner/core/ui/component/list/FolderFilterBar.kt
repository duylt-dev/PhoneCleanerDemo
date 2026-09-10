package com.pion.phonecleaner.core.ui.component.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.pion.phonecleaner.core.ui.R
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing

data class FolderFilterTab(
    val key: String?,
    val label: String,
    val count: Int,
)

data class SortMenuItem<T>(
    val value: T,
    val label: String,
)

@Composable
fun <T> FolderFilterBar(
    tabs: List<FolderFilterTab>,
    selectedKey: String?,
    sort: T,
    sortItems: List<SortMenuItem<T>>,
    onFolderSelected: (String?) -> Unit,
    onSortSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ScreenGutter, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            tabs.forEach { tab ->
                val onClick = remember(tab.key, onFolderSelected) { { onFolderSelected(tab.key) } }
                FilterChip(
                    selected = tab.key == selectedKey,
                    onClick = onClick,
                    label = {
                        Text(
                            text = "${tab.label} ${tab.count}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.action_sort))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            sortItems.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        menuOpen = false
                        onSortSelected(item.value)
                    },
                    trailingIcon = {
                        if (item.value == sort) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                )
            }
        }
    }
}
