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
import com.amorairedraws.equipleveling.loot.EquipmentLootModifier;

public class EquipLevelingMod implements ModInitializer {
	public static final String MOD_ID = "equip_leveling";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Equip Leveling!");
		
		// Load config
		EquipLevelingConfig.load();
		
		// Register custom component
		EquipmentComponent.register();
		
		// Register event listeners
		registerEventListeners();
		
		// Register loot modifiers
		LootTableEvents.MODIFY.register(new EquipmentLootModifier());
		
		LOGGER.info("Equip Leveling initialized successfully!");
	}

	private void registerEventListeners() {
		// XP accrual events
		AttackEntityCallback.EVENT.register(new EquipmentXpEvents.EntityKillXpHandler());
		PlayerBlockBreakEvents.BEFORE.register(new EquipmentXpEvents.BlockBreakXpHandler());
		ServerTickEvents.END_SERVER_TICK.register(new EquipmentXpEvents.DamageXpHandler());
	}
}
