package com.pion.phonecleaner.feature.onboarding.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The brand mark shared by `splash` and `appresume` — `posparce.xml`'s `demoillin` icon plus its
 * `app_name` `TextView` (`docs/screens/10-splash-and-onboarding.md` §1.3).
 *
 * The label is read from the installed application's own manifest rather than from a string in this
 * module: the app name is declared once, in `:app/res/values/strings.xml`, and a second copy here
 * would be a second name to translate and to forget.
 *
 * UNKNOWN — the brand image. `posparce.xml` draws `@drawable/demoillin` above the label, and no
 * equivalent asset exists in the project: `:core:ui/src/main/res` holds `values/strings.xml` and
 * nothing else, and `:app`'s launcher mipmap is not visible from a feature module (`LLM.md` §2). The
 * block therefore renders the label alone. **The asset belongs in `:core:ui/src/main/res/drawable/`**
 * so that both onboarding and any later brand surface read one copy; drawing an invented mark here
 * would be a brand decision taken by the wrong file.
 */
@Composable
internal fun BrandBlock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val label = remember(context) {
        context.applicationInfo.loadLabel(context.packageManager).toString()
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
