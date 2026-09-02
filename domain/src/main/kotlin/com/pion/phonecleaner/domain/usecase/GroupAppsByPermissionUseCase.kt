package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import com.pion.phonecleaner.domain.model.permission.PermissionGroup
import com.pion.phonecleaner.domain.policy.PermissionGrouping
import kotlinx.collections.immutable.ImmutableList

/**
 * Which apps hold something in each sensitive permission group
 * (`docs/screens/17-notification-and-permissions.md` §0.3).
 *
 * A thin wrapper over [PermissionGrouping], which is a pure function and stays one: the use case exists
 * so the ViewModel resolves grouping the same way it resolves everything else — through `domainModule`
 * — while the rule itself remains a unit test with no fakes (`LLM.md` §4, §9).
 *
 * `docs/screens/17-notification-and-permissions.md:118` hangs `groupsFor` off
 * `AppPermissionScanRepository`; `docs/system-architecture.md:127` and `LLM.md` §3.3 put it in
 * `:domain/policy/`. The two binding documents win — a rule with no I/O does not belong on a port, and
 * the port as written in the shared API carries only `scanAll` and `refresh`.
 *
 * Not suspending: it is a fold over two lists the caller already holds. The competitor's equivalent is
 * thirty lines of nested loops inside a ViewModel (§0.2).
 *
 * Registered `factoryOf(::GroupAppsByPermissionUseCase)` in `domainModule`.
 */
class GroupAppsByPermissionUseCase {
    operator fun invoke(apps: List<AppPermissionReport>): ImmutableList<PermissionGroup> =
        PermissionGrouping.groupsFor(apps)
}
