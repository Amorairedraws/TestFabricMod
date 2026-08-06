package com.amorairedraws.equipleveling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.EnchantmentLevelEntry;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.util.EquipmentCategory;

@Mixin(GrindstoneScreenHandler.class)
public class GrindstoneScreenHandlerMixin {

	@Inject(method = "updateResult", at = @At("HEAD"))
	private void handleGrindstoneLogic(CallbackInfo ci) {
		GrindstoneScreenHandler handler = (GrindstoneScreenHandler) (Object) this;
		ItemStack input = handler.input.getStack(0);
		
		if (EquipmentCategory.isEquipment(input) && input.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			EquipmentComponent.EquipmentData data = input.get(EquipmentComponent.EQUIPMENT_TYPE);
			
			// Calculate XP to return
			int xpToReturn = calculateEnchantmentXp(input);
			
			// Strip component data
			ItemStack output = input.copy();
			output.remove(EquipmentComponent.EQUIPMENT_TYPE);
			
			// Add XP to grindstone output (handled by vanilla)
			handler.output.setStack(0, output);
		}
	}

	private int calculateEnchantmentXp(ItemStack stack) {
		int xp = 0;
		
		for (EnchantmentLevelEntry entry : stack.getEnchantments().getEnchantmentEntries()) {
			// Rough approximation: 10 XP per enchantment level
			xp += entry.level() * 10;
		}
		
		return xp;
	}
}
