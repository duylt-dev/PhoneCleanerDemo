package com.pion.phonecleaner.feature.settings.permissioncentre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.list.SectionHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.settings.R
import com.pion.phonecleaner.feature.settings.permissioncentre.component.GrantedRow
import com.pion.phonecleaner.feature.settings.permissioncentre.component.PermissionCardItem
import com.pion.phonecleaner.feature.settings.permissioncentre.component.PermissionRationaleSheet

/**
 * `docs/screens/20-settings-language-and-push.md` §5.3.
 *
 * One keyed `LazyColumn` over a list, in place of four near-identical hand-written XML cards that
 * hide themselves. The key is the enum, which makes every item stable, and `onIntent` is passed down
 * as-is (`LLM.md` §8).
 *
 * `[AD GATE: native]` — the competitor's only ad in the whole settings tree would go here, as a final
 * `item { }` pinned last so it can never displace an ungranted card. The ad boundary is out of scope
 * (§10.3 U11) and nothing on this screen depends on the answer, so no slot is reserved.
 */
@Composable
internal fun PermissionCentreScreen(
    state: PermissionCentreState,
    onIntent: (PermissionCentreIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.settings_permissions_title),
                onBack = { onIntent(PermissionCentreIntent.BackPressed) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(ScreenGutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                if (state.isAllGranted) {
                    item(key = AllGrantedKey, contentType = EmptyType) {
                        EmptyState(
                            message = stringResource(R.string.settings_permissions_all_granted),
                        )
                    }
                }
                items(
                    items = state.missing,
                    key = { it.permission },
                    contentType = { CardType },
                ) { card ->
                    PermissionCardItem(card = card, onIntent = onIntent)
                }
                if (state.granted.isNotEmpty()) {
                    item(key = GrantedHeaderKey, contentType = HeaderType) {
                        SectionHeader(titleRes = R.string.settings_permissions_granted_section)
                    }
                    items(
                        items = state.granted,
                        key = { it.permission },
                        contentType = { GrantedType },
                    ) { card ->
                        GrantedRow(card = card)
                    }
                }
            }
        }
    }
    state.rationaleCard?.let { card ->
        PermissionRationaleSheet(card = card, onIntent = onIntent)
    }
}

private const val AllGrantedKey = "permissions.all-granted"
private const val GrantedHeaderKey = "permissions.granted-header"
private const val EmptyType = "permissions.empty"
private const val CardType = "permissions.card"
private const val HeaderType = "permissions.header"
private const val GrantedType = "permissions.granted"
