package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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
                int xp = callback.getReturnValue() * 10;
                if (player.getEntityWorld().isClient()) {
                    com.amorairedraws.equipleveling.event.XpDisplay.show(bobber.getEntityPos(), xp);
                } else if (EquipmentComponent.addXp(rod, xp)) {
                    com.amorairedraws.equipleveling.event.XpDisplay.showForPlayer(player, bobber.getEntityPos(), xp);
                }
            }
        }
    }

    /** Fishing loot is generated directly from the fishing table and does not
     * pass through the normal loot-drop callback. Replace forbidden books before
     * the item entity is constructed so enchanted books cannot be fished. */
    @ModifyArg(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/ItemEntity;<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/item/ItemStack;)V"), index = 4)
    private ItemStack equipLeveling$removeFishedBooks(ItemStack stack) {
        return stack.isOf(Items.ENCHANTED_BOOK)
                || stack.contains(DataComponentTypes.STORED_ENCHANTMENTS) ? ItemStack.EMPTY : stack;
    }
}
