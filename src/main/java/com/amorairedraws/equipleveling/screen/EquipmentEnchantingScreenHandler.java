package com.amorairedraws.equipleveling.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import com.amorairedraws.equipleveling.util.MaterialTierUpgrader;

public class EquipmentEnchantingScreenHandler extends ScreenHandler {

	/** Registered by the common initializer; kept here as the single handler type. */
	public static ScreenHandlerType<EquipmentEnchantingScreenHandler> TYPE;
	
	public static final int WIDTH = 176;
	public static final int HEIGHT = 222;

	private final Inventory inventory;
	private final PlayerEntity sourcePlayer;
	private final net.minecraft.util.Hand sourceHand;
	private final net.minecraft.util.math.random.Random random = net.minecraft.util.math.random.Random.create();
	
	public EquipmentEnchantingOffer[] offers = new EquipmentEnchantingOffer[3];
	public int[] offerLevels = new int[3];

	/* Four integer properties per offer make the server-generated offers visible
	 * on the client without trusting client-side random generation. Layout:
	 * type (1=new, 2=upgrade, 3=legendary), enchantment raw ID, level, cost. */
	private final int[] offerPropertyValues = new int[12];
	private final net.minecraft.screen.PropertyDelegate offerProperties = new net.minecraft.screen.PropertyDelegate() {
		@Override public int get(int index) { return index >= 0 && index < 12 ? offerPropertyValues[index] : 0; }
		@Override public void set(int index, int value) {
			if (index >= 0 && index < 12) offerPropertyValues[index] = value;
			if (sourcePlayer.getEntityWorld().isClient()) rebuildClientOffers();
		}
		@Override public int size() { return 12; }
	};

