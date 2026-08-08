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

    /** Keep tracked equipment as an inventory stack when its last durability point is used.
     *
     * 1.21.11's entity-damage overload returns void. The old ItemStack-returning
     * selector silently failed to protect the item at runtime. Cancel the
     * correctly-mapped overload before vanilla can decrement the stack. */
    @Inject(method = "damage(ILnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/EquipmentSlot;)V", at = @At("HEAD"), cancellable = true)
    private void preserveBrokenEquipment(int amount, net.minecraft.entity.LivingEntity entity,
            net.minecraft.entity.EquipmentSlot slot, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!com.amorairedraws.equipleveling.config.EquipLevelingConfig.isBrokenMechanicEnabled()
                || !EquipmentComponent.isTracked(stack)) return;
        EquipmentComponent.EquipmentData existing = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
        if (existing != null && existing.broken) {
            ci.cancel();
            return;
        }
        if (stack.isDamageable() && stack.getDamage() + amount >= stack.getMaxDamage()) {
            stack.setDamage(stack.getMaxDamage());
            EquipmentComponent.markBrokenIfNecessary(stack);
            ci.cancel();
        }
    }

    /** Keep compatibility with the legacy overload still present in 1.21.11.
     * Some vanilla item paths call this overload rather than the void method. */
    @Inject(method = "damage(ILnet/minecraft/item/ItemConvertible;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void preserveBrokenEquipmentLegacy(int amount, net.minecraft.item.ItemConvertible itemAfterBreaking,
            net.minecraft.entity.LivingEntity entity, net.minecraft.entity.EquipmentSlot slot,
            CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!com.amorairedraws.equipleveling.config.EquipLevelingConfig.isBrokenMechanicEnabled()
                || !EquipmentComponent.isTracked(stack)) return;
        if (stack.isDamageable() && stack.getDamage() + amount >= stack.getMaxDamage()) {
            stack.setDamage(stack.getMaxDamage());
            EquipmentComponent.markBrokenIfNecessary(stack);
            cir.setReturnValue(stack);
        }
    }

    /** Also cover damage caused by dispensers and other non-living sources. */
    @Inject(method = "damage(ILnet/minecraft/server/world/ServerWorld;Lnet/minecraft/server/network/ServerPlayerEntity;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private void preserveBrokenEquipmentFromWorld(int amount, net.minecraft.server.world.ServerWorld world,
            net.minecraft.server.network.ServerPlayerEntity player,
            java.util.function.Consumer<net.minecraft.item.Item> breakCallback, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!com.amorairedraws.equipleveling.config.EquipLevelingConfig.isBrokenMechanicEnabled()
                || !EquipmentComponent.isTracked(stack)) return;
        if (stack.isDamageable() && stack.getDamage() + amount >= stack.getMaxDamage()) {
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
