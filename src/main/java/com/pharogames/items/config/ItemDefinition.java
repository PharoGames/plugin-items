package com.pharogames.items.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable definition of a custom item loaded from config.
 * All fields except logicalId and material are optional.
 */
public class ItemDefinition {

    private final String logicalId;
    private final String material;

    // Presentation
    private final String displayName;
    private final List<String> lore;

    // Model
    private final String itemModel;
    private final CustomModelDataDef customModelData;

    // Data components
    private final Boolean enchantmentGlint;
    private final String rarity;
    private final Integer maxStackSize;
    /**
     * When non-null, overrides the item type's durability (MAX_DAMAGE). A BOW with {@code 5} breaks
     * after five shots. Null on every item that keeps its vanilla durability.
     */
    private final Integer maxDamage;
    private final boolean unbreakable;
    private final Map<String, Integer> enchantments;
    private final boolean hideTooltip;
    private final boolean hideAdditionalTooltip;

    // Inventory behaviour (defaults -- can be overridden by GiveOptions)
    private final int slot;
    private final boolean locked;
    private final boolean droppable;
    private final boolean movable;

    /**
     * When true the built stack carries NO PDC identity (no logical id, no locked/droppable/movable
     * flags), so it is byte-identical to the vanilla item and stacks with vanilla copies of the same
     * material. For definitions that exist only to give a kit a name for a plain vanilla ingredient:
     * without this, kit steak and chest steak are two different stacks that never merge.
     *
     * <p>The trade-off is the whole point of the flag: a vanillaStack item cannot be identified by
     * {@link com.pharogames.items.api.ItemsAPI#getLogicalId}, cannot be locked / undroppable /
     * unmovable, and cannot carry an interact handler. Enforced in {@link Builder#build()}.
     */
    private final boolean vanillaStack;

    // Arbitrary metadata for plugin-specific use
    private final Map<String, Object> metadata;

    /** When non-null, overrides the item type's {@code minecraft:food} component (1.21+). */
    private final FoodDef food;

    /**
     * Bukkit {@code PotionType} constant name (e.g. {@code FIRE_RESISTANCE}) for a POTION /
     * SPLASH_POTION / LINGERING_POTION / TIPPED_ARROW stack, or null for every other item.
     * The type carries vanilla's effect AND its vanilla duration, so a duration is chosen by
     * picking the right constant ({@code FIRE_RESISTANCE} = 3:00, {@code LONG_FIRE_RESISTANCE}
     * = 8:00) rather than by a separate field.
     */
    private final String potionType;

    /**
     * Custom potion effects layered on top of {@link #potionType}, empty for every other item.
     * Each entry names a Bukkit {@code PotionEffectType} plus an explicit duration, which is how a
     * potion expresses a duration vanilla has no {@code PotionType} constant for.
     */
    private final List<PotionEffectDef> potionEffects;

    private ItemDefinition(Builder builder) {
        this.logicalId = builder.logicalId;
        this.material = builder.material;
        this.displayName = builder.displayName;
        this.lore = List.copyOf(builder.lore);
        this.itemModel = builder.itemModel;
        this.customModelData = builder.customModelData;
        this.enchantmentGlint = builder.enchantmentGlint;
        this.rarity = builder.rarity;
        this.maxStackSize = builder.maxStackSize;
        this.maxDamage = builder.maxDamage;
        this.unbreakable = builder.unbreakable;
        this.enchantments = Map.copyOf(builder.enchantments);
        this.hideTooltip = builder.hideTooltip;
        this.hideAdditionalTooltip = builder.hideAdditionalTooltip;
        this.slot = builder.slot;
        this.locked = builder.locked;
        this.droppable = builder.droppable;
        this.movable = builder.movable;
        this.vanillaStack = builder.vanillaStack;
        this.metadata = Map.copyOf(builder.metadata);
        this.food = builder.food;
        this.potionType = builder.potionType;
        this.potionEffects = List.copyOf(builder.potionEffects);
    }

