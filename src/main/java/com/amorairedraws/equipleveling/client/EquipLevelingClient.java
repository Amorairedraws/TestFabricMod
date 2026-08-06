package com.amorairedraws.equipleveling.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

import com.amorairedraws.equipleveling.client.tooltip.EquipmentTooltipRenderer;
import com.amorairedraws.equipleveling.client.render.BrokenItemRenderer;

public class EquipLevelingClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Register tooltip renderer
		new EquipmentTooltipRenderer().register();
		
		// Register item renderer modifications
		new BrokenItemRenderer().register();
	}
}
