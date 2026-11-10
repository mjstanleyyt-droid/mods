# Validation and manual release checklist

## Automated checks actually run

The 26.2 port was rebuilt from clean sources on 2026-11-10; `gradle clean build` succeeded with JDK 25, Gradle 9.9.1, Loom 1.19.4 and the verified 26.2 dependencies. Twenty-nine JUnit tests passed (zero skipped, zero failures, zero errors):

* Partial supply takes only the configured deficit.
* Missing items are reported and later targets still receive available supplies.
* Full-inventory whole-stack cycles use lossless swaps.
* Blocked partial-stack transfers preserve all items.
* Matching stacks reserved for another target are not stolen.
* Unrelated items move only when required to free a destination.
* Disabling rearrangement preserves occupied incompatible destinations.
* Component-distinct stacks do not merge.
* 2,000 deterministic randomized 63-slot inventory scenarios conserve every component-key item total and respect stack capacities after every planned move.
* Initial configuration, saved kit, quantities, active UUID and settings survive a new configuration-manager instance.
* Invalid JSON is backed up before recovery.
* Unknown configuration schema is preserved in a backup.
* Kit duplication produces an independent UUID and settings.
* Invalid in-memory data cannot replace an existing kits file.

Additional fast-mode checks run for 1.0.2 (15 tests):

* Verified 2-click half-stack, 4-click quarter-stack and 3-click nearly-full-stack transfers.
* Verified partial destinations, odd counts and unequal slot limits.
* Exhaustively simulated valid transfers with stack limit 8 and tested 250 deterministic randomized vanilla-stack transfers. Every click conserved all items, respected capacities, and finished with an empty cursor and exact final counts.
* Verified invalid transfers are refused and oversized modded stacks remain safe.
* Verified zero delay defaults, validation, legacy 150 ms default migration, persistence across reload, preservation of other custom delays, and preservation of an explicitly reselected 150 ms delay.

Manual fast-mode checks remain NOT RUN: native hotbar SWAP on a live container, multi-packet server confirmation timing, nonzero delays on multiplayer, cancellation during a temporarily overfilled split, full inventories, server rejection, and item conservation during actual gameplay. Click-count tests do not establish real-world latency or inventory safety.

JUnit reports: `build/reports/tests/test/index.html`. Machine-readable reports: `build/test-results/test/`. These tests exercise planning and disk persistence, **not** Minecraft's network or GUI runtime. They do not prove no loss in multiplayer. See `docs/RUNTIME.md` for the development-client launch result.

## Manual checks — NOT RUN

All rows below are pending. Use a disposable backed-up world, Survival, cheats disabled. Obtain supplies through ordinary play. Before each scenario record all item counts and component data across inventory, equipped armor, cursor, shulker and any world drops. Compare totals afterward. Do not infer success just from the completion message.

| # | Scenario | Required observations | Status |
|---|---|---|---|
| 1 | Fresh single-player Survival | Client loads, defaults created, ordinary inventory works | NOT RUN |
| 2 | Cheats disabled | All client commands work without operator/cheats; no server commands sent | NOT RUN |
| 3 | Save a new kit | Search pearls, cobweb, golden apple, crystal, obsidian, totem, sword, chestplate, XP bottle; assign slots/quantities; save | NOT RUN |
| 4 | Exit and restart Minecraft | Kits, component templates, settings and active kit unchanged; repeat across servers | NOT RUN |
| 5 | E → Kits | Vanilla screen retained, active kit visible, list scroll/search work; recipe book and GUI scale changes do not overlap button | NOT RUN |
| 6 | Hotbar rearrangement | Pearls in slot 7 move to kit slot 3; occupied destination safely swaps or reports incomplete | NOT RUN |
| 7 | Main inventory rearrangement | Chains and cycles, partial splits, merges, component-distinct variants and extras preserved | NOT RUN |
| 8 | Full shulker | Shift + right-click opens with empty and occupied hands; synchronization precedes clicks; only deficits taken | NOT RUN |
| 9 | Partially filled shulker | All available required items used, unrelated contents stay unchanged | NOT RUN |
| 10 | Missing items | Missing target skipped; later targets complete; slot indices correctly reported | NOT RUN |
| 11 | Full player inventory | Whole-stack cycles work; blocked partial-stack moves skip; no drops | NOT RUN |
| 12 | Full shulker inventory | Armor exchange uses vacated source slot; changed source slot leaves old armor in player inventory | NOT RUN |
| 13 | Armor below 35% | Equivalent/better piece equips using close → inventory → reopen sequence; old piece returns intact | NOT RUN |
| 14 | Armor at/above 35% | No replacement at exactly threshold or above it | NOT RUN |
| 15 | Different armor enchantments | Inferior/missing enchantments rejected; custom data preserved; Binding handled without crash; curses not introduced | NOT RUN |
| 16 | Close mid-transaction | Escape stops at an empty-cursor boundary; forced close stops immediately; ordinary vanilla close recovery; no retry | NOT RUN |
| 17 | Compatible multiplayer with lag | Observe server-returned state before next click, increase delay; simulate denied clicks and external container changes | NOT RUN |
| 18 | Disconnect during restock | No further clicks or armor reopens; reconnect shows authoritative items; no duplicates/drops | NOT RUN |
| 19 | Repeated kit switching | No overlapping execution, no theft from reserved targets, extras and stack components remain intact | NOT RUN |
| 20 | Conservation audit | Count ALL physical items including cursor and drops before/after every case; zero creation/deletion | NOT RUN |

Additional checks: every toggle, exact component matching on captured custom items, keyboard shortcuts and Tab navigation, modded registry search, uninstall/reinstall a mod providing a saved item, invalid JSON recovery, read-only config filesystem error, threshold cycling, delay bounds, command alias conflict, deletion cancellation and confirmation, duplicate kit independence, no arbitrary kit count limit. Confirm a deliberately changed inventory stops the queue and all staging phases stop on interruption.

Release gate: run the full matrix on an authenticated client and at least one vanilla and one Fabric server permitting automation. Until then this is a compiled implementation with automated core tests, not a production-safety certification.
