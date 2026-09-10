package com.pion.phonecleaner.data.junk

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants of the shipping catalogue, not its contents.
 *
 * A test that asserted "there are twelve system-cache rules" would fail every time someone adds a
 * rule, which is the one thing this file is meant to make easy. What must never drift is the SHAPE:
 * `resolveUnder` joins a rule fragment onto a root with `File(root, fragment)`, so a fragment with a
 * leading slash silently becomes an absolute path and the rule then points at `/cache` on the device
 * root instead of `/storage/emulated/0/cache`. `SystemCacheRule`'s own KDoc states the contract —
 * "No leading or trailing separator" — and nothing enforced it until here.
 *
 * The empty-catalogue check is the regression guard for what this file replaced: `EmptyJunkRuleCatalog`
 * returned nothing from both methods, so the scan reported 0 B on every device and the screen said
 * "scanned, found nothing". An accidental return to that state must break a test, not ship.
 */
internal class KotlinJunkRuleCatalogTest {

    private val catalog = KotlinJunkRuleCatalog()

    @Test
    fun `both rule sets are non-empty`() = runTest {
        assertTrue(catalog.systemCacheRules().isNotEmpty())
        assertTrue(catalog.appRules().isNotEmpty())
    }

    @Test
    fun `system cache rule ids are unique`() = runTest {
        val ids = catalog.systemCacheRules().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `no rule fragment is anchored to the device root`() = runTest {
        catalog.systemCacheRules().forEach { rule ->
            assertTrue(
                "system-cache rule '${rule.id}' must be relative to a storage root: ${rule.path}",
                !rule.path.startsWith("/") && !rule.path.endsWith("/"),
            )
        }
        catalog.appRules().flatMap { it.roots }.forEach { root ->
            assertTrue(
                "app-rule root must be relative to a storage root: ${root.path}",
                !root.path.startsWith("/") && !root.path.endsWith("/"),
            )
            root.subPaths.forEach { sub ->
                assertTrue(
                    "app-rule sub-path must be relative to its root: ${sub.path}",
                    !sub.path.startsWith("/") && !sub.path.endsWith("/"),
                )
            }
        }
    }

    @Test
    fun `every app rule names a package and at least one root`() = runTest {
        catalog.appRules().forEach { rule ->
            assertTrue(rule.packageName.isNotBlank())
            assertTrue("`label` is what makes a row legible (Delta R4)", rule.label.isNotBlank())
            assertTrue("a rule with no root can never fire", rule.roots.isNotEmpty())
        }
    }

    /**
     * `scoped` marks the roots under `Android/data` and `Android/obb` that stay unreadable on API 30+
     * even holding `MANAGE_EXTERNAL_STORAGE`. A root carrying such a prefix and `scoped = false` would
     * be walked, find nothing, and report a clean result for a directory nobody looked in.
     */
    @Test
    fun `a root under Android data or obb is marked scoped`() = runTest {
        catalog.appRules().flatMap { it.roots }.forEach { root ->
            val unreadable = root.path.startsWith("Android/data") || root.path.startsWith("Android/obb")
            if (unreadable) {
                assertTrue("'${root.path}' is unreadable on API 30+ and must be scoped", root.scoped)
            }
        }
    }
}
