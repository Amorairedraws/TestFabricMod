package com.amorairedraws.equipleveling.command;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;

/**
 * {@code /elxp <add|set> <targets> <amount>} applies progression XP to the
 * item(s) the targeted player is currently holding. {@code add} accrues XP
 * (respecting the broken/ready/maxed guards); {@code set} overwrites it,
 * clamped to the item's current requirement.
 */
public final class ElxpCommand {
    private ElxpCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("elxp")
                    .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                    .then(CommandManager.literal("add")
                            .then(CommandManager.argument("targets", EntityArgumentType.players())
                                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                            .executes(ctx -> run(ctx, false)))))
                    .then(CommandManager.literal("set")
                            .then(CommandManager.argument("targets", EntityArgumentType.players())
                                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                            .executes(ctx -> run(ctx, true))))));
        });
    }

    private static int run(CommandContext<ServerCommandSource> ctx, boolean set)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        int affected = 0;
        for (ServerPlayerEntity player : targets) {
            ItemStack held = heldEquipment(player);
            if (held == null) continue;
            boolean ok = set
                    ? EquipmentComponent.setXp(held, amount, player)
                    : EquipmentComponent.addXp(held, amount, player);
            if (ok) affected++;
        }
        final int count = affected;
        String verb = set ? "set" : "added";
        ctx.getSource().sendFeedback(() -> Text.literal(String.format(
                "%s %d XP on %d held item(s).", verb, amount, count)), true);
        return affected;
    }

    private static ItemStack heldEquipment(ServerPlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (EquipmentComponent.isTracked(main)) return main;
        ItemStack off = player.getOffHandStack();
        if (EquipmentComponent.isTracked(off)) return off;
        return null;
    }
}
