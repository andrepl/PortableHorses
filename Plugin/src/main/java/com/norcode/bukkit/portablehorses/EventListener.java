package com.norcode.bukkit.portablehorses;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.EnumSet;

public class EventListener implements Listener {
	private PortableHorses plugin;


	private static final EnumSet<Material> INTERACTIVE_BLOCKS = EnumSet.of(Material.WOODEN_DOOR, Material.IRON_DOOR_BLOCK, Material.FENCE_GATE, Material.WORKBENCH,
			Material.ENCHANTMENT_TABLE, Material.ENDER_CHEST, Material.ENDER_PORTAL_FRAME, Material.CHEST, Material.TRAPPED_CHEST, Material.REDSTONE_COMPARATOR_OFF,
			Material.REDSTONE_COMPARATOR_ON, Material.DIODE_BLOCK_OFF, Material.DIODE_BLOCK_ON, Material.BEACON, Material.TRAP_DOOR, Material.NOTE_BLOCK, Material.JUKEBOX,
			Material.BREWING_STAND, Material.ANVIL, Material.BED_BLOCK, Material.FURNACE, Material.BURNING_FURNACE, Material.WOOD_BUTTON, Material.STONE_BUTTON, Material.LEVER);

	public EventListener(PortableHorses plugin) {
		this.plugin = plugin;
	}


	@EventHandler(priority= EventPriority.HIGH)
	public void onInventoryOpen(InventoryOpenEvent event) {
		if (event.getInventory().getHolder() instanceof Horse) {
			if (!plugin.preventHorseTheft) return;
			final Player p = (Player) event.getPlayer();
			Horse horse = ((Horse) event.getInventory().getHolder());
			if (plugin.isPortableHorseSaddle(horse.getInventory().getSaddle())) {
				if (!plugin.canUseHorse(p, horse)) {
					p.sendMessage(plugin.getMsg("not-your-horse"));
					event.setCancelled(true);
				} else {
					plugin.saveOwnerUse(horse);
				}
			}
		}
	}

	@EventHandler(priority=EventPriority.HIGH)
	public void onInteractHorse(PlayerInteractEntityEvent event) {
		if (!plugin.preventHorseTheft||plugin.preventHorseDamage) return;
		if (event.getRightClicked().getType().equals(EntityType.HORSE)) {
			Horse horse = ((Horse) event.getRightClicked());
			if (plugin.isPortableHorseSaddle(horse.getInventory().getSaddle())) {
				if (!plugin.canUseHorse(event.getPlayer(), horse)) {
					event.getPlayer().setMetadata("pre-mount-location",
							new FixedMetadataValue(plugin, event.getPlayer().getLocation().clone()));
				} else {
					plugin.saveOwnerUse(horse);
				}
			}
		}
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
	public void onClickSaddle(PlayerInteractEvent event) {
		if (event.getItem() != null && event.getItem().getType().equals(Material.SADDLE)) {
			if (event.getAction() == Action.RIGHT_CLICK_BLOCK && plugin.isPortableHorseSaddle(event.getItem())) {
				if (INTERACTIVE_BLOCKS.contains(event.getClickedBlock().getType())) {
					return;
				}
				if (event.getPlayer().hasPermission("portablehorses.spawn")) {
					Location spawnLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
					if (!plugin.isValidSpawnLocation(spawnLoc)) {
						event.getPlayer().sendMessage(plugin.getMsg("no-spawn-permission-here"));
						return;
					}
					Horse horse = (Horse) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.HORSE);
					if (horse.isValid()) {
						plugin.getNmsHandler().restoreHorseFromSaddle(event.getItem(), horse);
						horse.getInventory().setSaddle(event.getItem());
						event.getPlayer().setItemInHand(null);
						horse.setOwner(event.getPlayer());
						plugin.saveOwnerUse(horse);
					} else {
						event.getPlayer().sendMessage(plugin.getMsg("no-spawn-permission-here"));
					}
				} else {
					event.getPlayer().sendMessage(plugin.getMsg("no-spawn-permission"));
				}
			}
		}
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (event.getPlayer().hasMetadata("portablehorses-owner-override")) {
			event.getPlayer().removeMetadata("portablehorses-owner-override", plugin);
		}
	}


