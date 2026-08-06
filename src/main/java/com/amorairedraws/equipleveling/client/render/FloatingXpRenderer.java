package com.amorairedraws.equipleveling.client.render;

import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Client-side, depth-independent floating XP labels. Events are deliberately
 * short lived and capped so a busy farm cannot create an unbounded queue. */
public final class FloatingXpRenderer {
    private static final ArrayDeque<Label> LABELS = new ArrayDeque<>();
    private static final long LIFETIME_MS = 1500L;

    private FloatingXpRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            render(context.matrices(), client.gameRenderer.getCamera(), context.consumers());
        });
    }

    public static void show(Vec3d position, int amount) {
        if (amount < EquipLevelingConfig.getXpDisplayThreshold()) return;
        synchronized (LABELS) {
            while (LABELS.size() >= 64) LABELS.removeFirst();
            LABELS.addLast(new Label(position, amount, System.currentTimeMillis()));
        }
    }

    private static void render(MatrixStack matrices, Camera camera, VertexConsumerProvider consumers) {
        long now = System.currentTimeMillis();
        Vec3d cameraPos = camera.getCameraPos();
        synchronized (LABELS) {
            Iterator<Label> iterator = LABELS.iterator();
            while (iterator.hasNext()) {
                Label label = iterator.next();
                float progress = (now - label.created) / (float) LIFETIME_MS;
                if (progress >= 1.0f) { iterator.remove(); continue; }
                Vec3d pos = label.position.add(0, progress * 0.8 + 0.5, 0).subtract(cameraPos);
                matrices.push();
                matrices.translate(pos.x, pos.y, pos.z);
                matrices.multiply(camera.getRotation());
                matrices.scale(-0.025f, -0.025f, 0.025f);
                int alpha = Math.max(0, (int) (255 * (1.0f - progress)));
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                String text = "+" + label.amount + " XP";
                int width = MinecraftClient.getInstance().textRenderer.getWidth(text);
                MinecraftClient.getInstance().textRenderer.draw(Text.literal(text), -width / 2f, 0,
                        (alpha << 24) | 0x55FFFF, false, matrix, consumers,
                        TextRenderer.TextLayerType.SEE_THROUGH, 15728880, 0);
                matrices.pop();
            }
        }
    }


    private record Label(Vec3d position, int amount, long created) {}
}