    public String getLogicalId() { return logicalId; }
    public String getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public String getItemModel() { return itemModel; }
    public CustomModelDataDef getCustomModelData() { return customModelData; }
    public Boolean getEnchantmentGlint() { return enchantmentGlint; }
    public String getRarity() { return rarity; }
    public Integer getMaxStackSize() { return maxStackSize; }
    public Integer getMaxDamage() { return maxDamage; }
    public boolean isUnbreakable() { return unbreakable; }
    public Map<String, Integer> getEnchantments() { return enchantments; }
    public boolean isHideTooltip() { return hideTooltip; }
    public boolean isHideAdditionalTooltip() { return hideAdditionalTooltip; }
    public int getSlot() { return slot; }
    public boolean isLocked() { return locked; }
    public boolean isDroppable() { return droppable; }
    public boolean isMovable() { return movable; }
    public boolean isVanillaStack() { return vanillaStack; }
    public Map<String, Object> getMetadata() { return metadata; }
    public FoodDef getFood() { return food; }
    public String getPotionType() { return potionType; }
    /** Never null; empty for every item that configures no custom effects. */
    public List<PotionEffectDef> getPotionEffects() { return potionEffects; }

    public static Builder builder(String logicalId, String material) {
        return new Builder(logicalId, material);
    }

    public static final class Builder {
        private final String logicalId;
        private final String material;

        private String displayName = null;
        private List<String> lore = new ArrayList<>();
        private String itemModel = null;
        private CustomModelDataDef customModelData = null;
        private Boolean enchantmentGlint = null;
        private String rarity = null;
        private Integer maxStackSize = null;
        private Integer maxDamage = null;
        private boolean unbreakable = false;
        private Map<String, Integer> enchantments = new HashMap<>();
        private boolean hideTooltip = false;
        private boolean hideAdditionalTooltip = false;
        private int slot = -1;
        private boolean locked = false;
        private boolean droppable = true;
        private boolean movable = true;
        private boolean vanillaStack = false;
        private Map<String, Object> metadata = new HashMap<>();
        private FoodDef food = null;
        private String potionType = null;
        private List<PotionEffectDef> potionEffects = new ArrayList<>();

        private Builder(String logicalId, String material) {
            this.logicalId = logicalId;
            this.material = material;
        }

        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder lore(List<String> lore) { this.lore = lore; return this; }
        public Builder itemModel(String itemModel) { this.itemModel = itemModel; return this; }
        public Builder customModelData(CustomModelDataDef customModelData) { this.customModelData = customModelData; return this; }
        public Builder enchantmentGlint(Boolean glint) { this.enchantmentGlint = glint; return this; }
        public Builder rarity(String rarity) { this.rarity = rarity; return this; }
        public Builder maxStackSize(Integer maxStackSize) { this.maxStackSize = maxStackSize; return this; }
        /**
         * Overrides the item type's durability (MAX_DAMAGE). Only meaningful on a damageable
         * material; config-loaded definitions are rejected at load otherwise, runtime-registered
         * ones warn and keep vanilla durability.
         */
        public Builder maxDamage(Integer maxDamage) { this.maxDamage = maxDamage; return this; }
        public Builder unbreakable(boolean unbreakable) { this.unbreakable = unbreakable; return this; }
        public Builder enchantments(Map<String, Integer> enchantments) { this.enchantments = enchantments; return this; }
        public Builder hideTooltip(boolean hideTooltip) { this.hideTooltip = hideTooltip; return this; }
        public Builder hideAdditionalTooltip(boolean hide) { this.hideAdditionalTooltip = hide; return this; }
        public Builder slot(int slot) { this.slot = slot; return this; }
        public Builder locked(boolean locked) { this.locked = locked; return this; }
        public Builder droppable(boolean droppable) { this.droppable = droppable; return this; }
        public Builder movable(boolean movable) { this.movable = movable; return this; }
        /**
         * Builds a bare vanilla stack with no PDC identity, so it merges with vanilla items of the
         * same material. Mutually exclusive with the inventory-behaviour flags, which are stored in
         * that PDC — see {@link ItemDefinition#isVanillaStack()}.
         */
        public Builder vanillaStack(boolean vanillaStack) { this.vanillaStack = vanillaStack; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        /**
         * Sets the {@code minecraft:food} data component, overriding defaults for this item type
         * (e.g. steak appearance with custom nutrition / saturation).
         */
        public Builder food(FoodDef food) { this.food = food; return this; }
        /**
         * Sets the base potion type by Bukkit {@code PotionType} constant name. Only meaningful on
         * a potion-shaped material; other materials carry no PotionMeta and the value is ignored
         * with a warning at build time.
         */
        public Builder potionType(String potionType) { this.potionType = potionType; return this; }
        /**
         * Sets custom potion effects layered on top of the base {@link #potionType(String)}. Use this
         * for a duration vanilla has no {@code PotionType} constant for; the constant is still the
         * better answer when one exists.
         */
        public Builder potionEffects(List<PotionEffectDef> potionEffects) {
            this.potionEffects = potionEffects != null ? potionEffects : new ArrayList<>();
            return this;
        }

        public ItemDefinition build() {
            if (logicalId == null || logicalId.isBlank()) {
                throw new IllegalArgumentException("Item logicalId cannot be null or blank");
            }
            if (material == null || material.isBlank()) {
                throw new IllegalArgumentException("Item material cannot be null or blank for '" + logicalId + "'");
            }
            if (vanillaStack && (locked || !droppable || !movable)) {
                // These flags live in the PDC that vanillaStack suppresses, so honouring both would
                // mean silently ignoring one. Refuse the combination rather than hand out an item
                // whose lock is decoration.
                throw new IllegalArgumentException("Item '" + logicalId + "' sets vanillaStack together with"
                        + " locked/droppable/movable — those flags are stored in the PDC that vanillaStack"
                        + " strips. Drop vanillaStack, or drop the flags.");
            }
            return new ItemDefinition(this);
        }
    }

