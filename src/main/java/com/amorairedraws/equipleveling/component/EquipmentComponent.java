package com.amorairedraws.equipleveling.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.amorairedraws.equipleveling.EquipLevelingMod;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import com.amorairedraws.equipleveling.util.MaterialTierUpgrader;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistent, codec-backed data attached to an equipment ItemStack. */
public final class EquipmentComponent {
    public static ComponentType<EquipmentData> EQUIPMENT_TYPE;

    private EquipmentComponent() {}

    public static void register() {
        EQUIPMENT_TYPE = Registry.register(Registries.DATA_COMPONENT_TYPE,
            Identifier.of(EquipLevelingMod.MOD_ID, "equipment"),
            ComponentType.<EquipmentData>builder().codec(EquipmentData.CODEC).cache().build());
    }

    public static boolean isTracked(ItemStack stack) {
        return !stack.isEmpty() && EquipmentCategory.isEquipment(stack);
    }

    /** Returns whether the stack is a tracked item currently in the broken state. */
    public static boolean isBroken(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.contains(EQUIPMENT_TYPE)) return false;
        EquipmentData data = stack.get(EQUIPMENT_TYPE);
        return data != null && data.broken;
    }

    /** Adds the component lazily; this avoids mutating every ordinary item in a world. */
    public static EquipmentData getOrCreate(ItemStack stack) {
        EquipmentData data = stack.get(EQUIPMENT_TYPE);
        EquipmentData before = data == null ? null : data.copy();
        if (data == null) {
            data = EquipmentData.create(EquipmentCategory.getCategory(stack));
        } else {
            data.refresh(EquipmentCategory.getCategory(stack));
        }
        // refresh() can only inspect component data. Include the actual item
        // material here so non-tiered equipment can still reach MAX LEVEL and a
        // non-final tier is not incorrectly treated as maxed.
        data.updateMaxed(MaterialTierUpgrader.isTierLevelSatisfied(stack, data.level,
                EquipLevelingConfig.getMaterialTiers()));
        // Issue 4: avoid marking the stack dirty every tick when nothing changed.
        if (before == null || !before.equals(data)) {
            stack.set(EQUIPMENT_TYPE, data);
        }
        return data;
    }

    /**
     * Lookup-aware variant of {@link #getOrCreate(ItemStack)}. Uses the live
     * world registry to read the real maximum level of every enchantment
     * (including modded ones), so the client-side maxed detection matches the
     * server exactly instead of falling back to the vanilla-only switch.
     */
    public static EquipmentData getOrCreate(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        EquipmentData data = stack.get(EQUIPMENT_TYPE);
        EquipmentData before = data == null ? null : data.copy();
        if (data == null) {
            data = EquipmentData.create(EquipmentCategory.getCategory(stack));
        } else {
            data.refresh(EquipmentCategory.getCategory(stack));
        }
        data.updateMaxed(lookup, MaterialTierUpgrader.isTierLevelSatisfied(stack, data.level,
                EquipLevelingConfig.getMaterialTiers()));
        if (before == null || !before.equals(data)) {
            stack.set(EQUIPMENT_TYPE, data);
        }
        return data;
    }

    /** Adds progression XP and immediately writes the immutable data component back.
     * @return true only when the item accepted the reward (not broken, capped, or maxed). */
    public static boolean addXp(ItemStack stack, int amount) {
        return addXp(stack, amount, null);
    }

    /** Adds XP and plays the ready-to-level-up sound if the item becomes ready.
     * The player is needed for the sound; it may be null for non-player sources. */
    public static boolean addXp(ItemStack stack, int amount, net.minecraft.entity.player.PlayerEntity player) {
        if (!isTracked(stack)) return false;
        EquipmentData data = getOrCreate(stack);
        boolean wasReady = data.readyToLevelUp;
        int before = data.xp;
        data.addXp(amount);
        stack.set(EQUIPMENT_TYPE, data);
        // Issue 12: play a celebratory sound the moment an item becomes ready to
        // level up. Only the server owns progression.
        if (!wasReady && data.readyToLevelUp && player != null
                && player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            serverWorld.playSound(null, player.getBlockPos(),
                    net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_FALL,
                    net.minecraft.sound.SoundCategory.MASTER, 1.0F, 1.0F);
        }
        // The server mutated the stack's data component, but the client holds its
        // own copy of the item that only refreshes when a slot-update packet
        // arrives (drop/pickup/chest). markDirty() does not reliably emit one for
        // the survival inventory, and sendContentUpdates() relies on a hash-based
        // change detector that does not reliably detect a change to a custom data
        // component (observed: a held item's XP can lag several seconds behind the
        // server until a durability change or resync action forces a re-read, and
        // with Unbreaking a durability change may not even occur). Push the mutated
        // slot directly so the client is always brought up to date immediately.
        if (player != null && !player.getEntityWorld().isClient()) {
            forceClientSync((net.minecraft.server.network.ServerPlayerEntity) player, stack);
        }
        return data.xp != before;
    }

    /**
     * Reliably pushes an item's slot to the client. Unlike sendContentUpdates(),
     * which only emits a packet when its change detector decides the slot differs
     * from the last-sent state (and which does not reliably detect a change to a
     * custom data component), this sends a ScreenHandlerSlotUpdateS2CPacket
     * directly for the screen-handler slot that currently holds {@code stack}.
     * This bypasses all change-detection heuristics, so the client's copy of the
     * item is always brought up to date immediately.
     */
    public static void forceClientSync(net.minecraft.server.network.ServerPlayerEntity player, ItemStack stack) {
        if (player == null || stack == null) return;
        // The item is always in the player's own inventory (main hand, offhand or
        // armor), so sync it through the survival inventory screen handler (syncId 0).
        net.minecraft.screen.PlayerScreenHandler handler = player.playerScreenHandler;
        if (handler == null) return;
        for (int i = 0; i < handler.slots.size(); i++) {
            net.minecraft.screen.slot.Slot slot = handler.slots.get(i);
            if (slot != null && slot.getStack() == stack) {
                int revision = handler.nextRevision();
                player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket(
                        handler.syncId, revision, i, stack));
                return;
            }
        }
    }

    /** Rehydrates vanilla's enchantment component from custom slot IDs. Loot
     * intentionally removes vanilla enchantment tags, so this bridge makes loot
     * bonuses functional again while keeping the custom component authoritative. */
    public static void restoreEnchantments(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        if (!isTracked(stack) || !stack.contains(EQUIPMENT_TYPE)) return;
        EquipmentData data = stack.get(EQUIPMENT_TYPE);
        if (data == null) return;

        // Equipment can enter a world through commands, older versions of this
        // mod, or another mod without passing through our loot hook. Reconcile
        // the vanilla ENCHANTMENTS component into bonus slots so that any
        // enchantment added after the component was created (e.g. via commands)
        // is still captured. Curses are never allowed; bonus slots always start
        // at level 1 and are capped at two.
        var enchantmentLookup = lookup.getOrThrow(RegistryKeys.ENCHANTMENT);
        for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
            if (data.bonusSlots.size() >= 2) break;
            String id = entry.getKey().getKey().map(key -> key.getValue().toString()).orElse(null);
            if (id == null || "minecraft:mending".equals(id)) continue;
            // Skip enchantments already present in standard or bonus slots.
            boolean already = false;
            for (EquipmentComponent.EquipmentSlot s : data.slots) {
                if (!s.isEmpty() && id.equals(s.enchantmentId)) { already = true; break; }
            }
            if (already) continue;
            for (EquipmentComponent.EquipmentSlot s : data.bonusSlots) {
                if (!s.isEmpty() && id.equals(s.enchantmentId)) { already = true; break; }
            }
            if (already) continue;
            // Curses are never allowed on equipment.
            try {
                var key = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(id));
                boolean curse = enchantmentLookup.getOptional(key)
                        .map(e -> e.isIn(net.minecraft.registry.tag.EnchantmentTags.CURSE)).orElse(false);
                if (curse) continue;
            } catch (RuntimeException ignored) { continue; }
            data.bonusSlots.add(new EquipmentSlot(id, 1));
        }
        data.refresh(EquipmentCategory.getCategory(stack));
        data.updateMaxed(lookup, MaterialTierUpgrader.isTierLevelSatisfied(stack, data.level,
                EquipLevelingConfig.getMaterialTiers()));
        if (data.broken) {
            stack.remove(DataComponentTypes.ENCHANTMENTS);
            return;
        }
        ItemEnchantmentsComponent mirrored = buildEnchantmentsComponent(data, enchantmentLookup);
        // Issue 4: avoid recreating identical components every tick. Only write
        // when the mirrored enchantments actually differ from what is stored.
        ItemEnchantmentsComponent current = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS,
                ItemEnchantmentsComponent.DEFAULT);
        if (!current.equals(mirrored)) {
            stack.set(DataComponentTypes.ENCHANTMENTS, mirrored);
        }
        TooltipDisplayComponent display = stack.getOrDefault(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplayComponent.DEFAULT);
        TooltipDisplayComponent hidden = display.with(DataComponentTypes.ENCHANTMENTS, false);
        if (!hidden.equals(display)) {
            stack.set(DataComponentTypes.TOOLTIP_DISPLAY, hidden);
        }
        // Only write the EQUIPMENT_TYPE component back if the data actually changed.
        EquipmentData stored = stack.get(EQUIPMENT_TYPE);
        if (stored == null || !stored.equals(data)) {
            stack.set(EQUIPMENT_TYPE, data);
        }
    }

    /** Builds the mirrored vanilla ENCHANTMENTS component from the custom slot
     * data, regardless of the broken flag. Used by the anvil so a broken item
     * can be repaired (vanilla bails out of updateResult when canHaveEnchantments
     * is false, which the broken mechanic otherwise makes true by removing the
     * component). */
    private static ItemEnchantmentsComponent buildEnchantmentsComponent(EquipmentData data,
            net.minecraft.registry.RegistryEntryLookup<net.minecraft.enchantment.Enchantment> enchantmentLookup) {
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        for (EquipmentSlot slot : data.slots) addEnchantment(builder, enchantmentLookup, slot);
        for (EquipmentSlot slot : data.bonusSlots) addEnchantment(builder, enchantmentLookup, slot);
        if (data.mending) addEnchantment(builder, enchantmentLookup,
                new EquipmentSlot("minecraft:mending", 1));
        return builder.build();
    }

    /** Temporarily restores the vanilla ENCHANTMENTS component on a broken item
     * so vanilla's anvil repair path (which requires canHaveEnchantments() to be
     * true) can run. The broken flag is left intact; onTakeOutput clears it and
     * rebuilds the component properly. */
    public static void restoreEnchantmentsForRepair(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        if (!isTracked(stack) || !stack.contains(EQUIPMENT_TYPE)) return;
        EquipmentData data = stack.get(EQUIPMENT_TYPE);
        if (data == null) return;
        var enchantmentLookup = lookup.getOrThrow(RegistryKeys.ENCHANTMENT);
        ItemEnchantmentsComponent mirrored = buildEnchantmentsComponent(data, enchantmentLookup);
        stack.set(DataComponentTypes.ENCHANTMENTS, mirrored);
    }

    private static void addEnchantment(ItemEnchantmentsComponent.Builder builder,
            net.minecraft.registry.RegistryEntryLookup<net.minecraft.enchantment.Enchantment> lookup,
            EquipmentSlot slot) {
        if (slot.isEmpty()) return;
        try {
            var key = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(slot.enchantmentId));
            lookup.getOptional(key).ifPresent(entry -> builder.set(entry, slot.enchantmentLevel));
        } catch (RuntimeException ignored) { }
    }

    public static void markBrokenIfNecessary(ItemStack stack) {
        if (EquipLevelingConfig.isBrokenMechanicEnabled() && stack.isDamageable()
                && stack.getDamage() >= stack.getMaxDamage() && isTracked(stack)) {
            EquipmentData data = getOrCreate(stack);
            data.broken = true;
            // Remove the mirrored vanilla component immediately.  The mixin also
            // guards enchantment reads, but removing the data prevents other mods
            // that inspect components directly from applying an effect.
            stack.remove(DataComponentTypes.ENCHANTMENTS);
            stack.set(EQUIPMENT_TYPE, data);
        }
    }

    /** Repairs an equipment stack with a material at an anvil. Returns false when
     * the item is not one of ours or the supplied material is not accepted. */
    public static boolean repair(ItemStack stack, int restoredDamage) {
        if (!isTracked(stack) || !stack.contains(EQUIPMENT_TYPE) || !stack.isDamageable()) return false;
        EquipmentData data = stack.get(EQUIPMENT_TYPE);
        if (!data.broken && stack.getDamage() <= 0) return false;
        stack.setDamage(Math.max(0, stack.getDamage() - Math.max(1, restoredDamage)));
        data.broken = false;
        data.refresh(EquipmentCategory.getCategory(stack));
        stack.set(EQUIPMENT_TYPE, data);
        return true;
    }

    public static int repairCost(ItemStack stack) {
        EquipmentData data = stack.get(EQUIPMENT_TYPE);
        if (data == null) return 0;
        long cost = (long) EquipLevelingConfig.getAnvilBaseCost()
                + (long) data.level * EquipLevelingConfig.getAnvilPerLevelCost();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, cost));
    }

    public static final class EquipmentData {
        public static final Codec<EquipmentData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("level").forGetter(d -> d.level),
            Codec.INT.fieldOf("xp").forGetter(d -> d.xp),
            Codec.INT.fieldOf("xp_required").forGetter(d -> d.xpRequired),
            Codec.BOOL.fieldOf("mending").forGetter(d -> d.mending),
            EquipmentSlot.CODEC.listOf().fieldOf("slots").forGetter(d -> d.slots),
            EquipmentSlot.CODEC.listOf().fieldOf("bonus_slots").forGetter(d -> d.bonusSlots),
            Codec.BOOL.fieldOf("ready_to_level_up").forGetter(d -> d.readyToLevelUp),
            Codec.BOOL.fieldOf("broken").forGetter(d -> d.broken),
            Codec.BOOL.fieldOf("maxed").forGetter(d -> d.maxed),
            Codec.INT.optionalFieldOf("max_slots", 4).forGetter(d -> d.maxSlots),
            Codec.BOOL.optionalFieldOf("slots_complete", false).forGetter(d -> d.slotsComplete),
            StoredOffer.CODEC.listOf().optionalFieldOf("offers", new ArrayList<>()).forGetter(d -> d.offers)
        ).apply(i, EquipmentData::new));

        public int level, xp, xpRequired;
        public int maxSlots;
        public boolean mending, readyToLevelUp, broken, maxed;
        /** True when no more compatible enchantments can be added to the standard
         * slots, so the item is "slot complete" even if not every configured slot
         * is filled. This lets tools with few compatible enchantments still earn
         * mending and reach MAX LEVEL. */
        public boolean slotsComplete;
        public List<EquipmentSlot> slots, bonusSlots;
        /** The currently displayed enchanting-table offers, persisted so they
         * survive taking the item out of and back into the table without being
         * re-rolled. Cleared by reroll() and selectOffer(). */
        public List<StoredOffer> offers;

        public EquipmentData(int level, int xp, int xpRequired, boolean mending,
                List<EquipmentSlot> slots, List<EquipmentSlot> bonusSlots,
                boolean ready, boolean broken, boolean maxed, int maxSlots) {
            this(level, xp, xpRequired, mending, slots, bonusSlots, ready, broken, maxed, maxSlots, false);
        }

        public EquipmentData(int level, int xp, int xpRequired, boolean mending,
                List<EquipmentSlot> slots, List<EquipmentSlot> bonusSlots,
                boolean ready, boolean broken, boolean maxed, int maxSlots, boolean slotsComplete) {
            this(level, xp, xpRequired, mending, slots, bonusSlots, ready, broken, maxed, maxSlots,
                    slotsComplete, new ArrayList<>());
        }

        public EquipmentData(int level, int xp, int xpRequired, boolean mending,
                List<EquipmentSlot> slots, List<EquipmentSlot> bonusSlots,
                boolean ready, boolean broken, boolean maxed, int maxSlots, boolean slotsComplete,
                List<StoredOffer> offers) {
            this.level = Math.max(0, level); this.xp = Math.max(0, xp);
            this.xpRequired = Math.max(1, xpRequired); this.mending = mending;
            this.slots = new ArrayList<>(slots); this.bonusSlots = new ArrayList<>(bonusSlots);
            this.readyToLevelUp = ready; this.broken = broken; this.maxed = maxed;
            this.slotsComplete = slotsComplete;
            this.offers = new ArrayList<>(offers);
            // Allow up to 8 standard slots so players with large enchantment mods
            // can raise the cap (Issue 7).
            this.maxSlots = Math.min(8, Math.max(1, maxSlots));
            while (this.slots.size() < this.maxSlots) this.slots.add(new EquipmentSlot(null, 0));
            if (this.slots.size() > this.maxSlots) this.slots = new ArrayList<>(this.slots.subList(0, this.maxSlots));
            if (this.bonusSlots.size() > 2) this.bonusSlots = new ArrayList<>(this.bonusSlots.subList(0, 2));
        }

        public static EquipmentData create(String category) {
            List<EquipmentSlot> slots = new ArrayList<>();
            int max = EquipLevelingConfig.getMaxSlotsForCategory(category == null ? "default" : category);
            for (int n = 0; n < max; n++) slots.add(new EquipmentSlot(null, 0));
            return new EquipmentData(0, 0,
                EquipLevelingConfig.getBaseXpForCategory(category == null ? "default" : category),
                false, slots, new ArrayList<>(), false, false, false, max);
        }
        public static EquipmentData create() { return create("default"); }

        public void addXp(int amount) {
            // Never allow a broken item, a capped item, or a maxed item to accrue XP.
            if (amount <= 0 || broken || readyToLevelUp || maxed) return;
            // A full bar is a state, not overflow storage. Capping at the
            // requirement makes it impossible to render values such as 132/120.
            xp = (int) Math.min(xpRequired, Math.min((long) Integer.MAX_VALUE, (long) xp + amount));
            readyToLevelUp = xp >= xpRequired;
        }

        /** Recomputes derived state after a slot or repair mutation. */
        public void refresh() {
            refresh(null);
        }

        /** Recomputes the configured XP curve as well as derived slot state. */
        public void refresh(String category) {
            if (category != null) {
                double required = EquipLevelingConfig.getBaseXpForCategory(category)
                        * Math.pow(EquipLevelingConfig.getXpMultiplier(), level);
                xpRequired = Double.isFinite(required)
                        ? Math.max(1, required >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(required))
                        : Integer.MAX_VALUE;
            } else {
                xpRequired = Math.max(1, xpRequired);
            }
            readyToLevelUp = xp >= xpRequired;
            // If the configured max slots for this category changed, resync the
            // standard slot list to that size (dropping any overflow).
            int configuredMax = EquipLevelingConfig.getMaxSlotsForCategory(category == null ? "default" : category);
            if (configuredMax != maxSlots) {
                maxSlots = Math.min(8, Math.max(1, configuredMax));
                while (slots.size() < maxSlots) slots.add(new EquipmentSlot(null, 0));
                if (slots.size() > maxSlots) slots = new ArrayList<>(slots.subList(0, maxSlots));
            }
            // Mending is awarded once the configured standard-slot cap is reached,
            // or when no more compatible enchantments can be added (slotsComplete).
            mending = getFilledSlots() >= maxSlots || slotsComplete;
            while (bonusSlots.size() > 2) bonusSlots.remove(bonusSlots.size() - 1);
            updateMaxed();
        }
        public void levelUp(String category) {
            if (level < Integer.MAX_VALUE) level++;
            xp = 0; readyToLevelUp = false;
            double required = EquipLevelingConfig.getBaseXpForCategory(category)
                    * Math.pow(EquipLevelingConfig.getXpMultiplier(), level);
            xpRequired = Double.isFinite(required)
                    ? Math.max(1, (int) Math.ceil(required)) : Integer.MAX_VALUE;
            updateMaxed();
        }
        public void levelUp() { levelUp("default"); }
        public int getFilledSlots() { return (int)slots.stream().filter(s -> !s.isEmpty()).count(); }
        public int getTotalSlots() { return maxSlots + bonusSlots.size(); }
        /**
         * Recomputes the enchantment portion of maxed state when no ItemStack is
         * available.  Material tier is deliberately treated as unsatisfied here;
         * callers that have the stack must use the overload accepting the live
         * tier result.  This avoids incorrectly marking a wood item maxed merely
         * because its numeric level happens to be high enough.
         */
        public void updateMaxed() {
            updateMaxed(false);
        }

        public void updateMaxed(boolean tierLevelSatisfied) {
            // Issue 7: maxed is driven purely by enchantment completion - every
            // standard slot filled (or no more compatible enchantments) plus every
            // enchantment at its max level plus Mending awarded. The material tier
            // (legendary promotion) is an optional extra and must not block MAX.
            // Mending is recomputed here so a freshly filled last slot immediately
            // reflects the completed cap without waiting for a later refresh().
            mending = getFilledSlots() >= maxSlots || slotsComplete;
            maxed = (getFilledSlots() >= maxSlots || slotsComplete) && mending
                && slots.stream().allMatch(s -> s.isEmpty() || s.enchantmentLevel >= maxEnchantmentLevel(s))
                && bonusSlots.stream().allMatch(s -> s.isEmpty() || s.enchantmentLevel >= maxEnchantmentLevel(s));
            // A maxed item is no longer "ready to level up" - there are no more
            // upgrades to gather, so clear the flag or the tooltip would keep
            // advertising a level-up that can never happen.
            if (maxed) readyToLevelUp = false;
        }

        /** Uses the live world registry when deciding whether a modded
         * enchantment has reached its real maximum level. */
        public void updateMaxed(RegistryWrapper.WrapperLookup lookup) {
            // The item is required for a reliable material-tier check.  Use the
            // conservative value here; restoreEnchantments/getOrCreate follow
            // this with the stack-aware overload.
            updateMaxed(lookup, false);
        }

        public void updateMaxed(RegistryWrapper.WrapperLookup lookup, boolean tierLevelSatisfied) {
            // Material tier is not part of MAX (Issue 7); enchantment completion
            // alone determines maxed state. Mending is recomputed here too.
            mending = getFilledSlots() >= maxSlots || slotsComplete;
            maxed = (getFilledSlots() >= maxSlots || slotsComplete) && mending
                && slots.stream().allMatch(s -> s.isEmpty() || s.enchantmentLevel >= maxEnchantmentLevel(s, lookup))
                && bonusSlots.stream().allMatch(s -> s.isEmpty() || s.enchantmentLevel >= maxEnchantmentLevel(s, lookup));
            // A maxed item is no longer "ready to level up" - clear the flag so
            // the tooltip and enchanting table stop advertising a level-up.
            if (maxed) readyToLevelUp = false;
        }

        /** Returns the registered maximum, including maxima supplied by modded
         * or datapack enchantments. Unknown IDs retain a safe progression cap. */
        public static int maxEnchantmentLevel(EquipmentSlot slot) {
            if (slot == null || slot.isEmpty()) return 0;
            try {
                // Enchantment registries are world/datapack registries in 1.21.11,
                // so there is no safe process-global registry to query here.
                // The offer generator performs the world-registry compatibility
                // check; this fallback covers the vanilla maxima used by stored data.
                return switch (slot.enchantmentId) {
                    // Max level 1
                    case "minecraft:mending", "minecraft:binding_curse", "minecraft:vanishing_curse",
                            "minecraft:silk_touch", "minecraft:flame", "minecraft:infinity",
                            "minecraft:multishot", "minecraft:channeling", "minecraft:aqua_affinity" -> 1;
                    // Max level 2
                    case "minecraft:frost_walker", "minecraft:knockback", "minecraft:fire_aspect",
                            "minecraft:punch" -> 2;
                    // Max level 3
                    case "minecraft:soul_speed", "minecraft:swift_sneak", "minecraft:wind_burst",
                            "minecraft:unbreaking", "minecraft:fortune", "minecraft:respiration",
                            "minecraft:thorns", "minecraft:depth_strider", "minecraft:looting",
                            "minecraft:sweeping_edge", "minecraft:loyalty", "minecraft:riptide",
                            "minecraft:quick_charge", "minecraft:luck_of_the_sea", "minecraft:lure" -> 3;
                    // Max level 4
                    case "minecraft:breach", "minecraft:protection", "minecraft:fire_protection",
                            "minecraft:blast_protection", "minecraft:projectile_protection",
                            "minecraft:feather_falling", "minecraft:piercing" -> 4;
                    // Max level 5
                    case "minecraft:efficiency", "minecraft:sharpness", "minecraft:smite",
                            "minecraft:bane_of_arthropods", "minecraft:power", "minecraft:impaling",
                            "minecraft:density" -> 5;
                    default -> 5;
                };
            } catch (RuntimeException ignored) {
                return 5;
            }
        }
        public static int maxEnchantmentLevel(EquipmentSlot slot, RegistryWrapper.WrapperLookup lookup) {
            try {
                var key = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(slot.enchantmentId));
                return lookup.getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(key)
                        .map(entry -> Math.max(1, entry.value().getMaxLevel()))
                        .orElse(maxEnchantmentLevel(slot));
            } catch (RuntimeException ignored) {
                return maxEnchantmentLevel(slot);
            }
        }

        public EquipmentData copy() {
            List<EquipmentSlot> a = new ArrayList<>(), b = new ArrayList<>();
            slots.forEach(s -> a.add(s.copy())); bonusSlots.forEach(s -> b.add(s.copy()));
            List<StoredOffer> o = new ArrayList<>();
            offers.forEach(x -> o.add(x.copy()));
            return new EquipmentData(level, xp, xpRequired, mending, a, b, readyToLevelUp, broken, maxed, maxSlots, slotsComplete, o);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EquipmentData d)) return false;
            return level == d.level && xp == d.xp && xpRequired == d.xpRequired
                    && mending == d.mending && readyToLevelUp == d.readyToLevelUp
                    && broken == d.broken && maxed == d.maxed && maxSlots == d.maxSlots
                    && slotsComplete == d.slotsComplete
                    && slots.equals(d.slots) && bonusSlots.equals(d.bonusSlots)
                    && offers.equals(d.offers);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(level, xp, xpRequired, mending, readyToLevelUp, broken, maxed, maxSlots, slotsComplete, slots, bonusSlots, offers);
        }
    }

    public static final class EquipmentSlot {
        public static final Codec<EquipmentSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("enchantment_id").forGetter(s -> Optional.ofNullable(s.enchantmentId)),
            Codec.INT.fieldOf("level").forGetter(s -> s.enchantmentLevel)
        ).apply(i, (id, level) -> new EquipmentSlot(id.orElse(null), level)));
        public String enchantmentId; public int enchantmentLevel;
        public EquipmentSlot(String id, int level) { enchantmentId = id; enchantmentLevel = Math.max(0, level); }
        public boolean isEmpty() { return enchantmentId == null || enchantmentLevel <= 0; }
        public EquipmentSlot copy() { return new EquipmentSlot(enchantmentId, enchantmentLevel); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EquipmentSlot s)) return false;
            return enchantmentLevel == s.enchantmentLevel
                    && java.util.Objects.equals(enchantmentId, s.enchantmentId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(enchantmentId, enchantmentLevel);
        }
    }

    /** A persisted enchanting-table offer. Holds the enchantment identifier and
     * the encoded offer level (NEW_SLOT / UPGRADE_BASE-n / LEGENDARY) so the
     * exact offer can be restored when the item is re-inserted into the table. */
    public static final class StoredOffer {
        public static final Codec<StoredOffer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("enchantment_id").forGetter(s -> Optional.ofNullable(s.enchantmentId)),
            Codec.INT.fieldOf("encoded_level").forGetter(s -> s.encodedLevel)
        ).apply(i, (id, level) -> new StoredOffer(id.orElse(null), level)));
        public String enchantmentId;
        public int encodedLevel;
        public StoredOffer(String enchantmentId, int encodedLevel) {
            this.enchantmentId = enchantmentId;
            this.encodedLevel = encodedLevel;
        }
        public StoredOffer copy() { return new StoredOffer(enchantmentId, encodedLevel); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StoredOffer s)) return false;
            return encodedLevel == s.encodedLevel && java.util.Objects.equals(enchantmentId, s.enchantmentId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(enchantmentId, encodedLevel);
        }
    }
}
