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
    private final boolean unbreakable;
    private final Map<String, Integer> enchantments;
    private final boolean hideTooltip;
    private final boolean hideAdditionalTooltip;

    // Inventory behaviour (defaults -- can be overridden by GiveOptions)
    private final int slot;
    private final boolean locked;
    private final boolean droppable;
    private final boolean movable;

    // Arbitrary metadata for plugin-specific use
    private final Map<String, Object> metadata;

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
        this.unbreakable = builder.unbreakable;
        this.enchantments = Map.copyOf(builder.enchantments);
        this.hideTooltip = builder.hideTooltip;
        this.hideAdditionalTooltip = builder.hideAdditionalTooltip;
        this.slot = builder.slot;
        this.locked = builder.locked;
        this.droppable = builder.droppable;
        this.movable = builder.movable;
        this.metadata = Map.copyOf(builder.metadata);
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
    public boolean isUnbreakable() { return unbreakable; }
    public Map<String, Integer> getEnchantments() { return enchantments; }
    public boolean isHideTooltip() { return hideTooltip; }
    public boolean isHideAdditionalTooltip() { return hideAdditionalTooltip; }
    public int getSlot() { return slot; }
    public boolean isLocked() { return locked; }
    public boolean isDroppable() { return droppable; }
    public boolean isMovable() { return movable; }
    public Map<String, Object> getMetadata() { return metadata; }

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
        private boolean unbreakable = false;
        private Map<String, Integer> enchantments = new HashMap<>();
        private boolean hideTooltip = false;
        private boolean hideAdditionalTooltip = false;
        private int slot = -1;
        private boolean locked = false;
        private boolean droppable = true;
        private boolean movable = true;
        private Map<String, Object> metadata = new HashMap<>();

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
        public Builder unbreakable(boolean unbreakable) { this.unbreakable = unbreakable; return this; }
        public Builder enchantments(Map<String, Integer> enchantments) { this.enchantments = enchantments; return this; }
        public Builder hideTooltip(boolean hideTooltip) { this.hideTooltip = hideTooltip; return this; }
        public Builder hideAdditionalTooltip(boolean hide) { this.hideAdditionalTooltip = hide; return this; }
        public Builder slot(int slot) { this.slot = slot; return this; }
        public Builder locked(boolean locked) { this.locked = locked; return this; }
        public Builder droppable(boolean droppable) { this.droppable = droppable; return this; }
        public Builder movable(boolean movable) { this.movable = movable; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public ItemDefinition build() {
            if (logicalId == null || logicalId.isBlank()) {
                throw new IllegalArgumentException("Item logicalId cannot be null or blank");
            }
            if (material == null || material.isBlank()) {
                throw new IllegalArgumentException("Item material cannot be null or blank for '" + logicalId + "'");
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
}