	@EventHandler
	public void onClickHorse(EntityDamageByEntityEvent event) {
		if (!plugin.allowSaddleRemoval) return;
		if (event.getDamager() instanceof Player) {
			if (event.getEntity() instanceof Horse) {
				Horse horse = ((Horse) event.getEntity());
				Player p = (Player) event.getDamager();
				if (p.isSneaking()) {
					event.setCancelled(true);
					if (plugin.preventHorseTheft) {
						if (!plugin.canUseHorse(p, horse)) {
							p.sendMessage(plugin.getMsg("not-your-horse"));
							return;
						}
					}
					if (plugin.isPortableHorseSaddle(horse.getInventory().getSaddle())) {
						// Remove the saddle and 'disenchant' it.
						horse.getInventory().setSaddle(null);
						horse.getWorld().dropItem(horse.getLocation(), new ItemStack(plugin.getEmptyPortableHorseSaddle()));
					}
				}
			}
		}
	}

	@EventHandler(priority= EventPriority.NORMAL, ignoreCancelled = true)
	public void onSaddleEvent(final InventoryClickEvent event) {

		if (!(event.getInventory() instanceof HorseInventory)) return;
		Horse horse = ((Horse) event.getInventory().getHolder());
		plugin.debug("Inventory Action:" + event.getAction());
		plugin.debug("Cursor:" + event.getCursor());
		plugin.debug("CurrentItem:" + event.getCurrentItem());
		plugin.debug("Click:" + event.getClick());
		if (event.isShiftClick()) {
			if (event.getRawSlot() != 0) {
				if (plugin.isPortableHorseSaddle(event.getCurrentItem())) {
					event.setCancelled(true);
				} else if (plugin.isEmptyPortableHorseSaddle(event.getCurrentItem()) && ((HorseInventory) event.getInventory()).getSaddle() == null) {
					if (event.getCurrentItem().getAmount() > 1) {
						event.setCancelled(true);
					} else {
						onSaddled(event, horse, event.getCurrentItem());
					}
				}
			} else if (event.getRawSlot() == 0 && event.getWhoClicked().getInventory().firstEmpty() != -1 && plugin.isPortableHorseSaddle(event.getCurrentItem())) {
				// Removing a saddle by shift-click.
				onUnsaddled(event, horse, event.getCurrentItem());
			}
		} else if (event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_ONE) {
			if (event.getRawSlot() == 0 && event.getCurrentItem().getType() == Material.AIR) {
				if (plugin.isPortableHorseSaddle(event.getCursor())) {
					event.setCancelled(true);
				} else if (plugin.isEmptyPortableHorseSaddle(event.getCursor())) {
					if (event.getCursor().getAmount() > 1) {
						event.setCancelled(true);
					} else {
						plugin.debug("Saddling!");
						event.setCancelled(true);
						ItemStack stack = event.getCursor();
						onSaddled(event, horse, stack);
						event.setCurrentItem(stack);
						event.setCursor(null);
					}
				}
			}
		} else if (event.getAction() == InventoryAction.PICKUP_ALL || event.getAction() == InventoryAction.PICKUP_ONE || event.getAction() == InventoryAction.PICKUP_HALF) {
			if (event.getRawSlot() == 0 && plugin.isPortableHorseSaddle(event.getCurrentItem())) {
				// removed a saddle.
				onUnsaddled(event, horse, event.getCurrentItem());
			}
		} else if ((event.getAction() == InventoryAction.HOTBAR_SWAP ||
				event.getAction() == InventoryAction.DROP_ONE_SLOT ||
				event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) &&
				event.getRawSlot() == 0 && plugin.isPortableHorseSaddle(event.getCurrentItem())) {
			onUnsaddled(event, horse, event.getCurrentItem());
		}
	}


