package com.amorairedraws.equipleveling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.item.ItemStack;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
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
            boolean glint = data != null && data.readyToLevelUp && !data.broken;
            cir.setReturnValue(glint);
        }
    }

    /**
     * PlayerEntity's convenience overload intentionally clamps damage to
     * maxDamage - 1 so vanilla can consume the stack on the next line.  That
     * clamp would make a persistent broken item impossible: the stack would
     * never reach shouldBreak() without disappearing.  Handle the terminal
     * durability change before vanilla applies that clamp.
     */
    @Inject(method = "damage(ILnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$preventVanillaRemoval(int amount, PlayerEntity player, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!com.amorairedraws.equipleveling.config.EquipLevelingConfig.isBrokenMechanicEnabled()
                || !(player instanceof ServerPlayerEntity serverPlayer)
                || !EquipmentComponent.isTracked(stack) || !stack.isDamageable()
                || stack.get(EquipmentComponent.EQUIPMENT_TYPE) instanceof EquipmentComponent.EquipmentData data
                    && data.broken) return;

        int actualDamage = EnchantmentHelper.getItemDamage(serverPlayer.getEntityWorld(), stack, amount);
        if (actualDamage > 0 && (long) stack.getDamage() + actualDamage >= stack.getMaxDamage()) {
            stack.setDamage(stack.getMaxDamage());
            EquipmentComponent.markBrokenIfNecessary(stack, serverPlayer);
            ci.cancel();
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
            EquipmentComponent.markBrokenIfNecessary(stack, player);
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

    /** A broken tool is still allowed to mine, but only at hand speed. */
    @Inject(method = "getMiningSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void suppressBrokenToolSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
            var data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
            if (data != null && data.broken) cir.setReturnValue(1.0f);
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

    /**
     * A broken tool must not count as a "correct" tool for any block, otherwise
     * it would keep enabling block-specific drops (e.g. cobblestone from stone)
     * that a bare hand cannot produce. Returning false makes the broken item
     * behave exactly like an empty hand for loot purposes.
     */
    @Inject(method = "isSuitableFor", at = @At("HEAD"), cancellable = true)
    private void suppressBrokenToolSuitability(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
            EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
            if (data != null && data.broken) cir.setReturnValue(false);
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

    /**
     * Issue 5: suppress the vanilla enchantment tooltip for our tracked items.
     * The custom slots are already rendered by EquipmentTooltipRenderer, so the
     * vanilla ENCHANTMENTS component must never be appended separately. This
     * works on the client regardless of server-side TOOLTIP_DISPLAY sync.
     */
    @Inject(method = "appendComponentTooltip", at = @At("HEAD"), cancellable = true)
    private <T extends net.minecraft.item.tooltip.TooltipAppender> void suppressVanillaEnchantTooltip(
            net.minecraft.component.ComponentType<T> componentType,
            net.minecraft.item.Item.TooltipContext context,
            net.minecraft.component.type.TooltipDisplayComponent displayComponent,
            java.util.function.Consumer<net.minecraft.text.Text> textConsumer,
            net.minecraft.item.tooltip.TooltipType type,
            CallbackInfo ci) {
        if (componentType == net.minecraft.component.DataComponentTypes.ENCHANTMENTS
                && ((ItemStack) (Object) this).contains(EquipmentComponent.EQUIPMENT_TYPE)) {
            ci.cancel();
        }
    }
}
