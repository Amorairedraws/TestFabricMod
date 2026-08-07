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
import net.minecraft.text.Text;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

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
		
		// The vanilla table is deliberately reused: no resource pack or custom
		// block is needed. Only qualifying equipment opens our handler.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient() || hit.getBlockPos() == null
					|| world.getBlockState(hit.getBlockPos()).getBlock() != Blocks.ENCHANTING_TABLE
					|| !EquipmentComponent.isTracked(player.getStackInHand(hand))) return ActionResult.PASS;
			if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
				SimpleInventory input = new SimpleInventory(1);
				// Initialize lazily, but before the handler generates offers. The same
				// stack object is retained so component mutations persist in inventory.
				EquipmentComponent.getOrCreate(player.getStackInHand(hand));
				input.setStack(0, player.getStackInHand(hand));
				serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(syncId, inventory, p) -> new EquipmentEnchantingScreenHandler(syncId, inventory, input, p, hand),
					Text.translatable("equip_leveling.title")));
			}
			return ActionResult.SUCCESS;
		});

		// Hoe tilling is a block-use action rather than a block break. Award it
		// here; crop harvesting continues to use PlayerBlockBreakEvents.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.getBlockState(hit.getBlockPos()).isIn(net.minecraft.registry.tag.BlockTags.DIRT)
					&& "hoe".equals(com.amorairedraws.equipleveling.util.EquipmentCategory.getCategory(player.getStackInHand(hand)))) {
				int xp = 3;
				if (world.isClient()) {
					com.amorairedraws.equipleveling.event.XpDisplay.show(
							net.minecraft.util.math.Vec3d.ofCenter(hit.getBlockPos()), xp);
				} else {
					EquipmentComponent.addXp(player.getStackInHand(hand), xp);
				}
			}
			return ActionResult.PASS;
		});

		// Copy only our leveled equipment across the vanilla player clone created
		// after death. This is independent of the global keepInventory gamerule.
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (!EquipLevelingConfig.isKeepEquipOnDeath() || alive) return;
			for (int i = 0; i < oldPlayer.getInventory().size(); i++) {
				var stack = oldPlayer.getInventory().getStack(i);
				if (EquipmentComponent.isTracked(stack) && stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
					newPlayer.getInventory().setStack(i, stack.copy());
				}
			}
		});

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
		// A broken stack must not be usable as a weapon or right-click tool. Mining
		// is covered by ItemStackMixin.canMine; these callbacks cover entity attacks
		// and item-use actions without changing vanilla durability behaviour.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			var stack = player.getStackInHand(hand);
			var data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
			return data != null && data.broken ? ActionResult.FAIL : ActionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
			var stack = player.getStackInHand(hand);
			var data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
			return data != null && data.broken ? ActionResult.FAIL : ActionResult.PASS;
		});

		// XP accrual events
		PlayerBlockBreakEvents.AFTER.register(new EquipmentXpEvents.BlockBreakXpHandler());
		// AttackEntityCallback is used only for the client-side floating label. The
		// authoritative reward is granted from AFTER_DEATH below, once the kill is
		// known to have succeeded.
		AttackEntityCallback.EVENT.register(new EquipmentXpEvents.EntityKillXpHandler());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Durability is applied after most Fabric action callbacks. Scan once per
			// tick so zero-durability stacks reliably enter the persistent broken state.
			for (var player : server.getPlayerManager().getPlayerList()) {
				for (int i = 0; i < player.getInventory().size(); i++) {
					var stack = player.getInventory().getStack(i);
					// Materialize the component for every qualifying stack, not only
					// stacks that have already earned XP. This keeps the promised
					// persistent data model consistent for crafted and modded gear.
					if (EquipmentComponent.isTracked(stack)) EquipmentComponent.getOrCreate(stack);
					EquipmentComponent.markBrokenIfNecessary(stack);
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> ArmorXpHandler.allowDamage(entity, source, amount));
	}
}