	@EventHandler(priority=EventPriority.HIGH)
	public void onInventoryClick(final InventoryClickEvent event) {
		if (!(event.getInventory() instanceof HorseInventory)) return;
		if (plugin.allowNestedSaddles) {
			return;
		}
		Horse horse = ((Horse) event.getInventory().getHolder());
		if (!horse.isCarryingChest()) {
			return;
		}
		plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
			@Override
			public void run() {
				for (int i=1;i<event.getInventory().getSize();i++) {
					ItemStack s = ((HorseInventory) event.getInventory()).getItem(i);
					if (s != null && plugin.isPortableHorseSaddle(s)) {
						event.getInventory().setItem(i, null);
						event.getWhoClicked().getInventory().addItem(s);
						((Player) event.getWhoClicked()).updateInventory();
					}
				}
			}
		}, 0);
	}


	public void onSaddled(InventoryClickEvent event, Horse horse, ItemStack saddle) {
		plugin.debug(horse + "Saddled.");
		if (!plugin.usePermissions || event.getWhoClicked().hasPermission("portablehorses.saddle")) {

			plugin.getNmsHandler().saveToSaddle(horse, saddle);
			horse.getInventory().setSaddle(saddle);
			event.setCurrentItem(null);
		} else {
			if (event.getWhoClicked().getType() == EntityType.PLAYER) {
				((Player) event.getWhoClicked()).sendMessage(plugin.getMsg("no-saddle-permission"));
			}
		}
	}

	public void onUnsaddled(InventoryClickEvent event, Horse horse, ItemStack saddle) {
		plugin.debug(horse + "Unsaddled.");
		if (!plugin.usePermissions || event.getWhoClicked().hasPermission("portablehorses.unsaddle")) {
			if (!plugin.storeArmor) {
				if (horse.getInventory().getArmor() != null && horse.getInventory().getArmor().getType() != Material.AIR) {
					horse.getWorld().dropItem(horse.getLocation(), horse.getInventory().getArmor());
					horse.getInventory().setArmor(null);
				}
			}
			if (!plugin.storeInventory && horse.isCarryingChest()) {
				ItemStack toDrop;
				for (int i=2;i<horse.getInventory().getContents().length;i++) {
					toDrop = horse.getInventory().getItem(i);
					if (toDrop != null) {
						horse.getWorld().dropItem(horse.getLocation(), toDrop);
						horse.getInventory().setItem(i, null);
					}
				}
			}
			plugin.getNmsHandler().saveToSaddle(horse, saddle);
			horse.remove();
		} else {
			if (event.getWhoClicked().getType() == EntityType.PLAYER) {
				((Player) event.getWhoClicked()).sendMessage(plugin.getMsg("no-unsaddle-permission"));
			}
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onHorseDamage(EntityDamageByEntityEvent event) {
		if (!plugin.preventHorseDamage) {
			return;
		}
		if (event.getEntityType() == EntityType.HORSE) {
			Horse horse = (Horse) event.getEntity();
			LivingEntity damager = null;
			if (event.getDamager() instanceof Projectile) {
				damager = ((Projectile) event.getDamager()).getShooter();
			} else if (event.getDamager().getType() == EntityType.PLAYER) {
				damager = (LivingEntity) event.getDamager();
			}
			if (damager == null || damager.getType() != EntityType.PLAYER) {
				return;
			}
			if (plugin.isPortableHorseSaddle(horse.getInventory().getSaddle())) {
				if (!plugin.canDamageHorse((Player) damager, horse)) {
					event.setCancelled(true);
					((Player) damager).sendMessage(plugin.getMsg("not-your-horse"));
				}
			}
		}
	}

	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		if (event.getEntity() instanceof Horse) {
			Horse h = (Horse) event.getEntity();
			if (plugin.isPortableHorseSaddle(h.getInventory().getSaddle())) {
				h.getInventory().setSaddle(plugin.getEmptyPortableHorseSaddle());
			}
		}
	}

	@EventHandler(priority=EventPriority.HIGH)
	public void mountHorse(VehicleEnterEvent event) {
		plugin.debug("mountHorse");
		if (!(plugin.preventHorseTheft||plugin.preventHorseDamage)) return;
		if (event.getEntered().getType().equals(EntityType.PLAYER)) {
			final Player p = (Player) event.getEntered();
			if (event.getVehicle().getType().equals(EntityType.HORSE)) {
				Horse horse = ((Horse) event.getVehicle());
				if (plugin.isPortableHorseSaddle(horse.getInventory().getSaddle())) {
					if (!plugin.canUseHorse(p, horse)) {
						p.sendMessage(plugin.getMsg("not-your-horse"));
						event.setCancelled(true);
						final Location prevLoc = (Location) p.getMetadata("pre-mount-location").get(0).value();
						p.removeMetadata("pre-mount-location", plugin);
						p.teleport(prevLoc);
					} else {
						plugin.saveOwnerUse(horse);
					}
				}
			}
		}
	}

}
