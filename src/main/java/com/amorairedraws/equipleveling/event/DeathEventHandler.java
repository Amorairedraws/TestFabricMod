package com.amorairedraws.equipleveling.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;

public class DeathEventHandler implements ServerEntityEvents.AllowDamage {

	public static void handlePlayerDeath(PlayerEntity player) {
		if (!EquipLevelingConfig.isKeepEquipOnDeath()) {
			return;
		}

		// Keep equipment items in inventory on death
		for (ItemStack stack : player.getInventory().main) {
			if (EquipmentCategory.isEquipment(stack) && stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
				stack.setNbt(stack.getNbt()); // Mark to keep
			}
		}

		// Also keep armor
		for (ItemStack stack : player.getInventory().armor) {
			if (EquipmentCategory.isEquipment(stack) && stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
				stack.setNbt(stack.getNbt()); // Mark to keep
			}
		}
	}

	@Override
	public ActionResult allowDamage(net.minecraft.entity.LivingEntity entity, 
									net.minecraft.entity.damage.DamageSource source, float amount) {
		return ActionResult.PASS;
	}
}
