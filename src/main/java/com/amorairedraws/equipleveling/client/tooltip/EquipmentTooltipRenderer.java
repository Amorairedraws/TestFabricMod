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
		if (!EquipmentCategory.isEquipment(stack) || !stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			return;
		}

		EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);

		// Insert at the top (after the item name)
		int insertIndex = Math.min(1, lines.size());

		// XP bar and its numeric readout occupy separate tooltip rows, matching
		// the vanilla durability-bar presentation.
		lines.add(insertIndex++, renderXpBar(data));
		lines.add(insertIndex++, Text.literal(String.format("(%d/%d) XP", data.xp, data.xpRequired))
				.formatted(Formatting.GRAY));

		// Level or MAX LEVEL
		if (data.maxed) {
			lines.add(insertIndex++, Text.literal("§6MAX LEVEL"));
		} else {
			lines.add(insertIndex++, Text.literal(String.format("Level %d", data.level)).formatted(Formatting.YELLOW));
		}

		// Mending is the completion reward and is intentionally distinct from
		// the two loot-derived bonus slots. It appears at the top of the slot
		// section, before the four standard slots.
		if (data.mending) {
			lines.add(insertIndex++, Text.literal("\uD83D\uDC8E Mending 1").formatted(Formatting.AQUA));
		}

		// Standard slots
		renderSlots(lines, insertIndex, data.slots, "Standard");
		insertIndex += data.slots.size() + 1;

		// Bonus slots
		if (!data.bonusSlots.isEmpty()) {
			lines.add(insertIndex++, Text.literal(""));
			for (EquipmentComponent.EquipmentSlot slot : data.bonusSlots) {
				if (slot.isEmpty()) {
					lines.add(insertIndex++, Text.literal("★ [Empty]").formatted(Formatting.GOLD));
				} else {
					String enchName = slot.enchantmentId.replace("minecraft:", "");
					// Every entry in bonusSlots is loot-derived, even if the loot
					// happened to contain Mending. Automatic completion Mending is
					// rendered separately above and remains cyan.
					lines.add(insertIndex++,
						Text.literal(String.format("★ %s %d", enchName,
							slot.enchantmentLevel)).formatted(Formatting.GOLD));
				}
			}
		}

		// Broken state
		if (data.broken) {
			lines.add(insertIndex, Text.literal("Broken — repair at an Anvil").formatted(Formatting.RED));
		}
	}

	private Text renderXpBar(EquipmentComponent.EquipmentData data) {
		int barLength = 10;
		int filled = data.xpRequired > 0 ? (data.xp * barLength) / data.xpRequired : 0;
		filled = Math.min(filled, barLength);

		StringBuilder bar = new StringBuilder("§8[");
		for (int i = 0; i < barLength; i++) {
			if (i < filled) {
				bar.append("§a█");
			} else {
				bar.append("§7█");
			}
		}
		bar.append("§8]");

		return Text.of(bar.toString());
	}

	private void renderSlots(List<Text> lines, int startIndex, List<EquipmentComponent.EquipmentSlot> slots, 
							 String slotType) {
		if (slots.isEmpty()) {
			lines.add(startIndex, Text.literal(slotType + " Slots: None").formatted(Formatting.GRAY));
		} else {
			lines.add(startIndex, Text.literal(slotType + " Slots:").formatted(Formatting.GRAY));
			for (int i = 0; i < slots.size(); i++) {
				EquipmentComponent.EquipmentSlot slot = slots.get(i);
				if (slot.isEmpty()) {
					lines.add(startIndex + i + 1, Text.literal("  [Empty]").formatted(Formatting.DARK_GRAY));
				} else {
					String enchName = slot.enchantmentId.replace("minecraft:", "");
					lines.add(startIndex + i + 1,
						Text.literal(String.format("  %s %d", enchName, slot.enchantmentLevel)).formatted(Formatting.WHITE));
				}
			}
		}
	}
}
