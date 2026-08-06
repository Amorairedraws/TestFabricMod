package com.amorairedraws.equipleveling.config;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class EquipLevelingConfigScreen extends Screen {
	
	private final Screen parent;
	private TextFieldWidget xpMultiplierField;
	private TextFieldWidget xpDisplayThresholdField;

	public EquipLevelingConfigScreen(Screen parent) {
		super(Text.literal("Equip Leveling Configuration"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();

		// XP Multiplier field
		this.xpMultiplierField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 50, 200, 20, 
			Text.literal("XP Multiplier"));
		this.xpMultiplierField.setText(String.valueOf(EquipLevelingConfig.getXpMultiplier()));
		this.addDrawableChild(this.xpMultiplierField);

		// XP Display Threshold field
		this.xpDisplayThresholdField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 100, 200, 20,
			Text.literal("XP Display Threshold"));
		this.xpDisplayThresholdField.setText(String.valueOf(EquipLevelingConfig.getXpDisplayThreshold()));
		this.addDrawableChild(this.xpDisplayThresholdField);

		// Toggle buttons
		this.addDrawableChild(ButtonWidget.builder(
			Text.literal(EquipLevelingConfig.isKeepEquipOnDeath() ? "Keep Equipment: ON" : "Keep Equipment: OFF"),
			button -> {
				EquipLevelingConfig.setKeepEquipOnDeath(!EquipLevelingConfig.isKeepEquipOnDeath());
				button.setMessage(Text.literal(EquipLevelingConfig.isKeepEquipOnDeath() ? 
					"Keep Equipment: ON" : "Keep Equipment: OFF"));
			}
		).dimensions(this.width / 2 - 100, 150, 200, 20).build());

		this.addDrawableChild(ButtonWidget.builder(
			Text.literal(EquipLevelingConfig.isBrokenMechanicEnabled() ? "Broken Mechanic: ON" : "Broken Mechanic: OFF"),
			button -> {
				EquipLevelingConfig.setBrokenMechanicEnabled(!EquipLevelingConfig.isBrokenMechanicEnabled());
				button.setMessage(Text.literal(EquipLevelingConfig.isBrokenMechanicEnabled() ? 
					"Broken Mechanic: ON" : "Broken Mechanic: OFF"));
			}
		).dimensions(this.width / 2 - 100, 200, 200, 20).build());

		// Save and Back buttons
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Save & Exit"), button -> {
			try {
				EquipLevelingConfig.setXpMultiplier(Double.parseDouble(this.xpMultiplierField.getText()));
				EquipLevelingConfig.setXpDisplayThreshold(Integer.parseInt(this.xpDisplayThresholdField.getText()));
			} catch (NumberFormatException e) {
				// Ignore parsing errors
			}
			this.client.setScreen(this.parent);
		}).dimensions(this.width / 2 - 100, this.height - 50, 200, 20).build());
	}

	@Override
	public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
		
		context.drawTextWithShadow(this.textRenderer, "XP Multiplier:", this.width / 2 - 100, 35, 0xFFFFFF);
		context.drawTextWithShadow(this.textRenderer, "XP Display Threshold:", this.width / 2 - 100, 85, 0xFFFFFF);
		
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
	}
}
