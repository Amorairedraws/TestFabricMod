package com.amorairedraws.equipleveling.event;

import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import net.minecraft.util.math.Vec3d;
import java.util.function.BiConsumer;

/** Side-neutral bridge: the server never loads client rendering classes. */
public final class XpDisplay {
    private static BiConsumer<Vec3d, Integer> sink = (position, amount) -> {};
    private XpDisplay() {}
    public static void install(BiConsumer<Vec3d, Integer> clientSink) { sink = clientSink; }
    public static void show(Vec3d position, int amount) {
        // Keep the client-side presentation rule in one place.  Progression is
        // deliberately independent of this threshold: small actions still add
        // XP, they simply do not create noisy floating labels.
        if (amount >= EquipLevelingConfig.getXpDisplayThreshold()) {
            sink.accept(position, amount);
        }
    }
}
