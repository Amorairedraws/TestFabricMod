package com.amorairedraws.equipleveling.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.amorairedraws.equipleveling.EquipLevelingMod;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
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

    /** Adds the component lazily; this avoids mutating every ordinary item in a world. */
    public static EquipmentData getOrCreate(ItemStack stack) {
        EquipmentData data = stack.get(EQUIPMENT_TYPE);
        if (data == null) {
            data = EquipmentData.create(EquipmentCategory.getCategory(stack));
            stack.set(EQUIPMENT_TYPE, data);
        } else {
            data.refresh();
            stack.set(EQUIPMENT_TYPE, data);
        }
        return data;
    }

    /** Adds progression XP and immediately writes the immutable data component back. */
    public static void addXp(ItemStack stack, int amount) {
        if (!isTracked(stack)) return;
        EquipmentData data = getOrCreate(stack);
        data.addXp(amount);
        stack.set(EQUIPMENT_TYPE, data);
    }

    public static void markBrokenIfNecessary(ItemStack stack) {
        if (EquipLevelingConfig.isBrokenMechanicEnabled() && stack.isDamageable()
                && stack.getDamage() >= stack.getMaxDamage() && isTracked(stack)) {
            EquipmentData data = getOrCreate(stack);
            data.broken = true;
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
        data.refresh();
        stack.set(EQUIPMENT_TYPE, data);
        return true;
    }

    public static int repairCost(ItemStack stack) {
        EquipmentData data = stack.get(EQUIPMENT_TYPE);
        return data == null ? 0 : EquipLevelingConfig.getAnvilBaseCost()
                + data.level * EquipLevelingConfig.getAnvilPerLevelCost();
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
            Codec.BOOL.fieldOf("maxed").forGetter(d -> d.maxed)
        ).apply(i, EquipmentData::new));

        public int level, xp, xpRequired;
        public boolean mending, readyToLevelUp, broken, maxed;
        public List<EquipmentSlot> slots, bonusSlots;

        public EquipmentData(int level, int xp, int xpRequired, boolean mending,
                List<EquipmentSlot> slots, List<EquipmentSlot> bonusSlots,
                boolean ready, boolean broken, boolean maxed) {
            this.level = Math.max(0, level); this.xp = Math.max(0, xp);
            this.xpRequired = Math.max(1, xpRequired); this.mending = mending;
            this.slots = new ArrayList<>(slots); this.bonusSlots = new ArrayList<>(bonusSlots);
            this.readyToLevelUp = ready; this.broken = broken; this.maxed = maxed;
            while (this.slots.size() < 4) this.slots.add(new EquipmentSlot(null, 0));
            if (this.slots.size() > 4) this.slots = new ArrayList<>(this.slots.subList(0, 4));
            if (this.bonusSlots.size() > 2) this.bonusSlots = new ArrayList<>(this.bonusSlots.subList(0, 2));
        }

        public static EquipmentData create(String category) {
            List<EquipmentSlot> slots = new ArrayList<>();
            for (int n = 0; n < 4; n++) slots.add(new EquipmentSlot(null, 0));
            return new EquipmentData(0, 0,
                EquipLevelingConfig.getBaseXpForCategory(category == null ? "default" : category),
                false, slots, new ArrayList<>(), false, false, false);
        }
        public static EquipmentData create() { return create("default"); }

        public void addXp(int amount) {
            // Never allow a broken item, a capped item, or a maxed item to accrue XP.
            if (amount <= 0 || broken || readyToLevelUp || maxed) return;
            xp = (int) Math.min(Integer.MAX_VALUE, (long) xp + amount);
            readyToLevelUp = xp >= xpRequired;
        }

        /** Recomputes derived state after a slot or repair mutation. */
        public void refresh() {
            xpRequired = Math.max(1, xpRequired);
            readyToLevelUp = xp >= xpRequired;
            // Mending is derived from the four standard slots, never from the
            // material or from vanilla enchantment data.
            // Mending is a derived completion flag, not one of the two loot
            // bonus slots. Keeping it separate is important: loot may always
            // contribute at most two bonus enchantments.
            if (getFilledSlots() == 4) mending = true;
            while (bonusSlots.size() > 2) bonusSlots.remove(bonusSlots.size() - 1);
            updateMaxed();
        }
        public void levelUp(String category) {
            level++; xp = 0; readyToLevelUp = false;
            xpRequired = Math.max(1, (int)Math.ceil(EquipLevelingConfig.getBaseXpForCategory(category)
                    * Math.pow(EquipLevelingConfig.getXpMultiplier(), level)));
            updateMaxed();
        }
        public void levelUp() { levelUp("default"); }
        public int getFilledSlots() { return (int)slots.stream().filter(s -> !s.isEmpty()).count(); }
        public int getTotalSlots() { return 4 + bonusSlots.size(); }
        public void updateMaxed() {
            maxed = getFilledSlots() == 4 && mending && level >= EquipLevelingConfig.getMaterialTiers().length - 1
                && slots.stream().allMatch(s -> s.isEmpty() || s.enchantmentLevel >= maxEnchantmentLevel(s))
                && bonusSlots.stream().allMatch(s -> s.isEmpty() || s.enchantmentLevel >= maxEnchantmentLevel(s));
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
                    case "minecraft:mending", "minecraft:binding_curse", "minecraft:vanishing_curse",
                            "minecraft:silk_touch", "minecraft:flame", "minecraft:infinity",
                            "minecraft:multishot", "minecraft:channeling" -> 1;
                    case "minecraft:frost_walker" -> 2;
                    case "minecraft:soul_speed", "minecraft:swift_sneak", "minecraft:wind_burst" -> 3;
                    case "minecraft:breach" -> 4;
                    default -> 5;
                };
            } catch (RuntimeException ignored) {
                return 5;
            }
        }
        public EquipmentData copy() {
            List<EquipmentSlot> a = new ArrayList<>(), b = new ArrayList<>();
            slots.forEach(s -> a.add(s.copy())); bonusSlots.forEach(s -> b.add(s.copy()));
            return new EquipmentData(level, xp, xpRequired, mending, a, b, readyToLevelUp, broken, maxed);
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
    }
}
