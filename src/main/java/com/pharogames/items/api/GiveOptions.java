package com.pharogames.items.api;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Runtime overrides applied when giving an item to a player.
 * All fields are optional; null means "use the definition's default".
 */
public final class GiveOptions {

    private final Integer slot;
    private final Boolean locked;
    private final Boolean droppable;
    private final Boolean movable;
    private final Object headOwner; // Player or UUID -- only used for PLAYER_HEAD material

    private GiveOptions(Builder builder) {
        this.slot = builder.slot;
        this.locked = builder.locked;
        this.droppable = builder.droppable;
        this.movable = builder.movable;
        this.headOwner = builder.headOwner;
    }

    public Integer getSlot() { return slot; }
    public Boolean getLocked() { return locked; }
    public Boolean getDroppable() { return droppable; }
    public Boolean getMovable() { return movable; }

    /**
     * Returns the head owner as a {@link Player} or {@link UUID}, or null if not set.
     * Only meaningful when the item's material is PLAYER_HEAD.
     */
    public Object getHeadOwner() { return headOwner; }

    public static Builder builder() {
        return new Builder();
    }

    /** Convenience: no overrides at all. */
    public static GiveOptions defaults() {
        return builder().build();
    }

    public static final class Builder {
        private Integer slot = null;
        private Boolean locked = null;
        private Boolean droppable = null;
        private Boolean movable = null;
        private Object headOwner = null;

        private Builder() {}

        /** Override the inventory slot. -1 means next available. */
        public Builder slot(int slot) { this.slot = slot; return this; }

        /** Override whether the item can be moved from its slot. */
        public Builder locked(boolean locked) { this.locked = locked; return this; }

        /** Override whether the item can be dropped. */
        public Builder droppable(boolean droppable) { this.droppable = droppable; return this; }

        /** Override whether the item can be moved within an inventory. */
        public Builder movable(boolean movable) { this.movable = movable; return this; }

        /**
         * Set the skin owner for PLAYER_HEAD items.
         * The skin is resolved once at item creation time from Mojang's API.
         */
        public Builder headOwner(Player player) { this.headOwner = player; return this; }

        /**
         * Set the skin owner by UUID for PLAYER_HEAD items.
         * The skin is resolved once at item creation time from Mojang's API.
         */
        public Builder headOwner(UUID uuid) { this.headOwner = uuid; return this; }

        public GiveOptions build() {
            return new GiveOptions(this);
        }
    }
}
