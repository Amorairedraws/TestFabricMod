package com.amorairedraws.equipleveling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
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
