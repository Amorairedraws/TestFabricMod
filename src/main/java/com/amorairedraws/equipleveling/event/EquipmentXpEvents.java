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
import com.amorairedraws.equipleveling.event.XpDisplay;

public class EquipmentXpEvents {

    /** Called after a living entity actually dies, so XP is never awarded for a hit. */
    public static void awardKillXp(PlayerEntity player, LivingEntity entity,
            net.minecraft.entity.damage.DamageSource source) {
        if (source.getSource() != player) return;
        ItemStack held = player.getMainHandStack();
        String category = EquipmentCategory.getCategory(held);
        if (("sword".equals(category) || "axe".equals(category))) {
            int xp = XpCalculator.calculateEntityKillXp(entity);
            if (EquipmentComponent.addXp(held, xp, player)) {
                XpDisplay.showForPlayer(player, entity.getEntityPos(), xp);
            }
        }
    }


	public static class EntityKillXpHandler implements AttackEntityCallback {
		@Override
		public ActionResult interact(PlayerEntity player, World world, net.minecraft.util.Hand hand, 
									 Entity entity, net.minecraft.util.hit.EntityHitResult hitResult) {
			if (!(entity instanceof LivingEntity living) || living.getHealth() <= 0) return ActionResult.PASS;

			ItemStack heldItem = player.getStackInHand(hand);
			String category = EquipmentCategory.getCategory(heldItem);
			
			if (category != null && (category.equals("sword") || category.equals("axe"))) {
				int xp = XpCalculator.calculateEntityKillXp(living);
				// This callback runs on both logical sides. Never mutate progression on
				// the client; the AFTER_DEATH callback is the sole server reward path.
				// The reward and its floating label are emitted by AFTER_DEATH on
				// the server. This callback must not predict a successful kill.
			}
			
			return ActionResult.PASS;
		}
	}

	public static class BlockBreakXpHandler implements PlayerBlockBreakEvents.After {
		@Override
		public void afterBlockBreak(World world, PlayerEntity player, net.minecraft.util.math.BlockPos pos,
								net.minecraft.block.BlockState state, net.minecraft.block.entity.BlockEntity breakingEntity) {
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
				
				// Progression and its notification are server-authoritative.
				// Showing a client-side prediction here would duplicate the packet
				// emitted after the server accepts the reward.
				if (xp > 0 && !world.isClient() && EquipmentComponent.addXp(heldItem, xp, player)) {
					XpDisplay.showForPlayer(player, net.minecraft.util.math.Vec3d.ofCenter(pos), xp);
				}
			}
			
		}
	}

	public static class DamageXpHandler implements ServerTickEvents.EndTick {
		@Override
		public void onEndTick(net.minecraft.server.MinecraftServer server) {
			// Armor XP is handled via damage events in a separate event handler
		}
	}
}
