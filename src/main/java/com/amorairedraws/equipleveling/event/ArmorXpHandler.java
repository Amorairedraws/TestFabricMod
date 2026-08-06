package com.amorairedraws.equipleveling.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import com.amorairedraws.equipleveling.util.XpCalculator;

public class ArmorXpHandler {

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof PlayerEntity player)) {
			return true;
		}

		// Award XP to armor pieces
		int xp = (int) (amount * 5); // Scale with damage amount
		
		for (int i = 0; i < 4; i++) {
			ItemStack armorPiece = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.values()[i]);
			if (!armorPiece.isEmpty()) {
				String category = EquipmentCategory.getCategory(armorPiece);
				if (category != null && category.matches("helmet|chestplate|leggings|boots")) {
					EquipmentComponent.getOrCreate(armorPiece).addXp(xp);
				}
			}
		}

		return true;
	}
}
