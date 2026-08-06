package com.amorairedraws.equipleveling.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import com.amorairedraws.equipleveling.screen.EquipmentEnchantingScreenHandler;
import com.amorairedraws.equipleveling.client.screen.EquipmentEnchantingScreen;

import com.amorairedraws.equipleveling.client.tooltip.EquipmentTooltipRenderer;
import com.amorairedraws.equipleveling.client.render.BrokenItemRenderer;
import com.amorairedraws.equipleveling.client.render.FloatingXpRenderer;
import com.amorairedraws.equipleveling.event.XpDisplay;

public class EquipLevelingClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		HandledScreens.register(EquipmentEnchantingScreenHandler.TYPE, EquipmentEnchantingScreen::new);

		// Register tooltip renderer
		new EquipmentTooltipRenderer().register();
		
		// Register item renderer modifications
		new BrokenItemRenderer().register();
		FloatingXpRenderer.register();
		XpDisplay.install(FloatingXpRenderer::show);
	}
}
