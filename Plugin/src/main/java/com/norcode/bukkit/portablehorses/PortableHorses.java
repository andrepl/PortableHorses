package com.norcode.bukkit.portablehorses;

import net.gravitydevelopment.updater.Updater;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PortableHorses extends JavaPlugin implements Listener {

	private Map<String, MessageFormat> cachedMessages = new HashMap<String, MessageFormat>();

	private static EnumSet<BlockFace> surroundingEight = EnumSet.of(
			BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST, BlockFace.SOUTH_EAST,
			BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST, BlockFace.NORTH_WEST);

    private Updater updater;
    private IPacketListener packetListener;
	private long expiryMillis = TimeUnit.DAYS.toMillis(90);
	public boolean debugMode = false;
    public boolean usePermissions = true;
    public boolean storeArmor = true;
    public boolean storeInventory = true;
    public boolean allowNestedSaddles = false;
    private boolean requireSpecialSaddle = false;
    private boolean craftSpecialSaddle = false;
    public boolean allowSaddleRemoval = true;
	public boolean preventHorseTheft = false;
	public boolean preventHorseDamage = false;
	private ConfigAccessor messagesConfig = null;
	private boolean logSpawns = false;
	private boolean requireClearing = true;
	private FileHandler logFileHandler = null;
	private Logger spawnLogger = Logger.getLogger("PortableHorses");
	private Random random = new Random();
	private HashMap<String, HashMap<Long, List<String>>> loreStorage = new HashMap<String, HashMap<Long, List<String>>>();
	private ShapedRecipe specialSaddleRecipe;
	private NMS nmsHandler;

	public NMS getNmsHandler() {
		return nmsHandler;
	}

	private Recipe getSpecialSaddleRecipe() {
        if (this.specialSaddleRecipe == null) {
            ItemStack result = getEmptyPortableHorseSaddle();
            this.specialSaddleRecipe = new ShapedRecipe(result);
            this.specialSaddleRecipe.shape("PPP", "PSP", "PPP");
            this.specialSaddleRecipe.setIngredient('P', getCraftingSupplement());
            this.specialSaddleRecipe.setIngredient('S', Material.SADDLE);
        }
        return this.specialSaddleRecipe;
    }

    @Override
    public void onEnable() {
		this.messagesConfig = new ConfigAccessor(this, "messages.yml");
		this.messagesConfig.saveDefaultConfig();
		this.messagesConfig.getConfig().options().copyDefaults(true);
		this.messagesConfig.saveConfig();
		saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        initializeNMSHandler();
        initializePacketListener();
		if (this.isEnabled()) {
        	doUpdater();
        	getServer().getPluginManager().registerEvents(new EventListener(this), this);
		}
	}

	private void initializePacketListener() {
		String packageName = this.getServer().getClass().getPackage().getName();
		// Get full package string of CraftServer.
		// org.bukkit.craftbukkit.versionstring (or for pre-refactor, just org.bukkit.craftbukkit
		String version = packageName.substring(packageName.lastIndexOf('.') + 1);
		// Get the last element of the package
		if (getConfig().getString("force-version", null) != null) {
			version = getConfig().getString("force-version");
			getLogger().info("forcing package version `" + version + "` as set in config.yml");
		} else if (version.equals("craftbukkit")) { // If the last element of the package was "craftbukkit" we are now pre-refactor
			this.getLogger().severe("This version of craftbukkit is not supported.");
			this.setEnabled(false);
			return;
		}
		try {
			final Class<?> clazz = Class.forName("com.norcode.bukkit.portablehorses." + version + ".PacketListener");
			// Check if we have a NMSHandler class at that location.
			if (IPacketListener.class.isAssignableFrom(clazz)) { // Make sure it actually implements NMS
				this.packetListener = (IPacketListener) clazz.getConstructor(JavaPlugin.class).newInstance(this); // Set our handler
			}
		} catch (final Exception e) {
			getLogger().log(Level.SEVERE, "Exception loading implementation: ", e);

			this.getLogger().severe("Could not find support for this craftbukkit version " + version + ".");
			this.getLogger().info("Check for updates at http://dev.bukktit.org/bukkit-plugins/portable-horses/");
			this.setEnabled(false);
			return;
		}
	}

	private void initializeNMSHandler() {
        String packageName = this.getServer().getClass().getPackage().getName();
        // Get full package string of CraftServer.
        // org.bukkit.craftbukkit.versionstring (or for pre-refactor, just org.bukkit.craftbukkit
        String version = packageName.substring(packageName.lastIndexOf('.') + 1);
		debug("detected package version `" + version + "`");
        // Get the last element of the package
		if (getConfig().getString("force-version", null) != null) {
			version = getConfig().getString("force-version");
			getLogger().info("forcing package version `" + version + "` as set in config.yml");
		} else if (version.equals("craftbukkit")) {
			// If the last element of the package was "craftbukkit" we are now pre-refactor
			this.getLogger().severe("This version of craftbukkit is not supported.");
            this.setEnabled(false);
			return;
        }
        try {
            final Class<?> clazz = Class.forName("com.norcode.bukkit.portablehorses." + version + ".NMSHandler");
            // Check if we have a NMSHandler class at that location.
            if (NMS.class.isAssignableFrom(clazz)) { // Make sure it actually implements NMS
                this.nmsHandler = (NMS) clazz.getConstructor().newInstance(); // Set our handler
            }
        } catch (final Exception e) {
            getLogger().log(Level.SEVERE, "Exception loading implementation: ", e);
            this.getLogger().severe("Could not find support for this craftbukkit version " + version + ".");
            this.getLogger().info("Check for updates at http://dev.bukktit.org/bukkit-plugins/portable-horses/");
            this.setEnabled(false);
            return;
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        this.usePermissions = getConfig().getBoolean("use-permissions", true);
        this.debugMode = getConfig().getBoolean("debug", false);
        this.storeArmor = getConfig().getBoolean("store-armor", true);
        this.storeInventory = getConfig().getBoolean("store-inventory", true);
        this.allowNestedSaddles = getConfig().getBoolean("allow-nested-saddles", false);
        this.requireSpecialSaddle = getConfig().getBoolean("require-special-saddle", false);
        this.craftSpecialSaddle = getConfig().getBoolean("craft-special-saddle", false);
        this.allowSaddleRemoval = getConfig().getBoolean("allow-saddle-removal", true);
		this.preventHorseTheft = getConfig().getBoolean("prevent-horse-theft", false);
		this.preventHorseDamage = getConfig().getBoolean("prevent-horse-damage", false);
		this.requireClearing = getConfig().getBoolean("require-clearing-to-spawn", true);
		this.expiryMillis = timeDeltaToMillis(getConfig().getString("theft-prevention-expiry", "90d"));
		this.messagesConfig.reloadConfig();

		if (logSpawns && !getConfig().getBoolean("log-spawns", false)) {
			if (logFileHandler != null) {
				logFileHandler.flush();
				logFileHandler.close();
				spawnLogger.removeHandler(logFileHandler);
			}
		} else if (getConfig().getBoolean("log-spawns", false) && !logSpawns) {
			try {
				logFileHandler = new FileHandler(new File(getDataFolder(), "spawn.log").getAbsolutePath());
				Formatter sformatter = new LogFormatter();
				logFileHandler.setFormatter(sformatter);
				spawnLogger.addHandler(logFileHandler);
			} catch (IOException e) {
				getLogger().warning("Failed to initialize spawn log @ " + new File(getDataFolder(), "spawn.log"));
				return;
			}
		}
		this.logSpawns = getConfig().getBoolean("log-spawns", false);
		this.cachedMessages.clear();
        // Add or remove the crafting recipe for the special saddle as necessary.
        boolean found = false;
        Iterator<Recipe> it = getServer().recipeIterator();
        try {
            while (it.hasNext()) {
                Recipe r = it.next();
                if (r.equals(this.specialSaddleRecipe)) {
                    if (!craftSpecialSaddle) {
                        it.remove();
                        break;
                    } else {
                        found = true;
                    }
                }
            }
        } catch (AbstractMethodError ex) {
            getLogger().warning("abstract method error");
        }



        if (craftSpecialSaddle && !found) {
            getServer().addRecipe(this.getSpecialSaddleRecipe());
        }

    }

    public void doUpdater() {
        String autoUpdate = getConfig().getString("auto-update", "notify-only").toLowerCase();
        if (autoUpdate.equals("true")) {
            updater = new Updater(this, 64321, this.getFile(), Updater.UpdateType.DEFAULT, true);
        } else if (autoUpdate.equals("false")) {
            getLogger().info("Auto-updater is disabled.  Skipping check.");
        } else {
            updater = new Updater(this, 64321, this.getFile(), Updater.UpdateType.NO_DOWNLOAD, true);
        }
    }

    public void debug(String s) {
        if (debugMode) {
            getLogger().info(s);
        }
    }

	public static long decodeTimestamp(String s) {
		String hexDigits = StringUtils.join(s.split("" + ChatColor.COLOR_CHAR));
		try {
			return Integer.parseInt(hexDigits, 16) * 1000L;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	public static String encodeTimestamp(long timeMillis) {
		int ts = (int) (timeMillis/1000);
		StringBuilder sb = new StringBuilder();
		for (char c: Integer.toHexString(ts).toCharArray()) {
			sb.append(ChatColor.COLOR_CHAR);
			sb.append(c);
		}
		return sb.toString();
	}

	public boolean canDamageHorse(Player player, Horse horse) {
		ItemStack saddle = horse.getInventory().getSaddle();
		if (isPortableHorseSaddle(saddle)) {
			if (preventHorseDamage) {
				if (player.equals(horse.getOwner()) || isAdminMode(player)) {
					return true;
				}
				return System.currentTimeMillis() - getLastOwnerInteraction(horse) > expiryMillis;
			}
		}
		return true;
	}

	public boolean canUseHorse(Player player, Horse horse) {
		ItemStack saddle = horse.getInventory().getSaddle();
		if (isPortableHorseSaddle(saddle)) {
			if (preventHorseTheft) {
				if (player.equals(horse.getOwner()) || isAdminMode(player)) {
					return true;
				}
				return System.currentTimeMillis() - getLastOwnerInteraction(horse) > expiryMillis;
			}
		}
		return true;
	}

	public long getLastOwnerInteraction(Horse horse) {
		ItemStack saddle = horse.getInventory().getSaddle();
		if (!horse.hasMetadata("last-owner-interact")) {
			ItemMeta meta = saddle.getItemMeta();
			List<String> lore = meta.getLore();
			String line1 = lore.get(0);
			if (line1.contains(ChatColor.RESET.toString())) {
				String timestamp = line1.substring(NMS.LORE_PREFIX.length())
						.split(ChatColor.RESET.toString())[0];
				horse.setMetadata("last-owner-interact",
						new FixedMetadataValue(this, decodeTimestamp(timestamp)));
			} else {
				horse.setMetadata("last-owner-interact", new FixedMetadataValue(this, 0));
			}

		}
		return horse.getMetadata("last-owner-interact").get(0).asLong();
	}



	private boolean isAdminMode(Player player) {
		if (!player.hasPermission("portablehorses.admin")) {
			return false;
		}
		if (!player.hasMetadata("portablehorses-override-owner")) {
			return false;
		}
		return true;
	}

    public ItemStack getEmptyPortableHorseSaddle() {
        PlayerPickupItemEvent e;
        if (requireSpecialSaddle) {
            ItemStack s = new ItemStack(Material.SADDLE);
            ItemMeta meta = getServer().getItemFactory().getItemMeta(Material.SADDLE);
            meta.setDisplayName(nmsHandler.DISPLAY_NAME);
            List<String> lore = new LinkedList<String>();
            lore.add("empty");
            meta.setLore(lore);
            s.setItemMeta(meta);
            return s;
        } else {
            return new ItemStack(Material.SADDLE);
        }
    }

    public boolean isEmptyPortableHorseSaddle(ItemStack currentItem) {
        if (currentItem.getType() != Material.SADDLE) {
            return false;
        }
        if (requireSpecialSaddle) {
            if (!currentItem.hasItemMeta()) {
                return false;
            }
            if (!nmsHandler.DISPLAY_NAME.equals(currentItem.getItemMeta().getDisplayName())) {
                return false;
            }
            return currentItem.getItemMeta().hasLore() && "empty".equals(currentItem.getItemMeta().getLore().get(0));
        } else {
            return !isPortableHorseSaddle(currentItem);
        }
    }

    public boolean isPortableHorseSaddle(ItemStack currentItem) {
        if (currentItem != null && currentItem.getType().equals(Material.SADDLE)) {
            if (currentItem.hasItemMeta()) {
                if (currentItem.getItemMeta().hasLore()) {
                    List<String> lore = currentItem.getItemMeta().getLore();
                    if (lore.size() >= 1 && lore.get(0).startsWith(nmsHandler.LORE_PREFIX) && lore.get(0).length() > nmsHandler.LORE_PREFIX.length()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

	public void saveOwnerUse(Horse horse) {
		ItemMeta meta = horse.getInventory().getSaddle().getItemMeta();
		List<String> lore = meta.getLore();
		String line1 = lore.get(0);
		line1 = line1.substring(NMS.LORE_PREFIX.length());
		if (line1.contains(ChatColor.RESET.toString())) {
			line1 = line1.split(ChatColor.RESET.toString(),2)[1];
		}
		long  now = System.currentTimeMillis();
		line1 = NMS.LORE_PREFIX + encodeTimestamp(now) +
				ChatColor.RESET + line1;
		lore.set(0, line1);
		meta.setLore(lore);
		horse.getInventory().getSaddle().setItemMeta(meta);
		horse.setMetadata("last-owner-interact", new FixedMetadataValue(this, now));
	}

	public boolean isValidSpawnLocation(Location spawnLoc) {
		Block b = spawnLoc.getBlock();
		boolean fits = (!b.getType().isSolid() &&
				!b.getRelative(BlockFace.UP).getType().isSolid());
		if (!requireClearing) {
			return fits;
		} else if (fits) {
			// we need at least 3x2x3
			for (BlockFace dir: surroundingEight) {
				if (b.getRelative(dir).getType().isSolid()) {
					return false;
				}
			}
			b = b.getRelative(BlockFace.UP);
			for (BlockFace dir: surroundingEight) {
				if (b.getRelative(dir).getType().isSolid()) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	public MaterialData getCraftingSupplement() {
        String mat = getConfig().getString("recipe-extra-item", "ENDER_PEARL");
        int data = -1;
        if (mat.contains(":")) {
            String[] parts = mat.split(":");
            mat = parts[0];
            data = Integer.parseInt(parts[1]);
        }
        Material material = null;
        try {
            material = Material.getMaterial(Integer.parseInt(mat));
        } catch (IllegalArgumentException ex) {
            material = Material.getMaterial(mat);
        }
        MaterialData md = new MaterialData(material);
        if (data != -1) {
            md.setData((byte) data);
        }
        return md;
    }

	private static Pattern TIMEDELTA_PATTERN = Pattern.compile("(\\d+)\\s?(d|h|m|s|ms)", Pattern.CASE_INSENSITIVE);

	public static long timeDeltaToMillis(String s) {
		Matcher m = TIMEDELTA_PATTERN.matcher(s);
		long millis = 0;
		TimeUnit unit;
		while (m.find()) {
			if (m.group(2).toLowerCase().equals("d")) {
				unit = TimeUnit.DAYS;
			} else if (m.group(2).toLowerCase().equals("h")) {
				unit = TimeUnit.HOURS;
			} else if (m.group(2).toLowerCase().equals("m")) {
				unit = TimeUnit.MINUTES;
			} else if (m.group(2).toLowerCase().equals("s")) {
				unit = TimeUnit.SECONDS;
			} else if (m.group(2).toLowerCase().equals("ms")) {
				unit = TimeUnit.MILLISECONDS;
			} else {
				continue;
			}
			millis += unit.toMillis(Long.parseLong(m.group(1)));
		}
		return millis;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("override")) {
			long ticks = 20*60;
			if (args.length > 1) {
				try {
					ticks = timeDeltaToMillis(StringUtils.join(args, "", 1, args.length)) / 50;
					if (ticks < 5) {
						sender.sendMessage(getMsg("min-override-time"));
						return true;
					}
				} catch (IllegalArgumentException ex) {
					sender.sendMessage(getMsg("expecting-number", args[1]));
					return true;
				}
			}
			if (!(sender instanceof Player)) {
				sender.sendMessage(getMsg("no-console"));
				return true;
			}
			final Player p = (Player) sender;

			((Player) sender).setMetadata("portablehorses-override-owner", new FixedMetadataValue(this, System.currentTimeMillis()));
			sender.sendMessage(getMsg("override-enabled"));
			getServer().getScheduler().runTaskLater(this, new Runnable() {
				@Override
				public void run() {
					if (p.isOnline()) {
						p.removeMetadata("portablehorses-override-owner", PortableHorses.this);
						p.sendMessage(getMsg("override-enabled"));
					}
				}
			}, ticks);
			return true;
		} else if (args.length > 0 && args[0].equalsIgnoreCase("reloadconfig")) {
			reloadConfig();
			sender.sendMessage(getMsg("config-reloaded"));
			return true;
		}
		return false;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> results = new ArrayList<String>();
		if (args.length == 1) {
			if ("override".startsWith(args[0].toLowerCase())) {
				results.add("override");
			} else if ("reloadconfig".startsWith(args[0].toLowerCase())) {
				results.add("reloadconfig");
			}
		}
		return results;
	}

	public String getMsg(String key, Object... vars) {
		MessageFormat msg = cachedMessages.get(key);
		if (msg == null) {
			String tpl = null;
			tpl = messagesConfig.getConfig().getString(key, null);
			if (tpl == null) {
				tpl = key;
				if (vars.length > 0) {
					tpl += "[";
					for (int i=0;i<vars.length;i++) {
						tpl += "{" + i + "},";
					}
					if (tpl.endsWith(",")) {
						tpl = tpl.substring(0, tpl.length()-1);
					}
				}
			}
			msg = new MessageFormat(tpl);
			cachedMessages.put(key, msg);
		}
		return ChatColor.translateAlternateColorCodes('&', cachedMessages.get(key).format(vars));
	}

	public void logSpawn(Player player, Horse horse) {
		if (logSpawns) {
			Location l = horse.getLocation();
			spawnLogger.info(player.getName() + " spawned a PortableHorse at " + l.getWorld().getName() + " X:" + l.getX() + ", Y: " + l.getY() + ", Z: " + l.getZ());
		}
	}

	public void logDespawn(Player player, Horse horse) {
		if (logSpawns) {
			Location l = horse.getLocation();
			spawnLogger.info(player.getName() + " despawned a PortableHorse at " + l.getWorld().getName() + " X:" + l.getX() + ", Y: " + l.getY() + ", Z: " + l.getZ());
		}
	}

	public static final class LogFormatter extends Formatter {

		private static final String LINE_SEPARATOR = System.getProperty("line.separator");

		@Override
		public String format(LogRecord record) {
			StringBuilder sb = new StringBuilder();

			sb.append(new Date(record.getMillis()))
					.append(" ")
					.append(record.getLevel().getLocalizedName())
					.append(": ")
					.append(formatMessage(record))
					.append(LINE_SEPARATOR);

			if (record.getThrown() != null) {
				try {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					record.getThrown().printStackTrace(pw);
					pw.close();
					sb.append(sw.toString());
				} catch (Exception ex) {
					// ignore
				}
			}

			return sb.toString();
		}
	}
}
