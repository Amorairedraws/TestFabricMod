package com.amorairedraws.equipleveling;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.ConfigSerializer;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.item.ModItems;
import com.amorairedraws.equipleveling.recipe.RepairEquipmentRecipe;
import com.amorairedraws.equipleveling.event.EquipmentXpEvents;
import com.amorairedraws.equipleveling.event.ArmorXpHandler;
import com.amorairedraws.equipleveling.loot.EquipmentLootModifier;
import com.amorairedraws.equipleveling.network.ConfigSyncPacket;
import com.amorairedraws.equipleveling.util.PlayerBlockTracker;
import com.amorairedraws.equipleveling.util.AutoXpConfigGenerator;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

public class EquipLevelingMod implements ModInitializer {
    public static final String MOD_ID = "equip_leveling";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Recipe-book unlock targets. A repair kit should be discoverable as soon as
    // the player has any of its ingredients; the diamond kit once they have a diamond.
    private static final RegistryKey<Recipe<?>> REPAIR_KIT_RECIPE =
            RegistryKey.of(RegistryKeys.RECIPE, Identifier.of(MOD_ID, "repair_kit"));
    private static final RegistryKey<Recipe<?>> DIAMOND_REPAIR_KIT_RECIPE =
            RegistryKey.of(RegistryKeys.RECIPE, Identifier.of(MOD_ID, "diamond_repair_kit"));
    private static final Item[] REPAIR_KIT_INGREDIENTS = {
            Items.COPPER_INGOT, Items.IRON_INGOT, Items.FLINT, Items.LEATHER
    };
    private static int recipeUnlockTick;
    private static int reconciliationTick;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Equip Leveling!");

        // Load config — auto-generates block XP defaults on first run.
        ConfigSerializer.load();

        // Recompute the auto-derived material ladder once the item registry and
        // tags are settled (also covers mid-session datapack /reload).
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                EquipLevelingConfig.invalidateMaterialCache());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) ->
                EquipLevelingConfig.invalidateMaterialCache());

        // Register data component for equipment tracking.
        EquipmentComponent.register();

        // Register custom items and recipes (repair kit).
        ModItems.init();
        RepairEquipmentRecipe.init();

        // Register the /elxp command (admin/testing XP control).
        com.amorairedraws.equipleveling.command.ElxpCommand.register();

        // Register config sync packet (S2C).
        PayloadTypeRegistry.playS2C().register(ConfigSyncPacket.ID, ConfigSyncPacket.CODEC);

        // When a player joins a multiplayer server, sync the server config to them.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String configJson = ConfigSerializer.toJsonString();
            ServerPlayNetworking.send(handler.getPlayer(), new ConfigSyncPacket(configJson));
            LOGGER.debug("Synced config to {}", handler.getPlayer().getName().getString());
        });

        // Admin config update: allow operators to push new config values.
        // The YACL screen is read-only in multiplayer, so this only fires when
        // an admin edits the config file manually and a reload is triggered.
        // For simplicity, we skip the Live config push from clients for now.
        // Server config changes take effect on next reload or player join.
        // (The server → client sync packet at join already handles the common case.)

        // Keep leveled equipment on death (if enabled). This works in two parts:
        // 1. PlayerEntityMixin#dropInventory hides tracked items so vanilla doesn't
        //    drop them as entities, then restores them to the dead player's inventory.
        // 2. Vanilla's copyFrom skips the inventory for a death respawn (alive=false),
        //    so this handler copies the restored tracked items onto the new player.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!EquipLevelingConfig.isKeepEquipOnDeath() || alive) return;
            for (int i = 0; i < oldPlayer.getInventory().size(); i++) {
                var stack = oldPlayer.getInventory().getStack(i);
                if (EquipmentComponent.isTracked(stack) && stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
                    newPlayer.getInventory().setStack(i, stack.copy());
                }
            }
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

        // Kill XP: award on actual death, not on hit. Player deaths are skipped
        // here (equipment-keeping on death is handled by PlayerEntityMixin plus
        // the COPY_FROM handler above).
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) return;
            net.minecraft.entity.player.PlayerEntity killer = null;
            if (source.getSource() instanceof net.minecraft.entity.player.PlayerEntity p) killer = p;
            else if (source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity p) killer = p;
            if (killer != null) EquipmentXpEvents.awardKillXp(killer, entity, source);
        });

        LOGGER.info("Equip Leveling initialized successfully!");
    }

    private void registerEventListeners() {
        // Block break → XP award.
        PlayerBlockBreakEvents.AFTER.register(new EquipmentXpEvents.BlockBreakXpHandler());

        // Attack observation (no mutation — reward is from AFTER_DEATH).
        AttackEntityCallback.EVENT.register(new EquipmentXpEvents.EntityKillXpHandler());

        // Inventory reconciliation + recipe-book unlock. Reconciliation is throttled
        // to every 40 ticks (2 s) to match the client, since it only exists to catch
        // tracked items obtained outside the normal award paths (creative, commands,
        // /give) and to mirror custom slots into vanilla enchantments; neither needs
        // to run every tick.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            boolean checkUnlocks = ++recipeUnlockTick >= 20;
            if (checkUnlocks) recipeUnlockTick = 0;
            boolean reconcile = ++reconciliationTick >= 40;
            if (reconcile) reconciliationTick = 0;
            for (var player : server.getPlayerManager().getPlayerList()) {
                if (reconcile) {
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        var stack = player.getInventory().getStack(i);
                        if (EquipmentComponent.isTracked(stack)) {
                            EquipmentComponent.getOrCreate(stack);
                            EquipmentComponent.restoreEnchantments(stack, player.getEntityWorld().getRegistryManager());
                            EquipmentComponent.markBrokenIfNecessary(stack, player);
                        }
                    }
                }
                if (checkUnlocks) checkRecipeUnlocks(player);
            }
        });

        // Armor XP from damage.
        ServerLivingEntityEvents.AFTER_DAMAGE.register(ArmorXpHandler::afterDamage);
    }

    /**
     * Unlocks the repair-kit recipes in the player's recipe book as soon as they
     * own any of the required ingredients, so the recipes are discoverable early.
     */
    private static void checkRecipeUnlocks(ServerPlayerEntity player) {
        var book = player.getRecipeBook();
        if (!book.isUnlocked(REPAIR_KIT_RECIPE)) {
            for (Item ingredient : REPAIR_KIT_INGREDIENTS) {
                if (hasItem(player, ingredient)) {
                    player.unlockRecipes(List.of(REPAIR_KIT_RECIPE));
                    break;
                }
            }
        }
        if (!book.isUnlocked(DIAMOND_REPAIR_KIT_RECIPE) && hasItem(player, Items.DIAMOND)) {
            player.unlockRecipes(List.of(DIAMOND_REPAIR_KIT_RECIPE));
        }
    }

    private static boolean hasItem(ServerPlayerEntity player, Item item) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(item)) return true;
        }
        return false;
    }
}
