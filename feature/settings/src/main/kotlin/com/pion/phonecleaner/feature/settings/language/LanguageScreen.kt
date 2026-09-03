package com.pion.phonecleaner.feature.settings.language

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.settings.R

/**
 * `docs/screens/20-settings-language-and-push.md` §2.3.
 *
 * The `RecyclerView` + `ud.b` adapter + `herbuan` item + `notifyDataSetChanged()` become one
 * `LazyColumn` with a stable `key`: the competitor repaints all seventeen rows on every tap, keyed
 * items repaint two. `onIntent` is passed down **as-is** — a lambda allocated inside `items {}` is a
 * new instance on every recomposition and defeats the skip for every row (`LLM.md` §8).
 */
@Composable
internal fun LanguageScreen(
    state: LanguageState,
    onIntent: (LanguageIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.settings_language_title),
                onBack = { onIntent(LanguageIntent.BackPressed) },
                actionLabel = stringResource(R.string.settings_language_save),
                // A disabled action is absent rather than greyed: PageHeader renders nothing when
                // onAction is null, so a Save that cannot act cannot be pressed (§2.4 delta 6).
                onAction = if (state.canSave) {
                    { onIntent(LanguageIntent.SaveTapped) }
                } else {
                    null
                },
            )
            LanguageList(state, onIntent)
        }
    }
}

@Composable
private fun LanguageList(
    state: LanguageState,
    onIntent: (LanguageIntent) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = SYSTEM_DEFAULT_KEY, contentType = RowType) {
            LanguageRow(
                label = stringResource(R.string.settings_language_system_default),
                tag = null,
                selected = state.selectedTag == null,
                onIntent = onIntent,
            )
            RowDivider()
        }
        items(
            items = state.languages,
            key = { it.tag },
            contentType = { RowType },
        ) { language ->
            LanguageRow(
                // An unlabelled tag renders as itself: a language that is offered must be
                // selectable, and "pt-PT" beats a blank row.
                label = endonymRes(language.tag)?.let { stringResource(it) } ?: language.tag,
                tag = language.tag,
                selected = language.tag == state.selectedTag,
                onIntent = onIntent,
            )
            RowDivider()
        }
    }
}

/**
 * `Modifier.selectable(role = Role.RadioButton)` on the whole row, and `onClick = null` on the
 * control. The competitor puts `android:clickable="false"` on a `RadioButton` and a listener on the
 * row — the same behaviour, announced wrongly.
 *
 * The autosizing 10→15 sp `TextView` becomes a fixed `bodyLarge` with one line and an ellipsis:
 * seventeen endonyms fit, and autosizing was solving a problem the list does not have.
 */
@Composable
private fun LanguageRow(
    label: String,
    tag: String?,
    selected: Boolean,
    onIntent: (LanguageIntent) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onIntent(LanguageIntent.LanguageSelected(tag)) },
            )
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = ScreenGutter),
        thickness = DividerThickness,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private val RowMinHeight = 56.dp
private val DividerThickness = 0.5.dp
private const val RowType = "language.row"