    /**
     * Structured representation of the custom_model_data component (1.21+).
     */
    public static final class CustomModelDataDef {
        private final List<String> strings;
        private final List<Float> floats;
        private final List<Boolean> flags;
        private final List<Integer> colors;

        public CustomModelDataDef(List<String> strings, List<Float> floats,
                                  List<Boolean> flags, List<Integer> colors) {
            this.strings = strings != null ? List.copyOf(strings) : List.of();
            this.floats = floats != null ? List.copyOf(floats) : List.of();
            this.flags = flags != null ? List.copyOf(flags) : List.of();
            this.colors = colors != null ? List.copyOf(colors) : List.of();
        }

        public List<String> getStrings() { return strings; }
        public List<Float> getFloats() { return floats; }
        public List<Boolean> getFlags() { return flags; }
        public List<Integer> getColors() { return colors; }
    }

    /**
     * One custom potion effect: a Bukkit {@code PotionEffectType} constant name plus an explicit
     * duration and amplifier. Unlike a base {@code PotionType}, which carries vanilla's own duration,
     * this is how a config expresses a duration vanilla has no constant for (a 4:00 Invisibility).
     *
     * <p>Holds the type as a STRING on purpose: resolving it needs the server's effect registry,
     * which does not exist while config is being parsed in a unit test. Resolution happens once, in
     * {@link com.pharogames.items.manager.CustomItemManager}, at build time.
     */
    public static final class PotionEffectDef {
        private final String type;
        private final int durationSeconds;
        private final int amplifier;

        public PotionEffectDef(String type, int durationSeconds, int amplifier) {
            this.type = type;
            this.durationSeconds = durationSeconds;
            this.amplifier = amplifier;
        }

        public String getType() { return type; }
        public int getDurationSeconds() { return durationSeconds; }
        public int getAmplifier() { return amplifier; }

        /**
         * Duration in ticks, the unit {@code PotionEffect} takes. Config is authored in seconds
         * because that is what a kit or loot table is specified in.
         *
         * <p>A SPLASH potion delivers only 0.75x of this at ground zero (less further out), so a
         * 3:00 effect on the player it lands on is configured as {@code durationSeconds: 240}.
         */
        public int getDurationTicks() { return durationSeconds * 20; }
    }

    /**
     * Food component override (Paper {@code DataComponentTypes.FOOD} / {@code FoodProperties}).
     */
    public static final class FoodDef {
        private final int nutrition;
        private final float saturation;
        private final boolean canAlwaysEat;

        public FoodDef(int nutrition, float saturation, boolean canAlwaysEat) {
            this.nutrition = nutrition;
            this.saturation = saturation;
            this.canAlwaysEat = canAlwaysEat;
        }

        public int getNutrition() { return nutrition; }
        public float getSaturation() { return saturation; }
        public boolean isCanAlwaysEat() { return canAlwaysEat; }
    }
}
