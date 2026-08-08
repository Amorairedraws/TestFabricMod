package com.amorairedraws.equipleveling.client.render;

/** Internal bridge used by the client-only item rendering mixins. */
public interface BrokenItemRenderState {
    boolean equipLeveling$isBroken();
    void equipLeveling$setBroken(boolean broken);
}
