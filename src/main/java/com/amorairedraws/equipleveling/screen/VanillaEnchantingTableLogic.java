package com.amorairedraws.equipleveling.screen;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import com.amorairedraws.equipleveling.util.MaterialTierUpgrader;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Server-authoritative behavior for the vanilla enchanting-table handler.
 *
 * <p>The handler's existing three synchronized offer arrays are deliberately
 * reused instead of opening a replacement menu.  This keeps the original
 * inventory, book animation, texture, option hit boxes, and all normal screen
 * synchronization in place.</p>
 */
public final class VanillaEnchantingTableLogic {
    /** Encodings stored in EnchantmentScreenHandler.enchantmentLevel. */
    public static final int NEW_SLOT = -1;
    private static final int UPGRADE_BASE = -100;
    public static final int LEGENDARY = -1000;

    private VanillaEnchantingTableLogic() { }

    public enum OfferKind { NONE, NEW_ENCHANTMENT, UPGRADE, LEGENDARY }

    public static OfferKind getOfferKind(EnchantmentScreenHandler handler, int index) {
        if (index < 0 || index >= 3 || handler.enchantmentPower[index] <= 0) return OfferKind.NONE;
        int encoded = handler.enchantmentLevel[index];
        if (encoded == NEW_SLOT) return OfferKind.NEW_ENCHANTMENT;
        if (encoded <= UPGRADE_BASE && encoded != LEGENDARY) return OfferKind.UPGRADE;
        return encoded == LEGENDARY ? OfferKind.LEGENDARY : OfferKind.NONE;
    }

    public static int getUpgradeTargetLevel(EnchantmentScreenHandler handler, int index) {
        return Math.max(1, -(handler.enchantmentLevel[index] - UPGRADE_BASE));
    }

    /** Fills the vanilla synchronized arrays with this mod's three offers. */
    public static void generateOffers(EnchantmentScreenHandler handler, PlayerEntity player, Random random) {
        clearOffers(handler);
        ItemStack stack = handler.getSlot(0).getStack();
        if (!EquipmentComponent.isTracked(stack)) {
            handler.sendContentUpdates();
            return;
        }

        EquipmentComponent.EquipmentData data = EquipmentComponent.getOrCreate(stack);
        var registries = player.getEntityWorld().getRegistryManager();
        Registry<Enchantment> enchantments = registries.getOrThrow(RegistryKeys.ENCHANTMENT);
        data.updateMaxed(registries, MaterialTierUpgrader.isTierLevelSatisfied(stack, data.level,
                EquipLevelingConfig.getMaterialTiers()));
        stack.set(EquipmentComponent.EQUIPMENT_TYPE, data);
        if (data.maxed || data.broken || !data.readyToLevelUp) {
            handler.sendContentUpdates();
            return;
        }

        for (int index = 0; index < 3; index++) {
            GeneratedOffer offer = generateOffer(data, stack, enchantments, registries, random);
            if (offer == null) continue;
            handler.enchantmentPower[index] = 1; // active marker; leveling itself has no XP-level cost
            handler.enchantmentId[index] = offer.enchantmentRawId;
            handler.enchantmentLevel[index] = offer.encodedLevel;
        }
        handler.sendContentUpdates();
    }

    /** Applies an option selected through one of vanilla's three existing rows. */
    public static boolean selectOffer(EnchantmentScreenHandler handler, PlayerEntity player, int index, Random random) {
        if (index < 0 || index >= 3 || player.getEntityWorld().isClient()) return false;
        OfferKind kind = getOfferKind(handler, index);
        if (kind == OfferKind.NONE) return false;

        ItemStack stack = handler.getSlot(0).getStack();
        if (!EquipmentComponent.isTracked(stack)) return false;
        EquipmentComponent.EquipmentData data = EquipmentComponent.getOrCreate(stack);
        var registries = player.getEntityWorld().getRegistryManager();
        Registry<Enchantment> enchantments = registries.getOrThrow(RegistryKeys.ENCHANTMENT);
        data.updateMaxed(registries, MaterialTierUpgrader.isTierLevelSatisfied(stack, data.level,
                EquipLevelingConfig.getMaterialTiers()));
        if (!data.readyToLevelUp || data.broken || data.maxed) return false;

        boolean applied = switch (kind) {
            case NEW_ENCHANTMENT -> addNewSlot(data, idAt(handler, index, enchantments));
            case UPGRADE -> upgradeSlot(data, idAt(handler, index, enchantments),
                    getUpgradeTargetLevel(handler, index), registries);
            case LEGENDARY -> promote(handler, stack);
            case NONE -> false;
        };
        if (!applied) return false;

        // A legendary promotion replaces the stack in the existing vanilla input
        // slot. Re-read the component instead of retaining stale data from the
        // old material.
        stack = handler.getSlot(0).getStack();
        data = EquipmentComponent.getOrCreate(stack);
        int restored = (int) Math.round(stack.getMaxDamage()
                * (EquipLevelingConfig.getDurabilityRestorePercent() / 100.0));
        if (stack.isDamageable() && restored > 0) {
            stack.setDamage(Math.max(0, stack.getDamage() - restored));
        }

        data.levelUp(EquipmentCategory.getCategory(stack));
        stack.set(EquipmentComponent.EQUIPMENT_TYPE, data);
        EquipmentComponent.restoreEnchantments(stack, registries);
        handler.getSlot(0).markDirty();
        // markDirty invokes the handler's regular content-change path, which
        // immediately creates the next server-synchronized set of offers.
        return true;
    }

