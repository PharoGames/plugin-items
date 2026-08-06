# Decision & Debt Log — plugin-items

Newest first. See workspace CLAUDE.md §6 for the rules. Don't delete rows; mark `Resolved`.

---

## 2026-08-06 — A potion's `displayName` ships as CUSTOM_NAME, because ITEM_NAME never reached the client

**What.** `CustomItemManager.buildItemStack` now re-applies `displayName` as `CUSTOM_NAME` (italic forced off)
for potion materials, after the `PotionMeta` round trip that writes the contents. `ITEM_NAME` is still set and
is still what every other material uses; potions get both.

**Why.** A potion computes its own name from its contents, and that computed name outranks `ITEM_NAME` — so
`skywars.vanishing_flask` reached players as "Splash Potion of Invisibility" rather than "Vanishing Flask", and
the SkyWars kit selector, whose Magician icon is a bare `SPLASH_POTION`, read "Splash Potion of Water". Every
other material honours `ITEM_NAME`, which is why this went unnoticed until a kit was built out of a potion.
`CUSTOM_NAME` is the only name a potion cannot talk over.

**Why not switch everything to CUSTOM_NAME.** `ITEM_NAME` was chosen deliberately (2026 item pass) because it
never renders italic and cannot be stripped by an anvil rename. Both still hold for every non-potion material,
and widening the change would put an italic default on several hundred live item definitions to fix four
potions. Scoped to the materials that actually need it.

**The cosmetic cost, stated.** A potion now carries two name components. They render identically —
`CUSTOM_NAME` wins and italic is off — so nothing is visible in the tooltip, but a plugin reading `ITEM_NAME`
back off a potion stack is reading a value the client is not showing. Nothing does today.

---

## 2026-08-05 — `maxDamage` and `potion.effects`: durability and custom effects as item fields

**What.** Two optional item keys. `maxDamage: <1..32767>` sets the item's durability (`Damageable#setMaxDamage`),
and `potion.effects: [{type, durationSeconds, amplifier}]` adds custom potion effects
(`PotionMeta.addCustomEffect(effect, true)`) on top of — or instead of — the existing base `potion.type`. Both
default to absent, so every definition already on the network builds the identical stack.

**Why.** The SkyWars kit pass needed a Hunting Bow that breaks after 5 shots and a Magician invisibility potion
lasting 3:00. Neither is expressible today: durability has no field at all, and a base `PotionType` constant
carries vanilla's own duration, which for Invisibility is 3:00 or 8:00 and nothing between. This is exactly the
trigger the 2026-07-30 entry below named for adding `potion.effects`, so that entry is now **Resolved** — the
speculative-surface argument stopped applying the moment a real config needed a duration vanilla has no
constant for.

**The splash arithmetic, which callers get wrong — including the first version of this entry.** A SPLASH potion
applies the **full** configured duration, to an entity it hits directly and to anyone at the impact point, so
the 3:00 Magician potion is `durationSeconds: 180`. The widely-repeated "splash potions last 3/4 as long" rule
was removed in **15w31a** and has not been true since 1.9; this change shipped its first draft with 240 and a
0.75x table, which would have handed the Magician 4:00. What genuinely scales a splash is *distance* — duration
falls off linearly to nothing at 4 blocks — which is invisible to anyone reading only the YAML. Lingering is
0.25x per touch and tipped arrows 0.125x on hit. Documented as a table in `docs/configuration.md`, with the
15w31a note attached, so the next author does not re-derive the pre-1.9 rule from memory the way this one did.

**Why the validators take injected lookups.** `Material.getMaxDurability()` and `PotionEffectType` resolution
are both registry-backed on purpur-api 1.21.11 — headless they throw `IllegalStateException: No RegistryAccess
implementation found`, so neither can be reached from a unit test. Rather than hardcode a list of damageable
materials and vanilla effect names (which drifts silently the next time Mojang adds one), the two validators
take a `ToIntFunction<String>` / `Predicate<String>` and production passes the real registry. Tests stay pure
and the runtime stays accurate.

