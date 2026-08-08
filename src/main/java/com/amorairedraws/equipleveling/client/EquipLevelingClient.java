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
import com.amorairedraws.equipleveling.network.XpGainPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class EquipLevelingClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		HandledScreens.register(EquipmentEnchantingScreenHandler.TYPE, EquipmentEnchantingScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(XpGainPayload.ID,
				(payload, context) -> XpDisplay.show(payload.position(), payload.amount()));

		// Register tooltip renderer
		new EquipmentTooltipRenderer().register();
		
		// Register item renderer modifications
		new BrokenItemRenderer().register();
		FloatingXpRenderer.register();
		XpDisplay.install(FloatingXpRenderer::show);
	}
}
