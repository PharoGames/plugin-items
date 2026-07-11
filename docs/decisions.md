# Decision & Debt Log — plugin-items

Newest first. See workspace CLAUDE.md §6 for the rules. Don't delete rows; mark `Resolved`.

---

## 2026-07-11 — Functional-item dispatch honours a prior item-use DENY

**What.** `InteractionManager.onPlayerInteract` now returns early when
`event.useItemInHand() == Event.Result.DENY`, before dispatching any registered functional-item handler. A
functional item (legendary, GUI opener, kit tool) no longer fires when a higher-priority listener already
denied the item-in-hand action.

**Why.** `plugin-loot` needed to suppress the held item that a loot-chest right-click wrongly triggers: a
right-click on the chest's Interaction entity makes the vanilla client fall through to `USE_ITEM`, so opening a
chest also fired the held item. `plugin-loot` DENYs that fall-through at `LOWEST`; vanilla food/pearls honour
the DENY, but a legendary bound through `plugin-items` (dispatched here at `NORMAL`, `ignoreCancelled=false`,
with no prior state check) did not. Honouring a prior DENY closes that gap and matches the intent — a denied
item action should not run the item's function.

**Blast radius (network-wide, Tier 2).** This handler runs on every server with `plugin-items` (lobby + all
games). Reviewed every listener that DENYs or cancels `PlayerInteractEvent` at priority ≤ `NORMAL`: each is
either item-gated to its own logical ID (ConsumableAbilities, KillTokenManager, ShieldDisabler shields-only,
dragonsurvival) or state-gated to freeze/vanish/spectator. None suppresses a foreign functional item that
should fire, with one narrow staff-only exception (a vanish/spectator handler that DENYs item-use would also
stop a staff functional item on that click). `PlayerInteractEvent` constructs `useItemInHand` as `DEFAULT`
(never `DENY`) for all actions, so normal air/block right-clicks on functional items are unaffected.

**Cross-repo deploy order.** Ship this JAR to R2 with (or before) `plugin-loot`; both reach the three game
images via CI auto-fanout. No API change — behaviour only.
