package com.amorairedraws.equipleveling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

import com.amorairedraws.equipleveling.component.EquipmentComponent;

@Mixin(ItemStack.class)
public class ItemStackClientMixin {

	@Inject(method = "setCustomName", at = @At("HEAD"), cancellable = true)
	private void modifyName(Text text, CallbackInfo ci) {
		ItemStack stack = (ItemStack) (Object) this;
		
		if (stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
			
			if (data.broken) {
				Text broken = Text.literal("[BROKEN] ").withStyle(Style.EMPTY.withColor(0xFF0000));
				Text newName = broken.copy().append(text);
				stack.setCustomName(newName);
			}
		}
	}
}
