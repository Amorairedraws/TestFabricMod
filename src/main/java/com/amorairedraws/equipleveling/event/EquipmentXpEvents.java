package com.amorairedraws.equipleveling.event;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import com.amorairedraws.equipleveling.util.XpCalculator;

public class EquipmentXpEvents {

    /** Called after a living entity actually dies, so XP is never awarded for a hit. */
    public static void awardKillXp(PlayerEntity player, LivingEntity entity) {
        ItemStack held = player.getMainHandStack();
        String category = EquipmentCategory.getCategory(held);
        if (("sword".equals(category) || "axe".equals(category))) {
            EquipmentComponent.addXp(held, XpCalculator.calculateEntityKillXp(entity));
        }
    }


	public static class EntityKillXpHandler implements AttackEntityCallback {
		@Override
		public ActionResult interact(PlayerEntity player, World world, net.minecraft.util.Hand hand, 
									 Entity entity, net.minecraft.util.hit.EntityHitResult hitResult) {
			if (world.isClient() || !(entity instanceof LivingEntity living) || living.getHealth() <= 0) return ActionResult.PASS;

			ItemStack heldItem = player.getStackInHand(hand);
			String category = EquipmentCategory.getCategory(heldItem);
			
			if (category != null && (category.equals("sword") || category.equals("axe"))) {
				int xp = XpCalculator.calculateEntityKillXp(living);
				EquipmentComponent.addXp(heldItem, xp);
			}
			
			return ActionResult.PASS;
		}
	}

	public static class BlockBreakXpHandler implements PlayerBlockBreakEvents.Before {
		@Override
		public boolean beforeBlockBreak(World world, PlayerEntity player, net.minecraft.util.math.BlockPos pos,
										net.minecraft.block.BlockState state, net.minecraft.block.entity.BlockEntity breakingEntity) {
			if (world.isClient()) return true;

			ItemStack heldItem = player.getMainHandStack();
			String category = EquipmentCategory.getCategory(heldItem);
			
			if (category != null) {
				int xp = 0;
				switch (category) {
					case "pickaxe" -> xp = XpCalculator.calculateOreXp(state);
					case "axe" -> xp = XpCalculator.calculateLogXp(state);
					case "shovel" -> xp = XpCalculator.calculateShovelXp(state);
					case "hoe" -> xp = XpCalculator.calculateHoeXp(state);
				}
				
				if (xp > 0) {
					EquipmentComponent.addXp(heldItem, xp);
				}
			}
			
			return true;
		}
	}

	public static class DamageXpHandler implements ServerTickEvents.EndTick {
		@Override
		public void onEndTick(net.minecraft.server.MinecraftServer server) {
			// Armor XP is handled via damage events in a separate event handler
		}
	}
}
