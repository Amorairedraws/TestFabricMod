package com.amorairedraws.equipleveling;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.event.EquipmentXpEvents;
import com.amorairedraws.equipleveling.event.ArmorXpHandler;
import com.amorairedraws.equipleveling.event.DeathEventHandler;
import com.amorairedraws.equipleveling.loot.EquipmentLootModifier;
import com.amorairedraws.equipleveling.network.ConfigSyncPacket;
import com.amorairedraws.equipleveling.util.PlayerBlockTracker;
import com.amorairedraws.equipleveling.util.AutoXpConfigGenerator;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.util.ActionResult;

public class EquipLevelingMod implements ModInitializer {
    public static final String MOD_ID = "equip_leveling";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Equip Leveling!");

        // Load config — auto-generates block XP defaults on first run.
        EquipLevelingConfig.load();

        // Register data component for equipment tracking.
        EquipmentComponent.register();

        // Register config sync packet (S2C).
        PayloadTypeRegistry.playS2C().register(ConfigSyncPacket.ID, ConfigSyncPacket.CODEC);

        // When a player joins a multiplayer server, sync the server config to them.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String configJson = EquipLevelingConfig.toJsonString();
            ServerPlayNetworking.send(handler.getPlayer(), new ConfigSyncPacket(configJson));
            LOGGER.debug("Synced config to {}", handler.getPlayer().getName().getString());
        });

        // Admin config update: allow operators to push new config values.
        // The YACL screen is read-only in multiplayer, so this only fires when
        // an admin edits the config file manually and a reload is triggered.
        // For simplicity, we skip the Live config push from clients for now.
        // Server config changes take effect on next reload or player join.
        // (The server → client sync packet at join already handles the common case.)

        // Keep leveled equipment on death (if enabled).
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!EquipLevelingConfig.isKeepEquipOnDeath() || alive) return;
            for (int i = 0; i < oldPlayer.getInventory().size(); i++) {
                var stack = oldPlayer.getInventory().getStack(i);
                if (EquipmentComponent.isTracked(stack) && stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
                    newPlayer.getInventory().setStack(i, stack.copy());
                }
            }
        });

        // Track player-placed blocks for abuse prevention.
        // When a player places a block (right-click with a block item), record it.
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && player != null && !player.isSpectator()) {
                var heldStack = player.getStackInHand(hand);
                if (!heldStack.isEmpty() && heldStack.getItem() instanceof net.minecraft.item.BlockItem blockItem) {
                    var pos = hitResult.getBlockPos().offset(hitResult.getSide());
                    var placedState = blockItem.getBlock().getDefaultState();
                    PlayerBlockTracker.onBlockPlaced(world, pos, player.getUuid(), placedState);
                }
            }
            return ActionResult.PASS;
        });

        // Register event listeners.
        registerEventListeners();

        // Strip stored-enchantment books from loot and trades.
        LootTableEvents.MODIFY_DROPS.register((entry, context, drops) -> {
            for (int i = drops.size() - 1; i >= 0; i--) {
                var processed = EquipmentLootModifier.processLootItem(drops.get(i),
                        context.getWorld().getRegistryManager(), context.getWorld().getRandom());
                if (processed.isEmpty()) drops.remove(i);
                else drops.set(i, processed);
            }
        });

        // Kill XP: award on actual death, not on hit.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity dead) {
                DeathEventHandler.handlePlayerDeath(dead);
            } else {
                net.minecraft.entity.player.PlayerEntity killer = null;
                if (source.getSource() instanceof net.minecraft.entity.player.PlayerEntity p) killer = p;
                else if (source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity p) killer = p;
                if (killer != null) EquipmentXpEvents.awardKillXp(killer, entity, source);
            }
        });

        LOGGER.info("Equip Leveling initialized successfully!");
    }

    private void registerEventListeners() {
        // Block break → XP award.
        PlayerBlockBreakEvents.AFTER.register(new EquipmentXpEvents.BlockBreakXpHandler());

        // Attack observation (no mutation — reward is from AFTER_DEATH).
        AttackEntityCallback.EVENT.register(new EquipmentXpEvents.EntityKillXpHandler());

        // Per-tick inventory reconciliation.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) {
                for (int i = 0; i < player.getInventory().size(); i++) {
                    var stack = player.getInventory().getStack(i);
                    if (EquipmentComponent.isTracked(stack)) {
                        EquipmentComponent.getOrCreate(stack);
                        EquipmentComponent.restoreEnchantments(stack, player.getEntityWorld().getRegistryManager());
                        EquipmentComponent.markBrokenIfNecessary(stack);
                    }
                }
            }
        });

        // Armor XP from damage.
        ServerLivingEntityEvents.AFTER_DAMAGE.register(ArmorXpHandler::afterDamage);
    }
}
