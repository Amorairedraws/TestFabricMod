package com.amorairedraws.equipleveling.client.tooltip;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.util.Formatting;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.util.EquipmentCategory;

import java.util.List;

public class EquipmentTooltipRenderer implements ItemTooltipCallback {

	public void register() {
		ItemTooltipCallback.EVENT.register(this);
	}

	@Override
	public void getTooltip(ItemStack stack, TooltipContext context, TooltipType type, List<Text> lines) {
		// Only render on the client's render thread. Polymer (and other server-side
		// item tooltip sync systems) call getTooltip on the integrated-server thread
		// to bake a snapshot into the synced lore; adding our lines there makes them
		// appear twice (one static copy + one live copy). Guarding on the render
		// thread keeps the tooltip single and live.
		var client = net.minecraft.client.MinecraftClient.getInstance();
		if (client == null || !client.isOnThread()) {
			return;
		}

		if (!EquipmentCategory.isEquipment(stack)) {
			return;
		}

		// Read-only: the tooltip must never mutate the stack (a stack.set() here
		// re-enters the render pipeline and can draw the tooltip twice). Read the
		// stored component directly; if it is absent (a freshly obtained modded
		// item the server has not reconciled yet) render a transient default so
		// the tooltip still appears instead of flickering out.
		EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
		if (data == null) {
			data = EquipmentComponent.EquipmentData.create(EquipmentCategory.getCategory(stack));
		}

		// Insert at the top (after the item name)
		int insertIndex = Math.min(1, lines.size());

		// XP bar and its numeric readout occupy separate tooltip rows.
		lines.add(insertIndex++, renderXpBar(data));

		if (data.maxed) {
			// Issue 5/9: only one MAX LEVEL line, accented with the record
			// glyph (U+23FA) on either side.
			lines.add(insertIndex++, Text.literal("\u23FA MAX LEVEL \u23FA").formatted(Formatting.GOLD));
		} else if (data.readyToLevelUp) {
			lines.add(insertIndex++, Text.literal("Ready to level up!").formatted(Formatting.GREEN));
			// Issue 13: point new players toward the enchanting table.
			lines.add(insertIndex++, Text.literal("Level up at an Enchanting Table").formatted(Formatting.DARK_GREEN));
		} else {
			lines.add(insertIndex++, Text.literal(String.format("(%d/%d) XP", data.xp, data.xpRequired)).formatted(Formatting.GRAY));
		}

		// Level (skipped when maxed; the MAX LEVEL banner above is the marker).
		if (!data.maxed) {
			lines.add(insertIndex++, Text.literal(String.format("Level %d", data.level)).formatted(Formatting.YELLOW));
		}

		// Mending is the completion reward. Use the real enchantment name from
		// the registry (Issue 4) and no emoji prefix.
		if (data.mending) {
			String mendingName = formatEnchantmentName("minecraft:mending", 1, context);
			lines.add(insertIndex++, Text.literal("  " + mendingName).formatted(Formatting.AQUA));
		}

		// Standard slots.  When mending is active the item is at full enchantment
		// capacity — hide any remaining empty slots (Issue: useless [Empty] rows).
		renderSlots(lines, insertIndex, data.slots, context, data.mending);
		insertIndex += data.slots.size() + 1;

		// Bonus slots. Empty bonus slots are hidden entirely (they can never be
		// filled organically, so advertising them as empty is noise). Bonus
		// enchantments render in gold (Issue 6) - no star prefix, just the name.
		for (EquipmentComponent.EquipmentSlot slot : data.bonusSlots) {
			if (slot.isEmpty()) continue;
			String enchName = formatEnchantmentName(slot.enchantmentId, slot.enchantmentLevel, context);
			lines.add(insertIndex++, Text.literal(String.format("  %s", enchName)).formatted(Formatting.GOLD));
		}

		// Broken state
		if (data.broken) {
			lines.add(insertIndex, Text.literal("Broken \u2014 repair with a Repair Kit").formatted(Formatting.RED));
		}
	}

	private Text renderXpBar(EquipmentComponent.EquipmentData data) {
		int barLength = 15;
		int filled = data.xpRequired > 0 ? (data.xp * barLength) / data.xpRequired : 0;
		filled = Math.min(filled, barLength);

		// A flowing bar: empty segments are low block glyphs in grey, filled ones
		// are taller block glyphs in green, wrapped in vertical bars.
		StringBuilder bar = new StringBuilder("\u00a78\u2016");
		for (int i = 0; i < barLength; i++) {
			if (i < filled) {
				bar.append("\u00a7a\u2586");
			} else {
				bar.append("\u00a77\u2582");
			}
		}
		bar.append("\u00a78\u2016");

		return Text.of(bar.toString());
	}

	private static String formatEnchantmentName(String id, int level, TooltipContext context) {
		try {
			if (id != null && context.getRegistryLookup() != null) {
				var key = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ENCHANTMENT,
						net.minecraft.util.Identifier.of(id));
				var entry = context.getRegistryLookup().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT)
						.getOptional(key).orElse(null);
				if (entry != null) return net.minecraft.enchantment.Enchantment.getName(entry, level).getString();
			}
		} catch (RuntimeException ignored) { }
		String name = id == null ? "Unknown" : id.substring(id.indexOf(':') + 1)
				.replace('_', ' ').replace('-', ' ');
		StringBuilder result = new StringBuilder();
		for (String word : name.split("\\s+")) {
			if (word.isEmpty()) continue;
			if (result.length() > 0) result.append(' ');
			result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return result + " " + level;
	}

	private void renderSlots(List<Text> lines, int startIndex, List<EquipmentComponent.EquipmentSlot> slots,
			TooltipContext context, boolean hideEmpty) {
		if (slots.isEmpty()) {
			lines.add(startIndex, Text.literal("Enchantments: None").formatted(Formatting.GRAY));
		} else {
			lines.add(startIndex, Text.literal("Enchantments:").formatted(Formatting.GRAY));
			for (int i = 0; i < slots.size(); i++) {
				EquipmentComponent.EquipmentSlot slot = slots.get(i);
				if (slot.isEmpty()) {
					// When mending is active the item has exhausted every compatible
					// enchantment (either all standard slots filled, or slotsComplete
					// because no more enchantments can be added).  Advertising empty
					// slots is misleading — there are no possible enchantments left.
					if (hideEmpty) continue;
					lines.add(startIndex + i + 1, Text.literal("  [Empty]").formatted(Formatting.DARK_GRAY));
				} else {
					String enchName = formatEnchantmentName(slot.enchantmentId, slot.enchantmentLevel, context);
					lines.add(startIndex + i + 1,
						Text.literal(String.format("  %s", enchName)).formatted(Formatting.WHITE));
				}
			}
		}
	}
}
