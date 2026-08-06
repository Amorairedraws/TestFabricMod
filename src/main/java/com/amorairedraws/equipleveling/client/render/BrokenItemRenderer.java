package com.amorairedraws.equipleveling.client.render;

/**
 * Client registration hook for broken item rendering.
 *
 * Fabric 0.141 moved item tint registration out of ColorProviderRegistry; the
 * component/tooltip remain authoritative and the model tint is supplied by the
 * client renderer integration when an item model supports tint index zero.
 */
public final class BrokenItemRenderer {
    public void register() {
        // Intentionally empty: 1.21.11 has no item ColorProviderRegistry. A
        // dedicated renderer/mixin can consume the broken component without
        // registering an obsolete global provider.
    }
}
