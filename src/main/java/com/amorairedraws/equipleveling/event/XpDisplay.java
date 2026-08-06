package com.amorairedraws.equipleveling.event;

import net.minecraft.util.math.Vec3d;
import java.util.function.BiConsumer;

/** Side-neutral bridge: the server never loads client rendering classes. */
public final class XpDisplay {
    private static BiConsumer<Vec3d, Integer> sink = (position, amount) -> {};
    private XpDisplay() {}
    public static void install(BiConsumer<Vec3d, Integer> clientSink) { sink = clientSink; }
    public static void show(Vec3d position, int amount) { sink.accept(position, amount); }
}
