package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.event.XpFeedback;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import com.amorairedraws.equipleveling.util.XpCalculator;
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

/**
 * Awards XP only when the vanilla reel operation actually caught a fish.
 * {@code FishingBobberEntity#use} returns: 0 = nothing, 1/2 = a fish was caught
 * (bobber in air vs on ground), 3 = a hooked item, 5 = a hooked mob. Hooking an
 * entity or an item must never grant fishing XP.
 */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {
    @Inject(method = "use", at = @At("RETURN"))
    private void equipLeveling$awardReelXp(CallbackInfoReturnable<Integer> callback) {
        int result = callback.getReturnValue();
        if (result != 1 && result != 2) return;
        FishingBobberEntity bobber = (FishingBobberEntity) (Object) this;
        if (bobber.getOwner() instanceof PlayerEntity player) {
            var rod = player.getMainHandStack();
            if (!"fishing_rod".equals(EquipmentCategory.getCategory(rod))) {
                rod = player.getOffHandStack();
            }
            if ("fishing_rod".equals(EquipmentCategory.getCategory(rod))) {
                int baseXp = 10; // flat XP per caught fish
                double srcMult = EquipLevelingConfig.getSourceMultiplier("fishing");
                int xp = XpCalculator.applyMultipliers(baseXp, srcMult);
                if (!player.getEntityWorld().isClient() && EquipmentComponent.addXp(rod, xp, player)) {
                    XpFeedback.showForPlayer(player, xp);
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
