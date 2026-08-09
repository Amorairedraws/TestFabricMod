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
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

public class EquipLevelingMod implements ModInitializer {
	public static final String MOD_ID = "equip_leveling";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Equip Leveling!");
		
		// Load config
		EquipLevelingConfig.load();
		// The component is attached to all qualifying equipment as it enters the
		// game. Enchanting-table behavior is mixed into Minecraft's own handler,
		// so opening a table now keeps the original screen and inventory intact.
		EquipmentComponent.register();


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
				net.minecraft.item.ItemStack processed = EquipmentLootModifier.processLootItem(drops.get(i),
						context.getWorld().getRegistryManager(), context.getWorld().getRandom());
				if (processed.isEmpty()) drops.remove(i);
				else drops.set(i, processed);
			}
		});
		
		// A kill callback is used rather than AttackEntityCallback: the latter fires
		// before damage and cannot reliably determine whether the entity died.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof net.minecraft.entity.player.PlayerEntity dead) {
				DeathEventHandler.handlePlayerDeath(dead);
			} else {
				net.minecraft.entity.player.PlayerEntity killer = null;
				if (source.getSource() instanceof net.minecraft.entity.player.PlayerEntity p) killer = p;
				else if (source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity p) killer = p;
				// Only a player kill advances a held sword/axe/bow. Ranged weapons
				// (bow / crossbow) have the projectile as the direct source but the
				// player as the attacker, so both are accepted here.
				if (killer != null) EquipmentXpEvents.awardKillXp(killer, entity, source);
			}
		});

		LOGGER.info("Equip Leveling initialized successfully!");
	}

	private void registerEventListeners() {
		// Broken items deliberately use vanilla's ordinary fallback behaviour.
		// Their enchantments and attributes are suppressed by ItemStackMixin, but
		// they remain usable as slowly as a hand rather than blocking interaction.

		// XP accrual events
		PlayerBlockBreakEvents.AFTER.register(new EquipmentXpEvents.BlockBreakXpHandler());
		// AttackEntityCallback is used only to observe attempted attacks and never
		// mutates progression. The authoritative reward is granted from AFTER_DEATH
		// below, once the kill is known to have succeeded.
		AttackEntityCallback.EVENT.register(new EquipmentXpEvents.EntityKillXpHandler());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Materialise the component for every qualifying stack and reconcile its
			// mirrored enchantments. restoreEnchantments is cheap when nothing changed
			// (it skips identical writes), and running it every tick ensures
			// enchantments added externally (e.g. via commands) are captured into
			// bonus slots. Broken detection stays a lightweight per-tick check.
			for (var player : server.getPlayerManager().getPlayerList()) {
				for (int i = 0; i < player.getInventory().size(); i++) {
					var stack = player.getInventory().getStack(i);
					if (EquipmentComponent.isTracked(stack)) {
						EquipmentComponent.getOrCreate(stack);
						EquipmentComponent.restoreEnchantments(stack, player.getEntityWorld().getRegistryManager());
						EquipmentComponent.markBrokenIfNecessary(stack);
					}
				}
			}
		});
		// Award armor XP only after the damage pipeline confirms that damage was
		// actually applied; ALLOW_DAMAGE fires too early and also sees blocked hits.
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ArmorXpHandler::afterDamage);
	}
}
