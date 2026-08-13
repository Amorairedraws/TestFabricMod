package com.amorairedraws.equipleveling.screen;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import com.amorairedraws.equipleveling.util.MaterialHelper;
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
            forceSyncInput(handler, player);
            return;
        }

        // Issue 1: if offers were already rolled and persisted on the item, restore
        // them instead of re-rolling — but NEVER restore legendary offers.
        // Legendaries are a flat % chance and must be re-rolled fresh every time.
        if (restoreStoredOffers(handler, data, enchantments)) {
            handler.sendContentUpdates();
            return;
        }

        // Issue 8: never offer the same enchantment twice across the three rows.
        java.util.Set<String> usedEnchantments = new java.util.HashSet<>();
        List<GeneratedOffer> offers = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            GeneratedOffer offer = generateOffer(data, stack, enchantments, registries, random, usedEnchantments);
            if (offer == null) continue;
            // Record the enchantment id so later offers avoid it.
            String offeredId = offer.enchantmentRawId >= 0
                    ? enchantments.getEntry(offer.enchantmentRawId)
                            .flatMap(e -> e.getKey().map(k -> k.getValue().toString())).orElse(null)
                    : null;
            if (offeredId != null) usedEnchantments.add(offeredId);
            offers.add(offer);
        }

        // Issue 8: if no more compatible enchantments can be added, mark the
        // standard slots complete so mending / MAX LEVEL can still be reached
        // even when the configured slot cap is higher than what the item supports.
        if (data.getFilledSlots() < data.maxSlots && !canAddAnyMore(data, stack, enchantments)) {
            data.slotsComplete = true;
            data.refresh(EquipmentCategory.getCategory(stack));
            data.updateMaxed(registries, MaterialTierUpgrader.isTierLevelSatisfied(stack, data.level,
                    EquipLevelingConfig.getMaterialTiers()));
            stack.set(EquipmentComponent.EQUIPMENT_TYPE, data);
        }

        // Issue 11: legendary is a flat percentage pass that replaces one offer,
        // independent of the weight pool. This keeps it a rare, separate event
        // rather than competing with upgrade/new weights.
        boolean canLegendary = MaterialTierUpgrader.canPromote(stack, EquipmentCategory.getCategory(stack),
                EquipLevelingConfig.getMaterialTiers());
        if (canLegendary && !offers.isEmpty()
                && random.nextDouble() < EquipLevelingConfig.getLegendaryUpgradeProbability()) {
            String target = MaterialTierUpgrader.pickTargetMaterial(stack,
                    EquipmentCategory.getCategory(stack));
            offers.set(random.nextInt(offers.size()), new GeneratedOffer(-1, LEGENDARY, target));
        }

        // Issue 2: shuffle the offer positions so it's random which slot holds
        // which offer, avoiding a bias where later slots are more likely to be
        // new enchantments or upgrades.
        java.util.Collections.shuffle(offers, new java.util.Random(random.nextLong()));

        // Persist the rolled offers so they survive item removal/re-insertion.
        data.offers = new ArrayList<>();
        for (GeneratedOffer offer : offers) {
            String id = offer.enchantmentRawId >= 0
                    ? enchantments.getEntry(offer.enchantmentRawId)
                            .flatMap(e -> e.getKey().map(k -> k.getValue().toString())).orElse(null)
                    : null;
            // For legendary (material upgrade) offers, persist the chosen target
            // material in the id field so the client can advertise it and the
            // promotion applies exactly that material.
            String storedId = offer.encodedLevel == LEGENDARY ? offer.material : id;
            data.offers.add(new EquipmentComponent.StoredOffer(storedId, offer.encodedLevel));
        }
        stack.set(EquipmentComponent.EQUIPMENT_TYPE, data);

        applyOffersToHandler(handler, offers);
        handler.sendContentUpdates();
        forceSyncInput(handler, player);
    }

    /** Restores previously persisted offers into the handler arrays. Returns true
     * when the stored offers were valid and applied.
     *
     * <p>Legendary (material upgrade) offers have {@code enchantmentId == null}
     * and {@code encodedLevel == LEGENDARY}. They are restored as-is so the
     * material upgrade persists across item re-insertions just like the other
     * two offer types (Issue 4). We never re-roll the legendary component
     * during a restore — the flat 5 % pass in {@link #generateOffers} is the
     * only place legendary rolls happen.</p> */
    private static boolean restoreStoredOffers(EnchantmentScreenHandler handler,
            EquipmentComponent.EquipmentData data, Registry<Enchantment> enchantments) {
        if (data.offers == null || data.offers.isEmpty()) return false;
        List<GeneratedOffer> offers = new ArrayList<>();
        for (EquipmentComponent.StoredOffer stored : data.offers) {
            // Legendary (material upgrade) offers have a null enchantment id and
            // the special LEGENDARY encoded level. Restore them as-is.
            if (stored.encodedLevel == LEGENDARY) {
                offers.add(new GeneratedOffer(-1, LEGENDARY, stored.enchantmentId));
                continue;
            }
            if (stored.enchantmentId == null) continue;
            try {
                Enchantment ench = enchantments.get(Identifier.of(stored.enchantmentId));
                if (ench == null) return false;
                offers.add(new GeneratedOffer(enchantments.getRawId(ench), stored.encodedLevel));
            } catch (RuntimeException e) {
                return false;
            }
        }
        if (offers.isEmpty()) return false;
        applyOffersToHandler(handler, offers);
        return true;
    }

    private static void applyOffersToHandler(EnchantmentScreenHandler handler, List<GeneratedOffer> offers) {
        for (int index = 0; index < 3; index++) {
            if (index < offers.size()) {
                GeneratedOffer offer = offers.get(index);
                handler.enchantmentPower[index] = 1; // active marker; leveling itself has no XP-level cost
                handler.enchantmentId[index] = offer.enchantmentRawId;
                handler.enchantmentLevel[index] = offer.encodedLevel;
            }
        }
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
            case LEGENDARY -> promote(handler, stack, index);
            case NONE -> false;
        };
        if (!applied) return false;

        // A legendary promotion replaces the stack in the existing vanilla input
        // slot. Re-read the component instead of retaining stale data from the
        // old material. Also explicitly set the stack on the slot to force an
        // immediate sync, so the glint (readyToLevelUp) clears without delay.
        stack = handler.getSlot(0).getStack();
        data = EquipmentComponent.getOrCreate(stack);
        int restored = (int) Math.round(stack.getMaxDamage()
                * (EquipLevelingConfig.getDurabilityRestorePercent() / 100.0));
        if (stack.isDamageable() && restored > 0) {
            stack.setDamage(Math.max(0, stack.getDamage() - restored));
        }

        data.levelUp(EquipmentCategory.getCategory(stack));
        // Issue 6: leveling up restores durability, so clear any stale broken flag.
        data.broken = false;
        // Issue 1: a selection consumes the offers; the next generateOffers call
        // (triggered by markDirty) rolls a fresh set for the new level.
        data.offers.clear();
        stack.set(EquipmentComponent.EQUIPMENT_TYPE, data);
        EquipmentComponent.restoreEnchantments(stack, registries);
        handler.getSlot(0).setStack(stack); // force sync the modified stack
        handler.getSlot(0).markDirty();
        // Issue 12: celebratory sound when an enchantment is applied. Played at
        // the player's position on the server so it reaches the client.
        if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            serverWorld.playSound(null, player.getBlockPos(),
                    net.minecraft.sound.SoundEvents.ITEM_TRIDENT_RETURN,
                    net.minecraft.sound.SoundCategory.MASTER, 1.0F, 1.0F);
        }
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
        // Issue 1: a reroll explicitly discards the persisted offers so the next
        // generateOffers call rolls a fresh set.
        data.offers.clear();
        stack.set(EquipmentComponent.EQUIPMENT_TYPE, data);
        generateOffers(handler, player, random);
        return true;
    }

    /**
     * Reroll cost is priced off the current offers (Issue 5). Each offer
     * contributes its vanilla base anvil cost (weight) - both for upgrades and
     * new enchantments - plus a flat 10 for a legendary (material) upgrade. The
     * weapon's current level is then added on top, so rerolling gets more
     * expensive as the weapon levels up. The displayed number is the sum across
     * all three rows plus the weapon level.
     */
    public static int getRerollCost(ItemStack stack, EnchantmentScreenHandler handler,
            Registry<Enchantment> enchantments) {
        if (!EquipmentComponent.isTracked(stack) || !stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) return 0;
        int total = 0;
        for (int i = 0; i < 3; i++) {
            OfferKind kind = getOfferKind(handler, i);
            if (kind == OfferKind.NONE) continue;
            if (kind == OfferKind.LEGENDARY) {
                total += 10; // material upgrade fixed cost
                continue;
            }
            int rawId = handler.enchantmentId[i];
            int weight = enchantments.getEntry(rawId)
                    .map(entry -> Math.max(0, entry.value().getAnvilCost())).orElse(0);
            // Issue 5: use the base anvil cost for both upgrades and new
            // enchantments - no multiplication by target level.
            total += weight;
        }
        // The weapon's level is added onto the reroll price (Issue 5).
        EquipmentComponent.EquipmentData data = EquipmentComponent.getOrCreate(stack);
        total += Math.max(0, data.level);
        return Math.max(1, total);
    }

    /** Fallback label that needs no registry, used when the client world is not
     * available during rendering. Produces a readable description from the
     * encoded offer data alone. */
    public static String describeOfferFallback(EnchantmentScreenHandler handler, int index) {
        OfferKind kind = getOfferKind(handler, index);
        if (kind == OfferKind.LEGENDARY) return "Material Upgrade";
        if (kind == OfferKind.NONE) return "";
        if (kind == OfferKind.UPGRADE) return "Upgrade Enchantment";
        return "New Enchantment";
    }

    public static String describeOffer(EnchantmentScreenHandler handler, int index,
            Registry<Enchantment> enchantments) {
        OfferKind kind = getOfferKind(handler, index);
        if (kind == OfferKind.LEGENDARY) return "Material Upgrade";
        if (kind == OfferKind.NONE) return "";
        String name = enchantments.getEntry(handler.enchantmentId[index])
                .map(entry -> Enchantment.getName(entry,
                        kind == OfferKind.UPGRADE ? getUpgradeTargetLevel(handler, index) : 1).getString())
                .orElse("Unknown enchantment");
        return name;
    }

    /** The darker second line shown under the offer title (Issue 2).
     * For legendary offers, includes the target material. */
    public static String describeOfferSubtitle(EnchantmentScreenHandler handler, int index,
            net.minecraft.item.ItemStack stack) {
        OfferKind kind = getOfferKind(handler, index);
        if (kind == OfferKind.LEGENDARY) {
            // Advertise the exact material that was rolled when the offer was
            // generated (persisted on the item), rather than the first entry of
            // the next tier which may differ from the material actually applied.
            String target = legendaryTargetAt(stack, index);
            if (target != null && !target.isEmpty()) {
                return "Upgrade \u2192 " + MaterialHelper.displayName(target);
            }
            return "Legendary Upgrade";
        }
        if (kind == OfferKind.UPGRADE) return "Upgrade Enchantment";
        if (kind == OfferKind.NEW_ENCHANTMENT) return "New Enchantment";
        return "";
    }

    private static String legendaryTargetAt(net.minecraft.item.ItemStack stack, int index) {
        EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
        if (data == null || data.offers == null || index < 0 || index >= data.offers.size()) return null;
        EquipmentComponent.StoredOffer offer = data.offers.get(index);
        return offer.encodedLevel == LEGENDARY ? offer.enchantmentId : null;
    }

    private static GeneratedOffer generateOffer(EquipmentComponent.EquipmentData data, ItemStack stack,
            Registry<Enchantment> enchantments, net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
            Random random, java.util.Set<String> usedEnchantments) {
        List<EquipmentComponent.EquipmentSlot> upgradeable = new ArrayList<>();
        for (EquipmentComponent.EquipmentSlot slot : allSlots(data)) {
            if (!slot.isEmpty() && !isCurse(slot.enchantmentId, enchantments)
                    && slot.enchantmentLevel < EquipmentComponent.EquipmentData.maxEnchantmentLevel(slot, registries)
                    && !usedEnchantments.contains(slot.enchantmentId)) {
                upgradeable.add(slot);
            }
        }

        List<Identifier> additions = new ArrayList<>(enchantments.getIds());
        additions.removeIf(id -> usedEnchantments.contains(id.toString()) || !canAdd(id, data, stack, enchantments));
        boolean canUpgrade = !upgradeable.isEmpty();
        boolean canAdd = data.getFilledSlots() < data.maxSlots && !additions.isEmpty();
        if (!canUpgrade && !canAdd) return null;

        // Weighted selection between upgrade and new-enchantment offers only.
        // Legendary is handled separately as a flat percentage pass in
        // generateOffers (Issue 11), so it never competes with these weights.
        double upgradeWeight = Math.max(0.0, EquipLevelingConfig.getUpgradeWeight());
        double newWeight = Math.max(0.0, EquipLevelingConfig.getNewSlotWeight());

        double total = 0.0;
        if (canUpgrade) total += upgradeWeight;
        if (canAdd) total += newWeight;
        if (total <= 0.0) {
            // No weights configured; fall back to any available offer type.
            if (canUpgrade) return pickUpgrade(upgradeable, enchantments, random);
            return pickNew(additions, enchantments, random);
        }

        double roll = random.nextDouble() * total;
        if (canUpgrade) {
            roll -= upgradeWeight;
            if (roll < 0.0) return pickUpgrade(upgradeable, enchantments, random);
        }
        return pickNew(additions, enchantments, random);
    }

    private static GeneratedOffer pickUpgrade(List<EquipmentComponent.EquipmentSlot> upgradeable,
            Registry<Enchantment> enchantments, Random random) {
        EquipmentComponent.EquipmentSlot slot = upgradeable.get(random.nextInt(upgradeable.size()));
        return new GeneratedOffer(enchantments.getRawId(enchantments.get(Identifier.of(slot.enchantmentId))),
                UPGRADE_BASE - (slot.enchantmentLevel + 1));
    }

    private static GeneratedOffer pickNew(List<Identifier> additions,
            Registry<Enchantment> enchantments, Random random) {
        Identifier id = additions.get(random.nextInt(additions.size()));
        return new GeneratedOffer(enchantments.getRawId(enchantments.get(id)), NEW_SLOT);
    }

    /** Returns true if at least one compatible enchantment can still be added to
     * a currently-empty standard slot. Used to mark slotsComplete (Issue 8). */
    private static boolean canAddAnyMore(EquipmentComponent.EquipmentData data, ItemStack stack,
            Registry<Enchantment> enchantments) {
        if (data.getFilledSlots() >= data.maxSlots) return false;
        for (Identifier id : enchantments.getIds()) {
            if (canAdd(id, data, stack, enchantments)) return true;
        }
        return false;
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
        if (id == null || data.getFilledSlots() >= data.maxSlots) return false;
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

    private static boolean promote(EnchantmentScreenHandler handler, ItemStack stack, int index) {
        String target = null;
        EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
        if (data != null && data.offers != null && index >= 0 && index < data.offers.size()) {
            EquipmentComponent.StoredOffer offer = data.offers.get(index);
            if (offer.encodedLevel == LEGENDARY) target = offer.enchantmentId;
        }
        ItemStack promoted = MaterialTierUpgrader.promoteTo(stack, EquipmentCategory.getCategory(stack), target);
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

    /**
     * Pushes the enchanting table's input slot directly to the client. The
     * handler's own sendContentUpdates() uses a hash-based change detector that
     * does not reliably notice a change to our custom data component, so the
     * client's copy of the item can be missing its {@code equipment} component
     * even though offers are visible. That leaves the reroll button permanently
     * disabled (its affordability check reads the component). Sending the slot
     * update explicitly keeps the client's stack in sync.
     */
    private static void forceSyncInput(EnchantmentScreenHandler handler, PlayerEntity player) {
        if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return;
        ItemStack stack = handler.getSlot(0).getStack();
        if (stack.isEmpty()) return;
        sp.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket(
                handler.syncId, handler.nextRevision(), 0, stack));
    }

    private record GeneratedOffer(int enchantmentRawId, int encodedLevel, String material) {
        GeneratedOffer(int enchantmentRawId, int encodedLevel) {
            this(enchantmentRawId, encodedLevel, null);
        }
    }
}
