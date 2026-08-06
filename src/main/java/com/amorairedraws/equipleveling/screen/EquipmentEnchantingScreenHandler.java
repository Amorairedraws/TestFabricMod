package com.amorairedraws.equipleveling.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;

public class EquipmentEnchantingScreenHandler extends ScreenHandler {
	
	public static final int WIDTH = 176;
	public static final int HEIGHT = 166;

	private final Inventory inventory;
	private final net.minecraft.util.math.random.Random random = net.minecraft.util.math.random.Random.create();
	
	public EquipmentEnchantingOffer[] offers = new EquipmentEnchantingOffer[3];
	public int[] offerLevels = new int[3];

	public EquipmentEnchantingScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(1));
	}

	public EquipmentEnchantingScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
		super(null, syncId);
		this.inventory = inventory;

		this.addSlot(new Slot(inventory, 0, 15, 47));

		// Player inventory
		for (int m = 0; m < 3; m++) {
			for (int l = 0; l < 9; l++) {
				this.addSlot(new Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 140 + m * 18));
			}
		}

		for (int m = 0; m < 9; m++) {
			this.addSlot(new Slot(playerInventory, m, 8 + m * 18, 198));
		}
		
		this.generateOffers(playerInventory.player);
	}

	public void generateOffers(PlayerEntity player) {
		ItemStack itemStack = this.inventory.getStack(0);
		
		if (!itemStack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			return;
		}

		EquipmentComponent.EquipmentData data = itemStack.get(EquipmentComponent.EQUIPMENT_TYPE);
		
		if (data.maxed) {
			// No offers for maxed items
			this.offers = new EquipmentEnchantingOffer[0];
			return;
		}

		this.offers = new EquipmentEnchantingOffer[3];
		
		for (int i = 0; i < 3; i++) {
			this.offers[i] = generateRandomOffer(data);
			if (this.offers[i] != null) {
				this.offerLevels[i] = calculateOfferCost(data, i);
			}
		}
	}

	private EquipmentEnchantingOffer generateRandomOffer(EquipmentComponent.EquipmentData data) {
		double rand = random.nextDouble();
		double upgradeWeight = EquipLevelingConfig.getUpgradeWeight();
		
		if (rand < upgradeWeight && !data.slots.isEmpty()) {
			// Upgrade existing enchantment
			return new EquipmentEnchantingOffer.Upgrade(
				data.slots.get(random.nextInt(data.slots.size()))
			);
		} else if (data.getFilledSlots() < 4) {
			// Add new enchantment
			return new EquipmentEnchantingOffer.NewEnchantment();
		} else if (random.nextDouble() < EquipLevelingConfig.getLegendaryUpgradeProbability()) {
			// Legendary upgrade
			return new EquipmentEnchantingOffer.LegendaryUpgrade();
		}
		
		return null;
	}

	private int calculateOfferCost(EquipmentComponent.EquipmentData data, int slotIndex) {
		int baseCost = EquipLevelingConfig.getRerollCosts()[Math.min(data.getFilledSlots(), 4)];
		// Add enchantment weight multiplier
		return baseCost;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return this.inventory.canPlayerUse(player);
	}

	public void reroll(PlayerEntity player) {
		if (player.experienceLevel >= getRerollCost()) {
			player.addExperienceLevels(-getRerollCost());
			generateOffers(player);
		}
	}

	public int getRerollCost() {
		ItemStack itemStack = this.inventory.getStack(0);
		if (!itemStack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			return 0;
		}
		
		EquipmentComponent.EquipmentData data = itemStack.get(EquipmentComponent.EQUIPMENT_TYPE);
		int filledSlots = Math.min(data.getFilledSlots(), 4);
		return EquipLevelingConfig.getRerollCosts()[filledSlots];
	}

	public void selectOffer(int index, PlayerEntity player) {
		if (index < 0 || index >= offers.length || offers[index] == null) {
			return;
		}

		ItemStack itemStack = this.inventory.getStack(0);
		if (!itemStack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			return;
		}

		EquipmentComponent.EquipmentData data = itemStack.get(EquipmentComponent.EQUIPMENT_TYPE);
		EquipmentEnchantingOffer offer = offers[index];

		if (offer instanceof EquipmentEnchantingOffer.NewEnchantment newEnch) {
			if (data.getFilledSlots() < 4) {
				EquipmentComponent.EquipmentSlot slot = new EquipmentComponent.EquipmentSlot(
					"minecraft:unbreaking", 1
				);
				data.slots.add(slot);
			}
		} else if (offer instanceof EquipmentEnchantingOffer.Upgrade upgrade) {
			if (upgrade.slot.enchantmentLevel < 5) {
				upgrade.slot.enchantmentLevel++;
			}
		} else if (offer instanceof EquipmentEnchantingOffer.LegendaryUpgrade) {
			// Handle material tier upgrade
			String[] tiers = EquipLevelingConfig.getMaterialTiers();
			if (data.level < tiers.length - 1) {
				data.level++;
			}
		}

		// Restore durability
		int durableRestore = (int) (itemStack.getMaxDamage() * 
			(EquipLevelingConfig.getDurabilityRestorePercent() / 100.0));
		itemStack.setDamage(Math.max(0, itemStack.getDamage() - durableRestore));

		// Check if all slots are filled
		if (data.getFilledSlots() == 4 && !data.mending) {
			data.mending = true;
			EquipmentComponent.EquipmentSlot mendingSlot = new EquipmentComponent.EquipmentSlot(
				"minecraft:mending", 1
			);
			data.bonusSlots.add(0, mendingSlot);
		}

		// Level up
		data.levelUp();
		data.readyToLevelUp = false;

		generateOffers(player);
	}
}
