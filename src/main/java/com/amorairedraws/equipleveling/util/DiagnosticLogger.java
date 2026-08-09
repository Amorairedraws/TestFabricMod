package com.amorairedraws.equipleveling.util;

import com.amorairedraws.equipleveling.EquipLevelingMod;
import net.minecraft.item.ItemStack;

/**
 * Temporary, wide-ranging diagnostic logging to trace why an item's XP /
 * level-up state sometimes "freezes" on the client until a resync action
 * (drop, pick-up, opening a chest/crafting table).
 *
 * <p>Every log line is prefixed with {@code [DIAG]} so it is trivial to grep
 * from the game log. Enable with {@code -Dequipleveling.diag=true} on the
 * JVM (both client and server). When disabled, all calls are near-free no-ops
 * (a single boolean check), so this is safe to leave in place.
 *
 * <p>Each entry records the logical side (CLIENT / SERVER), the slot index if
 * known, and the item's identity (item id + count) plus its EquipmentData
 * state so we can reconstruct exactly what each side saw.
 */
public final class DiagnosticLogger {
    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty("equipleveling.diag", "false"));

    private DiagnosticLogger() {}

    public static boolean enabled() {
        return ENABLED;
    }

    /** A short, stable fingerprint of a stack for correlating log lines. */
    public static String tag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        return stack.getItem().toString() + "x" + stack.getCount();
    }

    /** Human-readable snapshot of the EquipmentData state (or "none"). */
    public static String state(com.amorairedraws.equipleveling.component.EquipmentComponent.EquipmentData d) {
        if (d == null) return "none";
        return "lvl=" + d.level + " xp=" + d.xp + "/" + d.xpRequired
                + " ready=" + d.readyToLevelUp + " maxed=" + d.maxed
                + " broken=" + d.broken + " mending=" + d.mending
                + " slots=" + d.getFilledSlots() + "/" + d.getTotalSlots();
    }

    /** Logs a server-side progression mutation. */
    public static void serverAddXp(ItemStack stack, int slot, int amount,
            com.amorairedraws.equipleveling.component.EquipmentComponent.EquipmentData before,
            com.amorairedraws.equipleveling.component.EquipmentComponent.EquipmentData after) {
        if (!ENABLED) return;
        EquipLevelingMod.LOGGER.info("[DIAG][SERVER] addXp slot={} item={} amount={} before[{}] after[{}]",
                slot, tag(stack), amount, state(before), state(after));
    }

    /** Logs a server-side getOrCreate that changed the stored component. */
    public static void serverGetOrCreateChanged(ItemStack stack, int slot,
            com.amorairedraws.equipleveling.component.EquipmentComponent.EquipmentData before,
            com.amorairedraws.equipleveling.component.EquipmentComponent.EquipmentData after) {
        if (!ENABLED) return;
        EquipLevelingMod.LOGGER.info("[DIAG][SERVER] getOrCreate CHANGED slot={} item={} before[{}] after[{}]",
                slot, tag(stack), state(before), state(after));
    }

    /** Logs a client-side periodic refresh that changed a stack. */
    public static void clientRefreshChanged(ItemStack stack, int slot,
            com.amorairedraws.equipleveling.component.EquipmentComponent.EquipmentData before,
            com.amorairedraws.equipleveling.component.EquipmentComponent.EquipmentData after) {
        if (!ENABLED) return;
        EquipLevelingMod.LOGGER.info("[DIAG][CLIENT] refresh CHANGED slot={} item={} before[{}] after[{}]",
                slot, tag(stack), state(before), state(after));
    }

    /** Logs a client-side tooltip getOrCreate that changed a stack. */
    public static void clientTooltipChanged(ItemStack stack,
            com.amorairedraws.equipleveling.component.EquipmentComponent.EquipmentData before,
            com.amorairedraws.equipleveling.component.EquipmentComponent.EquipmentData after) {
        if (!ENABLED) return;
        EquipLevelingMod.LOGGER.info("[DIAG][CLIENT] tooltip CHANGED item={} before[{}] after[{}]",
                tag(stack), state(before), state(after));
    }

    /** Logs the client's glint decision (a proxy for what the client believes). */
    public static void clientGlint(ItemStack stack, boolean glint) {
        if (!ENABLED) return;
        EquipLevelingMod.LOGGER.info("[DIAG][CLIENT] glint item={} -> {}", tag(stack), glint);
    }

    /** Logs a server-side inventory sync push (the fix path). */
    public static void serverSyncPush(ItemStack stack, int slot) {
        if (!ENABLED) return;
        EquipLevelingMod.LOGGER.info("[DIAG][SERVER] syncPush slot={} item={}", slot, tag(stack));
    }

    /** Logs a server-side restoreEnchantments / markBroken mutation. */
    public static void serverMutate(String what, ItemStack stack, int slot,
            com.amorairedraws.equipleveling.component.EquipmentComponent.EquipmentData before,
            com.amorairedraws.equipleveling.component.EquipmentComponent.EquipmentData after) {
        if (!ENABLED) return;
        EquipLevelingMod.LOGGER.info("[DIAG][SERVER] {} slot={} item={} before[{}] after[{}]",
                what, slot, tag(stack), state(before), state(after));
    }
}