**Why `Damageable#setMaxDamage` and not `DataComponentTypes.MAX_DAMAGE`.** Both exist on this API (confirmed by
`javap` against the pinned `purpur-api-1.21.11-R0.1-SNAPSHOT` jar). The meta route is not `@Experimental`, and it
self-guards on the meta type the way the neighbouring potion code already does.

**Shipped alongside: nested `potion` keys now WARN too.** `unknownKeys` only ever sees one section's direct
children, so `effect:` for `effects:` would have been swallowed exactly the way top-level keys were before the
2026-07-30 fix. The `potion` section now has its own known-key set and its own WARN.

**Blast radius (Tier 2, additive).** `buildItemStack` is HIGH-risk by `gitnexus_impact` — every item on every
server type flows through it — but both new branches are gated on a null/empty field, so an existing definition
takes a byte-identical path. No d=1 dependent changed; the builder additions are new optional methods.

**Deploy order.** JAR to R2 → base image rebuild → game image rebuild → *then* the `server-image-skywars` Items
config that uses the keys. A config that arrives first is safe but degraded: the older jar builds the item
WITHOUT the field — a vanilla-durability bow and a bottle carrying only its base potion type.

**Only `maxDamage` is greppable during that window.** It is a top-level item key, so the older jar's existing
`unknownKeys(s.getKeys(false))` check WARNs on it and `logs_errors` finds it. `potion.effects` does not WARN
on an older jar: `potion` is already in that jar's `KNOWN_KEYS`, and the nested `KNOWN_POTION_KEYS` check ships
in *this* commit — so an older jar reads `potion.type`, ignores `effects`, and logs nothing at all. Treating a
clean log as proof that both keys landed is exactly backwards for the flask: `skywars.vanishing_flask` silently
becomes a base-type INVISIBILITY splash until the base image carrying this jar reaches the pods. The only
reliable check during the window is the jar version on the pod, not the log.

## 2026-08-02 — `vanillaStack` lets a definition opt out of its own identity

**What.** New optional `vanillaStack: true`. The item is built with no PDC at all — no `logical_id`, no
`locked`/`droppable`/`movable` — so it is byte-identical to the vanilla material and merges with vanilla
copies of it. Default false, so every definition on the network builds exactly as before.

**Why.** The PDC lands in the `minecraft:custom_data` component and `ItemStack.isSimilar` compares
components, so an item this plugin builds can never stack with the same item from anywhere else. SkyWars
kits hand out steak, arrows, planks, pearls and TNT through definitions that are *only* a material — they
exist so a `KitItemEntry.logicalId` has something to name. A player left the cage with 2 kit steak, looted 3
more from a chest, and held two stacks that would not combine. Reported from a live match.

**Why here and not in the loot table.** The alternative was pointing the SkyWars loot entries at
`items:cooked_beef` so both sources produce the tagged form. That makes the *chest* the odd one out instead:
vanilla is what crafting outputs, what death drops preserve, and what every untagged entry in the pool
already is, so tagging more of the pool spreads the mismatch rather than removing it. The mismatch is
created here, at the point identity is stamped, so it is fixed here.

**Why opt-in rather than automatic.** "Definition has no customisation → skip the PDC" would fix every
gamemode at once and break several: HG, HG-event and Survival Islands match crafting ingredients by logical
id, including plain ones, so silently un-tagging them would take out custom crafting network-wide. The flag
makes each config say whether that item's identity is load-bearing.

**Guard rails.** `locked` / `droppable: false` / `movable: false` alongside `vanillaStack` is **rejected at
load** — those flags live in the PDC it strips, so honouring both would hand out an item whose lock is
decoration. The same request arriving through `GiveOptions` at give-time can't be rejected without failing a
give, so it WARNs and is ignored.

**Blast radius.** Additive: a new config key (added to `KNOWN_KEYS`, so it doesn't trip the unrecognised-key
WARN) and a new builder field. No existing definition changes shape. Deploy this JAR before the
`server-image-skywars` Items config that uses it — an older jar reads the key as unrecognised, WARNs, and
hands out the tagged item, which is today's behaviour.

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

**Resolved 2026-08-05** — that trigger fired (a 3:00 Invisibility splash potion for the SkyWars Magician kit)
and `potion.effects` shipped in exactly the shape sketched above. See the 2026-08-05 entry.

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
