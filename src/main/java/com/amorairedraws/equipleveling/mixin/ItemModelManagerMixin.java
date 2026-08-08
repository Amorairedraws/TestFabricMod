package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.client.render.BrokenItemRenderState;
import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries the stack's broken state into the cached item render state. */
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {
    @Inject(method = "update", at = @At("HEAD"))
    private void equipLeveling$markBroken(ItemRenderState state, ItemStack stack,
            ItemDisplayContext displayContext, World world, HeldItemContext heldItemContext,
            int seed, CallbackInfo ci) {
        EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
        ((BrokenItemRenderState) state).equipLeveling$setBroken(data != null && data.broken);
    }
}
