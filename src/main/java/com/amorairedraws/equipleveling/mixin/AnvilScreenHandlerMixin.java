package com.amorairedraws.equipleveling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;

@Mixin(AnvilScreenHandler.class)
public class AnvilScreenHandlerMixin {

	@Inject(method = "updateResult", at = @At("HEAD"))
	private void modifyAnvilBehavior(CallbackInfo ci) {
		// Prevent combining two equipment items
		AnvilScreenHandler handler = (AnvilScreenHandler) (Object) this;
		ItemStack left = handler.input.getStack(0);
		ItemStack right = handler.input.getStack(1);
		
		if (EquipmentCategory.isEquipment(left) && EquipmentCategory.isEquipment(right)) {
			handler.output.setStack(0, ItemStack.EMPTY);
			return;
		}

		// Handle repair logic
		if (EquipmentCategory.isEquipment(left) && !right.isEmpty()) {
			// Repair with material
			handleEquipmentRepair(handler, left);
		}
	}

	private void handleEquipmentRepair(AnvilScreenHandler handler, ItemStack equipment) {
		if (!equipment.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			return;
		}

		EquipmentComponent.EquipmentData data = equipment.get(EquipmentComponent.EQUIPMENT_TYPE);
		
		// Restore durability based on level
		if (data.broken && EquipLevelingConfig.isBrokenMechanicEnabled()) {
			data.broken = false;
			int maxDurability = equipment.getMaxDamage();
			equipment.setDamage(maxDurability / 2); // Restore 50% durability
		} else {
			int maxDurability = equipment.getMaxDamage();
			int currentDamage = equipment.getDamage();
			int repairAmount = (int) (maxDurability * 0.25); // 25% durability
			equipment.setDamage(Math.max(0, currentDamage - repairAmount));
		}

		// Calculate cost
		int baseCost = EquipLevelingConfig.getAnvilBaseCost();
		int perLevelCost = EquipLevelingConfig.getAnvilPerLevelCost();
		int totalCost = baseCost + (data.level * perLevelCost);
		
		handler.levelCost.set(totalCost);
	}
}
