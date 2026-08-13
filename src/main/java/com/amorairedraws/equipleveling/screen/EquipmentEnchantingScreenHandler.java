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
	private final net.minecraft.util.math.BlockPos sourcePos;
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
			PlayerEntity sourcePlayer, net.minecraft.util.math.BlockPos sourcePos) {
		super(TYPE, syncId);
		this.inventory = inventory;
		this.sourcePlayer = sourcePlayer;
		this.sourcePos = sourcePos;

		// This is a real input slot, just like the vanilla enchanting table slot.
		// The item is not copied from the player's hand: any qualifying equipment
		// can be dragged here and is returned to the player when the screen closes.
		this.addSlot(new Slot(inventory, 0, 15, 47) {
			@Override public boolean canInsert(ItemStack stack) {
				return EquipmentComponent.isTracked(stack);
			}
		});

		// Player inventory
		for (int m = 0; m < 3; m++) {
			for (int l = 0; l < 9; l++) {
				int inventoryIndex = l + m * 9 + 9;
				this.addSlot(playerSlot(playerInventory, inventoryIndex, 8 + l * 18, 140 + m * 18));
			}
		}

		for (int m = 0; m < 9; m++) {
			this.addSlot(playerSlot(playerInventory, m, 8 + m * 18, 198));
		}
		this.addProperties(offerProperties);
		this.generateOffers(playerInventory.player);
	}

	private Slot playerSlot(PlayerInventory playerInventory, int inventoryIndex, int x, int y) {
		return new Slot(playerInventory, inventoryIndex, x, y);
	}

	public void generateOffers(PlayerEntity player) {
		clearOfferProperties();
		ItemStack itemStack = this.inventory.getStack(0);
		if (!EquipmentComponent.isTracked(itemStack)) {
			return;
		}
		if (!itemStack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
			EquipmentComponent.getOrCreate(itemStack);
		}

		EquipmentComponent.EquipmentData data = itemStack.get(EquipmentComponent.EQUIPMENT_TYPE);
		data.updateMaxed(player.getEntityWorld().getRegistryManager(),
				MaterialTierUpgrader.isTierLevelSatisfied(itemStack, data.level,
						EquipLevelingConfig.getMaterialTiers()));
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

		// Issue: the legendary upgrade is a single global roll per offering, not
		// three independent rolls. If it hits, replace one random offer with the
		// legendary upgrade so the displayed chance matches the configured value.
		if (MaterialTierUpgrader.canPromote(itemStack,
				EquipmentCategory.getCategory(itemStack), EquipLevelingConfig.getMaterialTiers())) {
			double legendaryProbability = Math.max(0, Math.min(1,
					EquipLevelingConfig.getLegendaryUpgradeProbability()));
			if (random.nextDouble() < legendaryProbability) {
				int target = random.nextInt(3);
				this.offers[target] = new EquipmentEnchantingOffer.LegendaryUpgrade();
				this.offerLevels[target] = calculateOfferCost(data, this.offers[target], player);
			}
		}
		syncOfferProperties(player);
	}

	private EquipmentEnchantingOffer generateRandomOffer(EquipmentComponent.EquipmentData data, PlayerEntity player, ItemStack itemStack) {
		var enchantments = player.getEntityWorld().getRegistryManager()
				.getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
		java.util.List<EquipmentComponent.EquipmentSlot> upgradeable = new java.util.ArrayList<>();
		data.slots.stream().filter(s -> !s.isEmpty()
				&& s.enchantmentLevel < EquipmentComponent.EquipmentData.maxEnchantmentLevel(s, player.getEntityWorld().getRegistryManager()))
			.forEach(upgradeable::add);
		data.bonusSlots.stream().filter(s -> !s.isEmpty()
				&& s.enchantmentLevel < EquipmentComponent.EquipmentData.maxEnchantmentLevel(s, player.getEntityWorld().getRegistryManager()))
			.forEach(upgradeable::add);

		java.util.List<net.minecraft.util.Identifier> newEnchantmentIds = new java.util.ArrayList<>(enchantments.getIds());
		newEnchantmentIds.removeIf(id -> "minecraft:mending".equals(id.toString())
				|| data.slots.stream().anyMatch(slot -> id.toString().equals(slot.enchantmentId))
				|| data.bonusSlots.stream().anyMatch(slot -> id.toString().equals(slot.enchantmentId))
				|| !enchantments.get(id).isAcceptableItem(itemStack)
				|| conflictsWithExisting(id, data, enchantments));

		boolean canLegendary = MaterialTierUpgrader.canPromote(itemStack,
				EquipmentCategory.getCategory(itemStack), EquipLevelingConfig.getMaterialTiers());
		boolean canUpgrade = !upgradeable.isEmpty();
		boolean canAdd = data.getFilledSlots() < 4 && !newEnchantmentIds.isEmpty();
		if (!canLegendary && !canUpgrade && !canAdd) return null;

		// The legendary upgrade is rolled ONCE per offering (in generateOffers),
		// not per offer, so a 5% chance stays a 5% chance instead of effectively
		// becoming ~14% across three independent rolls. Here it is only used as a
		// deterministic fallback so a non-max-tier item never gets an empty offer
		// list merely because no ordinary offer was possible.
		if (canLegendary && !canUpgrade && !canAdd) {
			return new EquipmentEnchantingOffer.LegendaryUpgrade();
		}

		double upgradeWeight = Math.max(0, EquipLevelingConfig.getUpgradeWeight());
		double newSlotWeight = Math.max(0, EquipLevelingConfig.getNewSlotWeight());
		double totalWeight = upgradeWeight + newSlotWeight;
		double roll = totalWeight <= 0 ? 0 : random.nextDouble() * totalWeight;
		if (canUpgrade && (!canAdd || roll < upgradeWeight)) {
			return new EquipmentEnchantingOffer.Upgrade(upgradeable.get(random.nextInt(upgradeable.size())));
		}
		if (canAdd) {
			return new EquipmentEnchantingOffer.NewEnchantment(
					newEnchantmentIds.get(random.nextInt(newEnchantmentIds.size())).toString());
		}
		// A zero-weight configuration should still produce a valid offer.
		if (canUpgrade) return new EquipmentEnchantingOffer.Upgrade(upgradeable.get(random.nextInt(upgradeable.size())));
		return canLegendary ? new EquipmentEnchantingOffer.LegendaryUpgrade() : null;
	}

	private boolean conflictsWithExisting(net.minecraft.util.Identifier id,
			EquipmentComponent.EquipmentData data,
			net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment> enchantments) {
		var candidate = enchantments.getEntry(id).orElse(null);
		if (candidate == null) return true;
		for (EquipmentComponent.EquipmentSlot slot : data.slots) {
			if (slot.isEmpty()) continue;
			try {
				var existing = enchantments.getEntry(Identifier.of(slot.enchantmentId)).orElse(null);
				if (existing != null && !net.minecraft.enchantment.Enchantment.canBeCombined(candidate, existing)) return true;
			} catch (RuntimeException ignored) { }
		}
		for (EquipmentComponent.EquipmentSlot slot : data.bonusSlots) {
			if (slot.isEmpty()) continue;
			try {
				var existing = enchantments.getEntry(Identifier.of(slot.enchantmentId)).orElse(null);
				if (existing != null && !net.minecraft.enchantment.Enchantment.canBeCombined(candidate, existing)) return true;
			} catch (RuntimeException ignored) { }
		}
		return false;
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
		if (sourcePlayer.getEntityWorld().isClient()) {
			rebuildClientOffers();
		} else if (changedInventory == inventory) {
			// Generate offers when the player actually places an item in the slot.
			generateOffers(sourcePlayer);
		}
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
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		if (slotIndex < 0 || slotIndex >= this.slots.size()) return ItemStack.EMPTY;
		Slot source = this.slots.get(slotIndex);
		if (!source.hasStack()) return ItemStack.EMPTY;
		ItemStack original = source.getStack().copy();
		ItemStack moving = source.getStack();
		if (slotIndex == 0) {
			if (!insertItem(moving, 1, this.slots.size(), true)) return ItemStack.EMPTY;
		} else {
			if (!EquipmentComponent.isTracked(moving) || !insertItem(moving, 0, 1, false)) return ItemStack.EMPTY;
		}
		if (moving.isEmpty()) source.setStack(ItemStack.EMPTY);
		else source.markDirty();
		return original;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		if (!this.inventory.canPlayerUse(player)) return false;
		if (sourcePos == null) return true; // client-side constructor has no block context
		return player.getEntityWorld().getBlockState(sourcePos).isOf(net.minecraft.block.Blocks.ENCHANTING_TABLE)
				&& player.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(sourcePos)) <= 64.0;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		// SimpleInventory is not a block inventory, so return the real input stack
		// explicitly. This prevents the old display-copy/duplication behaviour.
		if (!player.getEntityWorld().isClient()) {
			ItemStack result = this.inventory.removeStack(0);
			if (!result.isEmpty() && !player.getInventory().insertStack(result)) {
				player.dropItem(result, false);
			}
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
		boolean applied = false;

		if (offer instanceof EquipmentEnchantingOffer.NewEnchantment newEnch) {
			if (data.getFilledSlots() < 4) {
				// Empty standard slots are retained as four fixed positions.  Appending
				// here used to create a fifth slot and was silently truncated on save.
				EquipmentComponent.EquipmentSlot slot = new EquipmentComponent.EquipmentSlot(
					newEnch.enchantmentId, newEnch.level
				);
				for (int i = 0; i < data.slots.size(); i++) {
					if (data.slots.get(i).isEmpty()) {
						data.slots.set(i, slot);
						applied = true;
						break;
					}
				}
			}
		} else if (offer instanceof EquipmentEnchantingOffer.Upgrade upgrade) {
			if (upgrade.slot.enchantmentLevel < EquipmentComponent.EquipmentData.maxEnchantmentLevel(
					upgrade.slot, player.getEntityWorld().getRegistryManager())) {
				upgrade.slot.enchantmentLevel++;
				applied = true;
			}
		} else if (offer instanceof EquipmentEnchantingOffer.LegendaryUpgrade) {
			// Promote the actual stack, retaining every custom and vanilla component.
			ItemStack promoted = MaterialTierUpgrader.promote(itemStack,
					EquipmentCategory.getCategory(itemStack), EquipLevelingConfig.getMaterialTiers());
			if (promoted == itemStack) return;
			this.inventory.setStack(0, promoted);
			itemStack = promoted;
			applied = true;
		}
		if (!applied) return;

		// Restore durability
		int durableRestore = (int) (itemStack.getMaxDamage() * 
			(EquipLevelingConfig.getDurabilityRestorePercent() / 100.0));
		itemStack.setDamage(Math.max(0, itemStack.getDamage() - durableRestore));

		// Completing all four standard slots grants the separate mending
		// completion effect. It is deliberately not inserted into bonusSlots:
		// that list is reserved for the maximum of two loot slots.
		boolean wasMending = data.mending;
		if (data.getFilledSlots() == 4) data.mending = true;
		// Issue: play a distinctive sound the moment Mending is awarded.
		if (!wasMending && data.mending && !player.getEntityWorld().isClient()) {
			player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					net.minecraft.sound.SoundEvents.ITEM_TRIDENT_THUNDER,
					net.minecraft.sound.SoundCategory.MASTER, 1.0F, 1.0F);
		}

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
