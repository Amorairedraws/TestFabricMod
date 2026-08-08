package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.client.render.BrokenItemRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores the broken flag alongside the cached client item model state. */
@Mixin(ItemRenderState.class)
public abstract class ItemRenderStateMixin implements BrokenItemRenderState {
    @Unique
    private boolean equipLeveling$broken;

    @Override
    public boolean equipLeveling$isBroken() {
        return equipLeveling$broken;
    }

    @Override
    public void equipLeveling$setBroken(boolean broken) {
        equipLeveling$broken = broken;
    }
}
