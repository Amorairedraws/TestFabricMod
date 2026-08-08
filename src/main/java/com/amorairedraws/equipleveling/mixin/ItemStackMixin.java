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
            // Broken equipment has no active enchantment effects and should not
            // advertise a ready-to-level-up state with a glint.
            cir.setReturnValue(data != null && data.readyToLevelUp && !data.broken);
        }
    }

    /**
     * Vanilla calculates Unbreaking and creative-mode exemptions before calling
     * onDurabilityChange. Intercept at that boundary so the broken mechanic uses
     * the actual post-enchantment damage rather than the raw requested amount.
     */
    @Inject(method = "onDurabilityChange", at = @At("HEAD"), cancellable = true)
    private void preserveBrokenEquipment(int damage, net.minecraft.server.network.ServerPlayerEntity player,
            java.util.function.Consumer<net.minecraft.item.Item> breakCallback, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!com.amorairedraws.equipleveling.config.EquipLevelingConfig.isBrokenMechanicEnabled()
                || !EquipmentComponent.isTracked(stack)) return;
        if (stack.isDamageable() && damage >= stack.getMaxDamage()) {
            stack.setDamage(stack.getMaxDamage());
            EquipmentComponent.markBrokenIfNecessary(stack);
            ci.cancel();
        }
    }

    /** Broken armor and weapons must not contribute their attribute modifiers. */
    @Inject(method = "applyAttributeModifiers", at = @At("HEAD"), cancellable = true)
    private void suppressBrokenAttributes(net.minecraft.entity.EquipmentSlot slot,
            java.util.function.BiConsumer<net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute>,
                net.minecraft.entity.attribute.EntityAttributeModifier> consumer, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) return;
        EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
        if (data != null && data.broken) ci.cancel();
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
