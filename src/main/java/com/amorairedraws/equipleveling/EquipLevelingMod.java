package com.amorairedraws.equipleveling;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.event.EquipmentXpEvents;
import com.amorairedraws.equipleveling.event.ArmorXpHandler;
import com.amorairedraws.equipleveling.event.DeathEventHandler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.amorairedraws.equipleveling.loot.EquipmentLootModifier;
import com.amorairedraws.equipleveling.screen.EquipmentEnchantingScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class EquipLevelingMod implements ModInitializer {
	public static final String MOD_ID = "equip_leveling";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Equip Leveling!");
		
		// Load config
		EquipLevelingConfig.load();
		
		// Register custom component and the real handler type used by both server
		// and client.  Passing null to ScreenHandler's constructor prevents the
		// handler from being opened by vanilla networking.
		EquipmentComponent.register();
		EquipmentEnchantingScreenHandler.TYPE = Registry.register(Registries.SCREEN_HANDLER,
			Identifier.of(MOD_ID, "equipment_enchanting"),
			new ScreenHandlerType<>(EquipmentEnchantingScreenHandler::new, net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES));
		
		// Register event listeners
		registerEventListeners();

		// Post-process every vanilla and modded loot table, including tables that
		// were not known at compile time.  This is the only reliable global hook
		// for stripping enchanted books and converting enchanted equipment.
		LootTableEvents.MODIFY_DROPS.register((entry, context, drops) -> {
			for (int i = drops.size() - 1; i >= 0; i--) {
				net.minecraft.item.ItemStack processed = EquipmentLootModifier.processLootItem(drops.get(i));
				if (processed.isEmpty()) drops.remove(i);
				else drops.set(i, processed);
			}
		});
		
		// A kill callback is used rather than AttackEntityCallback: the latter fires
		// before damage and cannot reliably determine whether the entity died.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof net.minecraft.entity.player.PlayerEntity dead) {
				DeathEventHandler.handlePlayerDeath(dead);
			} else if (source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity player) {
				EquipmentXpEvents.awardKillXp(player, entity);
			}
		});

		LOGGER.info("Equip Leveling initialized successfully!");
	}

	private void registerEventListeners() {
		// XP accrual events
		PlayerBlockBreakEvents.BEFORE.register(new EquipmentXpEvents.BlockBreakXpHandler());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Durability is applied after most Fabric action callbacks. Scan once per
			// tick so zero-durability stacks reliably enter the persistent broken state.
			for (var player : server.getPlayerManager().getPlayerList()) {
				for (int i = 0; i < player.getInventory().size(); i++) {
					EquipmentComponent.markBrokenIfNecessary(player.getInventory().getStack(i));
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> ArmorXpHandler.allowDamage(entity, source, amount));
	}
}
