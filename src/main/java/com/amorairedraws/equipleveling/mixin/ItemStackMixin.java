package com.amorairedraws.equipleveling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.item.ItemStack;
import net.minecraft.block.BlockState;
import net.minecraft.text.Text;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.util.Formatting;

import com.amorairedraws.equipleveling.component.EquipmentComponent;

@Mixin(ItemStack.class)
public class ItemStackMixin {

	@Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
	private void modifyGlint(CallbackInfoReturnable<Boolean> cir) {
		ItemStack stack = (ItemStack) (Object) this;
		
		if (stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
			// Only show glint when ready to level up
			cir.setReturnValue(data.readyToLevelUp);
		}
	}

	/** Keep tracked equipment as an inventory stack when its last durability point is used.
	 * Vanilla decrements the stack on break; setting damage to max and cancelling here
	 * gives the component scanner a durable broken item to work with instead. */
	@Inject(method = "damage(ILnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/EquipmentSlot;)V", at = @At("HEAD"), cancellable = true)
	private void preserveBrokenEquipment(int amount, net.minecraft.entity.LivingEntity entity,
			net.minecraft.entity.EquipmentSlot slot, CallbackInfo ci) {
		ItemStack stack = (ItemStack) (Object) this;
		if (!com.amorairedraws.equipleveling.config.EquipLevelingConfig.isBrokenMechanicEnabled()
				|| !EquipmentComponent.isTracked(stack) || stack.contains(EquipmentComponent.EQUIPMENT_TYPE)
				&& stack.get(EquipmentComponent.EQUIPMENT_TYPE).broken) return;
		if (stack.isDamageable() && stack.getDamage() + amount >= stack.getMaxDamage()) {
			stack.setDamage(stack.getMaxDamage());
			EquipmentComponent.markBrokenIfNecessary(stack);
			ci.cancel();
		}
	}

	@Inject(method = "getMiningSpeedMultiplier", at = @At("HEAD"), cancellable = true)
	private void suppressBrokenToolSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
		ItemStack stack = (ItemStack) (Object) this;
		if (stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			var data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
			if (data != null && data.broken) cir.setReturnValue(0.0f);
		}
	}

	@Inject(method = "canMine", at = @At("HEAD"), cancellable = true)
	private void suppressBrokenMining(BlockState state, net.minecraft.world.World world,
			net.minecraft.util.math.BlockPos pos, net.minecraft.entity.player.PlayerEntity player,
			CallbackInfoReturnable<Boolean> cir) {
		ItemStack stack = (ItemStack) (Object) this;
		if (stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			var data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
			if (data != null && data.broken) cir.setReturnValue(false);
		}
	}

	@Inject(method = "getEnchantments", at = @At("HEAD"), cancellable = true)
	private void suppressBrokenEnchantments(CallbackInfoReturnable<ItemEnchantmentsComponent> cir) {
		ItemStack stack = (ItemStack) (Object) this;
		if (stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
			if (data != null && data.broken) cir.setReturnValue(ItemEnchantmentsComponent.DEFAULT);
		}
	}

	@Inject(method = "getName", at = @At("RETURN"), cancellable = true)
	private void addBrokenPrefix(CallbackInfoReturnable<Text> cir) {
		ItemStack stack = (ItemStack) (Object) this;
		if (stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
			if (data != null && data.broken) {
				cir.setReturnValue(Text.literal("[BROKEN] ").formatted(Formatting.RED).append(cir.getReturnValue()));
			}
		}
	}
}