	public EquipmentEnchantingScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(1), playerInventory.player, null);
	}

	public EquipmentEnchantingScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
		this(syncId, playerInventory, inventory, playerInventory.player, null);
	}

	public EquipmentEnchantingScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory,
			PlayerEntity sourcePlayer, net.minecraft.util.Hand sourceHand) {
		super(TYPE, syncId);
		this.inventory = inventory;
		this.sourcePlayer = sourcePlayer;
		this.sourceHand = sourceHand;

		// The item is supplied by the hand that opened the table. It is a
		// deliberately fixed slot: allowing the generic Slot implementation to
		// take/place stacks would duplicate or lose the hand stack on close.
		this.addSlot(new Slot(inventory, 0, 15, 47) {
			@Override public boolean canInsert(ItemStack stack) { return false; }
			@Override public boolean canTakeItems(PlayerEntity player) { return false; }
		});

		// Player inventory
		for (int m = 0; m < 3; m++) {
			for (int l = 0; l < 9; l++) {
				this.addSlot(new Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 140 + m * 18));
			}
		}

		for (int m = 0; m < 9; m++) {
			this.addSlot(new Slot(playerInventory, m, 8 + m * 18, 198));
		}
		this.addProperties(offerProperties);
		this.generateOffers(playerInventory.player);
	}

	public void generateOffers(PlayerEntity player) {
		clearOfferProperties();
		ItemStack itemStack = this.inventory.getStack(0);
		
		if (!itemStack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			return;
		}

		EquipmentComponent.EquipmentData data = itemStack.get(EquipmentComponent.EQUIPMENT_TYPE);
		data.updateMaxed(player.getEntityWorld().getRegistryManager());
		itemStack.set(EquipmentComponent.EQUIPMENT_TYPE, data);
		
		if (data.maxed || data.broken || !data.readyToLevelUp) {
			// The table is intentionally inert until the item reaches its XP cap.
			// This also prevents a client from selecting stale offers after a
			// concurrent inventory update.
			this.offers = new EquipmentEnchantingOffer[0];
			this.offerLevels = new int[0];
			return;
		}

		this.offers = new EquipmentEnchantingOffer[3];
		this.offerLevels = new int[3];
		
		for (int i = 0; i < 3; i++) {
			this.offers[i] = generateRandomOffer(data, player, itemStack);
			if (this.offers[i] != null) {
				this.offerLevels[i] = calculateOfferCost(data, this.offers[i], player);
			}
		}
		syncOfferProperties(player);
	}

	private EquipmentEnchantingOffer generateRandomOffer(EquipmentComponent.EquipmentData data, PlayerEntity player, ItemStack itemStack) {
		double upgradeWeight = Math.max(0, EquipLevelingConfig.getUpgradeWeight());
		double newSlotWeight = Math.max(0, EquipLevelingConfig.getNewSlotWeight());
		// Legendary is a probability, not a third weight. Roll it separately so
		// its configured chance is not changed when ordinary offer weights change.
		double legendaryProbability = Math.max(0, Math.min(1,
				EquipLevelingConfig.getLegendaryUpgradeProbability()));
		if (random.nextDouble() < legendaryProbability
				&& MaterialTierUpgrader.canPromote(itemStack,
						EquipmentCategory.getCategory(itemStack), EquipLevelingConfig.getMaterialTiers())) {
			return new EquipmentEnchantingOffer.LegendaryUpgrade();
		}
		double totalWeight = upgradeWeight + newSlotWeight;
		double rand = totalWeight <= 0 ? 0 : random.nextDouble() * totalWeight;
		
		java.util.List<EquipmentComponent.EquipmentSlot> upgradeable = new java.util.ArrayList<>();
		data.slots.stream().filter(s -> !s.isEmpty()
				&& s.enchantmentLevel < EquipmentComponent.EquipmentData.maxEnchantmentLevel(s)).forEach(upgradeable::add);
		data.bonusSlots.stream().filter(s -> !s.isEmpty()
				&& s.enchantmentLevel < EquipmentComponent.EquipmentData.maxEnchantmentLevel(s)).forEach(upgradeable::add);
		if (rand < upgradeWeight && !upgradeable.isEmpty()) {
			// Upgrade a real, non-empty standard or loot slot.
			return new EquipmentEnchantingOffer.Upgrade(upgradeable.get(random.nextInt(upgradeable.size())));
		} else if (rand < upgradeWeight + newSlotWeight && data.getFilledSlots() < 4) {
			// Read the live registry so datapack/mod enchantments participate too.
			var enchantments = player.getEntityWorld().getRegistryManager()
					.getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
			var ids = new java.util.ArrayList<>(enchantments.getIds());
			// Mending is reserved for the automatic completion bonus. Also filter
			// through the enchantment's own compatibility predicate so offers never
			// contain enchantments that cannot operate on this item (including modded
			// enchantments registered by datapacks).
			ids.removeIf(id -> "minecraft:mending".equals(id.toString())
					|| data.slots.stream().anyMatch(slot -> id.toString().equals(slot.enchantmentId))
					|| data.bonusSlots.stream().anyMatch(slot -> id.toString().equals(slot.enchantmentId))
					|| !enchantments.get(id).isAcceptableItem(itemStack));
			if (ids.isEmpty()) return null;
			String id = ids.get(random.nextInt(ids.size())).toString();
			return new EquipmentEnchantingOffer.NewEnchantment(id);
		}
		return null;
	}

	private int calculateOfferCost(EquipmentComponent.EquipmentData data,
			EquipmentEnchantingOffer offer, PlayerEntity player) {
		int baseCost = EquipLevelingConfig.getRerollCosts()[Math.min(data.getFilledSlots(), 4)];
		int enchantmentWeight = 0;
		String id = offer instanceof EquipmentEnchantingOffer.NewEnchantment n ? n.enchantmentId
				: offer instanceof EquipmentEnchantingOffer.Upgrade u ? u.slot.enchantmentId : null;
		if (id != null) {
			try {
				var registry = player.getEntityWorld().getRegistryManager()
						.getOrThrow(RegistryKeys.ENCHANTMENT);
				var entry = registry.get(Identifier.of(id));
				if (entry != null) enchantmentWeight = Math.max(0, entry.getAnvilCost());
			} catch (RuntimeException ignored) { }
		}
		return Math.max(1, baseCost + enchantmentWeight);
	}

	private void clearOfferProperties() {
		java.util.Arrays.fill(offerPropertyValues, 0);
		offers = new EquipmentEnchantingOffer[3];
		offerLevels = new int[3];
	}

	private void syncOfferProperties(PlayerEntity player) {
		var registry = player.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
		for (int i = 0; i < 3; i++) {
			EquipmentEnchantingOffer offer = offers[i];
			int base = i * 4;
			if (offer instanceof EquipmentEnchantingOffer.NewEnchantment newOffer) {
				offerPropertyValues[base] = 1;
				offerPropertyValues[base + 1] = registry.getRawId(registry.get(Identifier.of(newOffer.enchantmentId)));
				offerPropertyValues[base + 2] = newOffer.level;
			} else if (offer instanceof EquipmentEnchantingOffer.Upgrade upgrade) {
				offerPropertyValues[base] = 2;
				offerPropertyValues[base + 1] = registry.getRawId(registry.get(Identifier.of(upgrade.slot.enchantmentId)));
				offerPropertyValues[base + 2] = upgrade.slot.enchantmentLevel;
			} else if (offer instanceof EquipmentEnchantingOffer.LegendaryUpgrade) {
				offerPropertyValues[base] = 3;
			}
			offerPropertyValues[base + 3] = offer == null ? 0 : offerLevels[i];
		}
	}

	/** Reconstructs the server's offer descriptions after property packets arrive. */
	private void rebuildClientOffers() {
		if (!sourcePlayer.getEntityWorld().isClient()) return;
		ItemStack stack = inventory.getStack(0);
		if (!stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) return;
		var data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
		var registry = sourcePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
		offers = new EquipmentEnchantingOffer[3];
		offerLevels = new int[3];
		for (int i = 0; i < 3; i++) {
			int base = i * 4;
			offerLevels[i] = offerPropertyValues[base + 3];
			try {
				offers[i] = switch (offerPropertyValues[base]) {
					case 1 -> registry.getEntry(offerPropertyValues[base + 1])
							.map(entry -> new EquipmentEnchantingOffer.NewEnchantment(registry.getId(entry.value()).toString()))
							.orElse(null);
					case 2 -> findUpgrade(data, registry.getEntry(offerPropertyValues[base + 1])
							.map(entry -> registry.getId(entry.value()).toString()).orElse(null), offerPropertyValues[base + 2]);
					case 3 -> new EquipmentEnchantingOffer.LegendaryUpgrade();
					default -> null;
				};
			} catch (RuntimeException ignored) {
				offers[i] = null;
			}
		}
	}

	private EquipmentEnchantingOffer.Upgrade findUpgrade(EquipmentComponent.EquipmentData data,
			String id, int level) {
		if (id == null) return null;
		for (EquipmentComponent.EquipmentSlot slot : data.slots) {
			if (id.equals(slot.enchantmentId) && slot.enchantmentLevel == level)
				return new EquipmentEnchantingOffer.Upgrade(slot);
		}
		for (EquipmentComponent.EquipmentSlot slot : data.bonusSlots) {
			if (id.equals(slot.enchantmentId) && slot.enchantmentLevel == level)
				return new EquipmentEnchantingOffer.Upgrade(slot);
		}
		return null;
	}

	@Override
	public void onContentChanged(Inventory changedInventory) {
		super.onContentChanged(changedInventory);
		if (sourcePlayer.getEntityWorld().isClient()) rebuildClientOffers();
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id == 3) {
			ItemStack stack = inventory.getStack(0);
			if (stack.isEmpty() || !stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) return false;
			var data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
			int cost = getRerollCost();
			if (player.experienceLevel < cost || data.maxed || data.broken || !data.readyToLevelUp) return false;
			player.addExperienceLevels(-cost);
			generateOffers(player);
			return true;
		}
		if (id >= 0 && id < 3) {
			selectOffer(id, player);
			return true;
		}
		return false;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return this.inventory.canPlayerUse(player);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		// Legendary promotion creates a new stack. Put that stack back in the
		// hand that opened the table instead of silently losing the promotion.
		if (sourcePlayer == player && sourceHand != null) {
			ItemStack result = this.inventory.getStack(0);
			if (!result.isEmpty()) sourcePlayer.setStackInHand(sourceHand, result);
		}
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
		int cost = EquipLevelingConfig.getRerollCosts()[filledSlots];
		// The current offers make difficult enchantments more expensive to reroll,
		// matching the vanilla anvil-weight concept used by the offer display.
		for (int offerCost : offerLevels) cost += Math.max(0, offerCost - EquipLevelingConfig.getRerollCosts()[filledSlots]);
		return cost;
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
		if (!data.readyToLevelUp || data.broken || data.maxed) return;

		if (offer instanceof EquipmentEnchantingOffer.NewEnchantment newEnch) {
			if (data.getFilledSlots() < 4) {
				// Empty standard slots are retained as four fixed positions.  Appending
				// here used to create a fifth slot and was silently truncated on save.
				EquipmentComponent.EquipmentSlot slot = new EquipmentComponent.EquipmentSlot(
					newEnch.enchantmentId, newEnch.level
				);
				for (int i = 0; i < data.slots.size(); i++) {
					if (data.slots.get(i).isEmpty()) { data.slots.set(i, slot); break; }
				}
			}
		} else if (offer instanceof EquipmentEnchantingOffer.Upgrade upgrade) {
			if (upgrade.slot.enchantmentLevel < EquipmentComponent.EquipmentData.maxEnchantmentLevel(upgrade.slot)) {
				upgrade.slot.enchantmentLevel++;
			}
		} else if (offer instanceof EquipmentEnchantingOffer.LegendaryUpgrade) {
			// Promote the actual stack, retaining every custom and vanilla component.
			ItemStack promoted = MaterialTierUpgrader.promote(itemStack,
					EquipmentCategory.getCategory(itemStack), EquipLevelingConfig.getMaterialTiers());
			if (promoted == itemStack) return;
			this.inventory.setStack(0, promoted);
			itemStack = promoted;
		}

		// Restore durability
		int durableRestore = (int) (itemStack.getMaxDamage() * 
			(EquipLevelingConfig.getDurabilityRestorePercent() / 100.0));
		itemStack.setDamage(Math.max(0, itemStack.getDamage() - durableRestore));

		// Completing all four standard slots grants the separate mending
		// completion effect. It is deliberately not inserted into bonusSlots:
		// that list is reserved for the maximum of two loot slots.
		if (data.getFilledSlots() == 4) data.mending = true;

		// Keep the custom slot data authoritative, but also apply its effects as
		// vanilla enchantments. This makes both vanilla and modded enchantments
		// functional while ItemStackMixin controls their visual glint.
		for (EquipmentComponent.EquipmentSlot slot : data.slots) syncEnchantment(itemStack, slot, player);
		for (EquipmentComponent.EquipmentSlot slot : data.bonusSlots) syncEnchantment(itemStack, slot, player);
		if (data.mending) {
			syncEnchantment(itemStack, new EquipmentComponent.EquipmentSlot("minecraft:mending", 1), player);
		}

		// Level up
		data.levelUp(EquipmentCategory.getCategory(itemStack));
		itemStack.set(EquipmentComponent.EQUIPMENT_TYPE, data);
		this.inventory.setStack(0, itemStack);
		this.inventory.markDirty();
		generateOffers(player);
	}

	private void syncEnchantment(ItemStack stack, EquipmentComponent.EquipmentSlot slot, PlayerEntity player) {
		if (slot.isEmpty()) return;
		try {
			var registry = player.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
			registry.getEntry(Identifier.of(slot.enchantmentId)).ifPresent(entry -> stack.addEnchantment(entry, slot.enchantmentLevel));
		} catch (RuntimeException ignored) {
			// A datapack can remove an enchantment between offer generation and
			// selection; retain the slot data rather than failing the screen.
		}
	}
}
