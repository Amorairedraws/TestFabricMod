package com.amorairedraws.equipleveling.client.render;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.fabricmc.fabric.api.client.rendering.v1.DrawItemStackOverlayCallback;

/** Makes broken equipment immediately identifiable in inventory and hotbars.
 * The overlay is intentionally registered through Fabric's supported client
 * hook rather than an obsolete ColorProvider API (which cannot tint arbitrary
 * item models in 1.21.11). */
public final class BrokenItemRenderer {
    public void register() {
        DrawItemStackOverlayCallback.EVENT.register((context, textRenderer, stack, x, y) -> {
            if (!stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) return;
            EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
            if (data != null && data.broken) {
                context.fill(x, y, x + 16, y + 16, 0x66FF0000);
            }
        });
    }
}
