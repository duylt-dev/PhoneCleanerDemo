package com.pion.phonecleaner.feature.onboarding.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.onboarding.R

/**
 * `posparce.xml`'s `covular` row and its five children (`docs/screens/10-splash-and-onboarding.md`
 * §1.3): a real `Checkbox`, so the state is announced to accessibility services, and one sentence
 * carrying two links.
 *
 * The sentence is **one** resource with two placeholders, not four strings joined at runtime.
 * `VibrnancActivity.java:466` concatenates its equivalent and ships full-width CJK parentheses into
 * all 17 locales; a format string lets a translator move the link text inside the sentence.
 *
 * Both offsets are located in the string this function itself formatted, so `indexOf` cannot drift.
 * A translation that drops a placeholder yields `-1` and simply loses that link — never a crash.
 */
@Composable
internal fun ConsentRow(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val terms = stringResource(R.string.onboarding_consent_terms)
    val privacy = stringResource(R.string.onboarding_consent_privacy)
    val body = stringResource(R.string.onboarding_consent_body, terms, privacy)
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val text: AnnotatedString = buildAnnotatedString {
        append(body)
        linkAt(body, terms, linkStyles, onTermsClick)
        linkAt(body, privacy, linkStyles, onPrivacyClick)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = accepted, onCheckedChange = onAcceptedChange)
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.linkAt(
    body: String,
    label: String,
    styles: TextLinkStyles,
    onClick: () -> Unit,
) {
    val start = body.indexOf(label)
    if (start < 0 || label.isEmpty()) return
    addLink(
        clickable = LinkAnnotation.Clickable(tag = label, styles = styles) { onClick() },
        start = start,
        end = start + label.length,
    )
}
