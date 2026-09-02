package com.pion.phonecleaner.core.ui.component.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The bottom half of the dialog API. It exists because three of the competitor's eighteen presenters
 * override `oc.a.a()` to move the window to `Gravity.BOTTOM` — a real distinction, expressed as a
 * second component rather than as a parameter on [AppDialog] (`docs/screens/21` §3.1).
 *
 * Everything [AppDialog]'s documentation says about **visibility living on `State`** applies here
 * unchanged: `state.isNotificationOptInVisible`, `state.pendingUnlockPackage`,
 * `state.isPermissionSheetVisible`.
 *
 * [dismissible] closes all three exits at once — the drag handle disappears, the swipe is refused,
 * and the scrim tap and back press are swallowed. `ModalBottomSheet` routes both of the last two
 * through [onDismissRequest], so guarding it there is the whole of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> dismissible || target != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = { if (dismissible) onDismissRequest() },
        modifier = modifier,
        sheetState = sheetState,
        dragHandle = if (dismissible) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
    ) {
        Column(
            Modifier.padding(horizontal = ScreenGutter).padding(bottom = Spacing.xxl),
            content = content,
        )
    }
}