    /** Rerolls without lapis, spending only the clearly displayed player-level cost. */
    public static boolean reroll(EnchantmentScreenHandler handler, PlayerEntity player, Random random) {
        if (player.getEntityWorld().isClient()) return true;
        ItemStack stack = handler.getSlot(0).getStack();
        if (!EquipmentComponent.isTracked(stack)) return false;
        EquipmentComponent.EquipmentData data = EquipmentComponent.getOrCreate(stack);
        if (!data.readyToLevelUp || data.broken || data.maxed) return false;

        Registry<Enchantment> enchantments = player.getEntityWorld().getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT);
        int cost = getRerollCost(stack, handler, enchantments);
        if (!player.isInCreativeMode() && player.experienceLevel < cost) return false;
        if (!player.isInCreativeMode()) player.addExperienceLevels(-cost);
        generateOffers(handler, player, random);
        return true;
    }

    /**
     * Base cost follows filled standard slots. The one displayed number adds the
     * highest offered enchantment's vanilla anvil weight once, rather than
     * summing all three rows and turning a simple reroll into a surprise tax.
     */
    public static int getRerollCost(ItemStack stack, EnchantmentScreenHandler handler,
            Registry<Enchantment> enchantments) {
        if (!EquipmentComponent.isTracked(stack) || !stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) return 0;
        EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
        int[] baseCosts = EquipLevelingConfig.getRerollCosts();
        int base = baseCosts[Math.min(4, data.getFilledSlots())];
        int extra = 0;
        for (int rawId : handler.enchantmentId) {
            if (rawId < 0) continue;
            extra = Math.max(extra, enchantments.getEntry(rawId)
                    .map(entry -> Math.max(0, entry.value().getAnvilCost())).orElse(0));
        }
        return Math.max(0, base + extra);
    }

    public static String describeOffer(EnchantmentScreenHandler handler, int index,
            Registry<Enchantment> enchantments) {
        OfferKind kind = getOfferKind(handler, index);
        if (kind == OfferKind.LEGENDARY) return "Legendary tier upgrade";
        if (kind == OfferKind.NONE) return "";
        String name = enchantments.getEntry(handler.enchantmentId[index])
                .map(entry -> Enchantment.getName(entry,
                        kind == OfferKind.UPGRADE ? getUpgradeTargetLevel(handler, index) : 1).getString())
                .orElse("Unknown enchantment");
        return kind == OfferKind.UPGRADE ? "Upgrade " + name : name;
    }

    private static GeneratedOffer generateOffer(EquipmentComponent.EquipmentData data, ItemStack stack,
            Registry<Enchantment> enchantments, net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
            Random random) {
        List<EquipmentComponent.EquipmentSlot> upgradeable = new ArrayList<>();
        for (EquipmentComponent.EquipmentSlot slot : allSlots(data)) {
            if (!slot.isEmpty() && !isCurse(slot.enchantmentId, enchantments)
                    && slot.enchantmentLevel < EquipmentComponent.EquipmentData.maxEnchantmentLevel(slot, registries)) {
                upgradeable.add(slot);
            }
        }

        List<Identifier> additions = new ArrayList<>(enchantments.getIds());
        additions.removeIf(id -> !canAdd(id, data, stack, enchantments));
        boolean canUpgrade = !upgradeable.isEmpty();
        boolean canAdd = data.getFilledSlots() < 4 && !additions.isEmpty();
        boolean canLegendary = MaterialTierUpgrader.canPromote(stack, EquipmentCategory.getCategory(stack),
                EquipLevelingConfig.getMaterialTiers());
        if (!canUpgrade && !canAdd && !canLegendary) return null;

        double legendaryChance = Math.clamp(EquipLevelingConfig.getLegendaryUpgradeProbability(), 0.0, 1.0);
        if (canLegendary && (random.nextDouble() < legendaryChance || (!canUpgrade && !canAdd))) {
            return new GeneratedOffer(-1, LEGENDARY);
        }

        double upgradeWeight = Math.max(0.0, EquipLevelingConfig.getUpgradeWeight());
        double newWeight = Math.max(0.0, EquipLevelingConfig.getNewSlotWeight());
        if (canUpgrade && (!canAdd || random.nextDouble() * (upgradeWeight + newWeight) < upgradeWeight)) {
            EquipmentComponent.EquipmentSlot slot = upgradeable.get(random.nextInt(upgradeable.size()));
            return new GeneratedOffer(enchantments.getRawId(enchantments.get(Identifier.of(slot.enchantmentId))),
                    UPGRADE_BASE - (slot.enchantmentLevel + 1));
        }
        if (canAdd) {
            Identifier id = additions.get(random.nextInt(additions.size()));
            return new GeneratedOffer(enchantments.getRawId(enchantments.get(id)), NEW_SLOT);
        }
        EquipmentComponent.EquipmentSlot slot = upgradeable.get(random.nextInt(upgradeable.size()));
        return new GeneratedOffer(enchantments.getRawId(enchantments.get(Identifier.of(slot.enchantmentId))),
                UPGRADE_BASE - (slot.enchantmentLevel + 1));
    }

    private static boolean canAdd(Identifier id, EquipmentComponent.EquipmentData data, ItemStack stack,
            Registry<Enchantment> enchantments) {
        RegistryEntry.Reference<Enchantment> candidate = enchantments.getEntry(id).orElse(null);
        if (candidate == null || candidate.isIn(EnchantmentTags.CURSE) || "minecraft:mending".equals(id.toString())
                || !candidate.value().isAcceptableItem(stack)) return false;
        for (EquipmentComponent.EquipmentSlot existing : allSlots(data)) {
            if (existing.isEmpty()) continue;
            if (id.toString().equals(existing.enchantmentId)) return false;
            try {
                RegistryEntry.Reference<Enchantment> entry = enchantments.getEntry(Identifier.of(existing.enchantmentId)).orElse(null);
                if (entry != null && !Enchantment.canBeCombined(candidate, entry)) return false;
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return true;
    }

    private static boolean addNewSlot(EquipmentComponent.EquipmentData data, String id) {
        if (id == null || data.getFilledSlots() >= 4) return false;
        for (int i = 0; i < data.slots.size(); i++) {
            if (data.slots.get(i).isEmpty()) {
                data.slots.set(i, new EquipmentComponent.EquipmentSlot(id, 1));
                return true;
            }
        }
        return false;
    }

    private static boolean upgradeSlot(EquipmentComponent.EquipmentData data, String id, int targetLevel,
            net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
        if (id == null) return false;
        for (EquipmentComponent.EquipmentSlot slot : allSlots(data)) {
            if (id.equals(slot.enchantmentId)
                    && slot.enchantmentLevel + 1 == targetLevel
                    && slot.enchantmentLevel < EquipmentComponent.EquipmentData.maxEnchantmentLevel(slot, registries)) {
                slot.enchantmentLevel++;
                return true;
            }
        }
        return false;
    }

    private static boolean promote(EnchantmentScreenHandler handler, ItemStack stack) {
        ItemStack promoted = MaterialTierUpgrader.promote(stack, EquipmentCategory.getCategory(stack),
                EquipLevelingConfig.getMaterialTiers());
        if (promoted == stack) return false;
        handler.getSlot(0).setStack(promoted);
        return true;
    }

    private static String idAt(EnchantmentScreenHandler handler, int index, Registry<Enchantment> enchantments) {
        return enchantments.getEntry(handler.enchantmentId[index])
                .flatMap(entry -> entry.getKey().map(key -> key.getValue().toString())).orElse(null);
    }

    private static boolean isCurse(String id, Registry<Enchantment> enchantments) {
        try {
            return enchantments.getEntry(Identifier.of(id)).map(entry -> entry.isIn(EnchantmentTags.CURSE)).orElse(true);
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static List<EquipmentComponent.EquipmentSlot> allSlots(EquipmentComponent.EquipmentData data) {
        List<EquipmentComponent.EquipmentSlot> result = new ArrayList<>(data.slots);
        result.addAll(data.bonusSlots);
        return result;
    }

    private static void clearOffers(EnchantmentScreenHandler handler) {
        Arrays.fill(handler.enchantmentPower, 0);
        Arrays.fill(handler.enchantmentId, -1);
        Arrays.fill(handler.enchantmentLevel, -1);
    }

    private record GeneratedOffer(int enchantmentRawId, int encodedLevel) { }
}
