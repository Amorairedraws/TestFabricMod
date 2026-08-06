package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Awards XP only when the vanilla reel operation actually caught something. */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {
    @Inject(method = "use", at = @At("RETURN"))
    private void equipLeveling$awardReelXp(CallbackInfoReturnable<Integer> callback) {
        if (callback.getReturnValue() <= 0) return;
        FishingBobberEntity bobber = (FishingBobberEntity) (Object) this;
        if (bobber.getOwner() instanceof PlayerEntity player) {
            var rod = player.getMainHandStack();
            if (!"fishing_rod".equals(EquipmentCategory.getCategory(rod))) {
                rod = player.getOffHandStack();
            }
            if ("fishing_rod".equals(EquipmentCategory.getCategory(rod))) {
                EquipmentComponent.getOrCreate(rod).addXp(callback.getReturnValue() * 10);
            }
        }
    }
}
