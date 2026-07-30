# Decision & Debt Log — plugin-items

Newest first. See workspace CLAUDE.md §6 for the rules. Don't delete rows; mark `Resolved`.

---

## 2026-07-30 — Potions carry a base type only, no custom effect list

**What.** New optional `potion.type` field maps a Bukkit `PotionType` constant onto `PotionMeta`. There is
deliberately **no** `effects:` list and no duration/amplifier field: a duration is chosen by picking the
constant that carries it (`FIRE_RESISTANCE` 3:00 vs `LONG_FIRE_RESISTANCE` 8:00).

**Why.** SkyWars loot needed vanilla Fire Resistance (3:00) and Regeneration (0:45) in its chest pool, and a
loot entry (`MATERIAL:amount:tier`) has no meta field — the registry is the only place that can hold it. Both
asks are exactly base types, so a custom-effect builder would have been speculative surface with two ways to
express the same potion and no test coverage for the second.

**If it turns out wrong.** The first request for a duration vanilla has no constant for (e.g. 2:00 Fire
Resistance) is the trigger to add `potion.effects: [{type, durationSeconds, amplifier}]` alongside `type`,
applying `PotionMeta.addCustomEffect`. Additive; nothing configured today changes shape.

**Shipped alongside: unrecognised item keys now WARN.** Adding the first new item field in a while exposed
that an unknown key was dropped in total silence — so a `potion:` block delivered to a pod running an older
jar produced an effectless bottle with nothing in the logs, and the same hole swallows a plain typo
(`enchantment:` for `enchantments:`). `ItemConfigLoader` now logs the offending item and key list.

WARN, not throw, deliberately: this plugin is baked into the shared base image while its config ships through
config-service, so config-ahead-of-jar is a normal few minutes of any rollout. Aborting start would convert
that window into a network-wide crashloop over a field the item can live without. The trade-off is that a
skewed pod still hands out the degraded item — the WARN is what makes it findable. If that proves too weak,
the escalation is a per-key severity (throw for keys that change what the item IS, warn for cosmetic ones).

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
