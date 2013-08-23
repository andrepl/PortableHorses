package com.norcode.bukkit.portablehorses;

import com.comphenix.protocol.Packets;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import net.minecraft.server.v1_6_R2.*;
import net.minecraft.v1_6_R2.org.bouncycastle.util.encoders.Base64;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_6_R2.entity.CraftHorse;
import org.bukkit.craftbukkit.v1_6_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_6_R2.inventory.CraftItemStack;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.util.*;

public class PortableHorses extends JavaPlugin implements Listener {

    public static final String DISPLAY_NAME = "Portable Horse";
    private static final String LORE_PREFIX = ChatColor.DARK_GREEN + "" + ChatColor.DARK_PURPLE + "" + ChatColor.GRAY;

    private ProtocolManager protocolManager;
    private Field containerCountField;
    private boolean debugMode = false;
    private boolean usePermissions = true;
    private boolean storeArmor = true;
    private boolean storeInventory = true;

    public static LinkedList<String> nbtToLore(NBTTagCompound tag) {
        byte[] tagdata = NBTCompressedStreamTools.a(tag);
        LinkedList<String> lines = new LinkedList<String>();
        String encoded = new String(Base64.encode(tagdata));
        while (encoded.length() > 32760) {
            lines.add(encoded.substring(0, 32760));
            encoded = encoded.substring(32760);
        }
        if (encoded.length() > 0) {
            lines.add(ChatColor.BLACK + encoded);
        }
        return lines;
    }

    public void onLoad() {
        protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public NBTTagCompound nbtFromLore(List<String> lore) {
        String data = "";
        for (int i=0;i<lore.size();i++) {
            if (lore.get(i).startsWith(ChatColor.BLACK.toString())) {
                data += lore.get(i).substring(2);
            }
        }
        byte[] decoded = null;
        try {
            decoded = Base64.decode(data);
        } catch (Exception e) {
            e.printStackTrace();
            debug(data.toString());
        }
        NBTTagCompound tag = NBTCompressedStreamTools.a(decoded);
        return tag;

    }

    public ItemStack saveToSaddle(Horse horse, ItemStack saddle) {
        NBTTagCompound tag = new NBTTagCompound();
        EntityHorse eh = ((CraftHorse) horse).getHandle();
        eh.b(tag);

        ItemMeta meta = saddle.getItemMeta();
        if (meta == null) {
            meta = getServer().getItemFactory().getItemMeta(Material.SADDLE);
        }
        meta.setDisplayName(DISPLAY_NAME);
        if (horse.getCustomName() != null) {
            meta.setDisplayName(horse.getCustomName());
        }
        LinkedList<String> lore = nbtToLore(tag);

        lore.addFirst(LORE_PREFIX + horse.getVariant().name() + "/" + horse.getColor().name());
        meta.setLore(lore);
        saddle.setItemMeta(meta);
        return saddle;
    }

    public void restoreHorseFromSaddle(ItemStack stack, Horse horse) {
        EntityHorse eh = ((CraftHorse) horse).getHandle();
        if (stack.hasItemMeta()) {
            List<String> lore = stack.getItemMeta().getLore();
            if (lore != null) {
                NBTTagCompound tag = nbtFromLore(lore);
                debug("Restoring Horse: " + tag.toString());
                eh.a(tag);
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig();
        try {
            containerCountField = EntityPlayer.class.getDeclaredField("containerCounter");
            containerCountField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Set<Integer> packets = new HashSet<Integer>();
        packets.add(0x67);
        packets.add(0x68);
        packets.add(0x6B);
        packets.add(0xFA);
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, ConnectionSide.SERVER_SIDE, ListenerPriority.NORMAL, packets) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                switch(packet.getID()){
                    case 0x68:
                        try{
                            ItemStack[] read = packet.getItemArrayModifier().read(0);
                            for(int i=0; i<read.length; i++) {
                                read[i] = filterLore(read[i]);
                            }
                            packet.getItemArrayModifier().write(0, read);
                        }
                        catch(Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case 0xFA:
                        try{
                            EntityPlayer p = ((CraftPlayer)event.getPlayer()).getHandle();
                            ContainerMerchant cM = ((ContainerMerchant) p.activeContainer);
                            Field fieldMerchant = cM.getClass().getDeclaredField("merchant");
                            fieldMerchant.setAccessible(true);
                            IMerchant imerchant = (IMerchant)fieldMerchant.get(cM);

                            MerchantRecipeList merchantrecipelist = imerchant.getOffers(p);
                            MerchantRecipeList nlist = new MerchantRecipeList();
                            for(Object orecipe : merchantrecipelist) {
                                MerchantRecipe recipe = (MerchantRecipe)orecipe;
                                int uses = recipe.i().getInt("uses");
                                int maxUses = recipe.i().getInt("maxUses");
                                MerchantRecipe nrecipe = new MerchantRecipe(filterLore(recipe.getBuyItem1()), filterLore(recipe.getBuyItem2()), filterLore(recipe.getBuyItem3()));
                                nrecipe.a(maxUses-7);
                                for(int i=0; i < uses; i++) {
                                    nrecipe.f();
                                }
                                nlist.add(nrecipe);
                            }

                            ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
                            DataOutputStream dataoutputstream = new DataOutputStream(bytearrayoutputstream);
                            dataoutputstream.writeInt(containerCountField.getInt(p));
                            nlist.a(dataoutputstream);
                            byte[] b = bytearrayoutputstream.toByteArray();
                            packet.getByteArrays().write(0, b);
                            packet.getIntegers().write(0, b.length);
                        }
                        catch(Exception e){
                            e.printStackTrace();
                        }
                        break;
                    default:
                        try{
                            packet.getItemModifier().write(0, filterLore(packet.getItemModifier().read(0)));
                        }
                        catch(Exception e) {
                            e.printStackTrace();
                        }

                }
            }

        });
        getServer().getPluginManager().registerEvents(this, this);
    }

    private net.minecraft.server.v1_6_R2.ItemStack filterLore(net.minecraft.server.v1_6_R2.ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        ItemStack stack = CraftItemStack.asCraftMirror(itemStack);
        return CraftItemStack.asNMSCopy(filterLore(stack));
    }

    private ItemStack filterLore(ItemStack itemStack) {
        if (itemStack != null) {
            ItemStack stack = itemStack.clone();
            if (stack.hasItemMeta() && stack.getItemMeta().hasLore() && stack.getItemMeta().getLore().get(0).startsWith(LORE_PREFIX)) {
                ItemMeta meta = stack.getItemMeta();
                List<String> lore = meta.getLore();
                LinkedList<String> newLore = new LinkedList<String>();
                for (String line: lore) {
                    if (!line.startsWith(ChatColor.BLACK.toString())) {
                        newLore.add(line);
                    }
                }
                meta.setLore(newLore);
                stack.setItemMeta(meta);
            }
            return stack;
        }
        return null;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        this.usePermissions = getConfig().getBoolean("use-permissions", true);
        this.debugMode = getConfig().getBoolean("debug", false);
        this.storeArmor = getConfig().getBoolean("store-armor", true);
        this.storeInventory = getConfig().getBoolean("store-inventory", true);
    }

    public void debug(String s) {
        if (debugMode) {
            getLogger().info(s);
        }
    }

    @EventHandler
    public void onSaddleEvent(final InventoryClickEvent event) {
        if (event.getInventory() instanceof HorseInventory) {
            Horse horse = ((Horse) event.getInventory().getHolder());
            if (event.isShiftClick()) {
                if (event.getRawSlot() != 0) {

                    if (isPortableHorseSaddle(event.getCurrentItem())) {
                        event.setCancelled(true);
                    } else if (event.getCurrentItem().getType().equals(Material.SADDLE) && ((HorseInventory) event.getInventory()).getSaddle() == null) {
                        onSaddled(event, horse, event.getCurrentItem());
                    }
                } else if (event.getRawSlot() == 0 && event.getWhoClicked().getInventory().firstEmpty() != -1 && isPortableHorseSaddle(event.getCurrentItem())) {
                    // Removing a saddle by shift-click.
                    onUnsaddled(event, horse, event.getCurrentItem());
                }
            } else if (event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_ONE) {
                if (event.getRawSlot() == 0 && event.getCurrentItem().getType() == Material.AIR) {
                    if (isPortableHorseSaddle(event.getCursor())) {
                        // Placing a saddle
                        event.setCancelled(true);
                    } else if (event.getCursor().getType() == Material.SADDLE) {
                        debug("Saddling!");
                        onSaddled(event, horse, event.getCursor());
                    }
                }
            } else if (event.getAction() == InventoryAction.PICKUP_ALL || event.getAction() == InventoryAction.PICKUP_ONE || event.getAction() == InventoryAction.PICKUP_HALF) {
                if (event.getRawSlot() == 0 && isPortableHorseSaddle(event.getCurrentItem())) {
                    // removed a saddle.
                    onUnsaddled(event, horse, event.getCurrentItem());
                }
            }
        }
    }

    public void onSaddled(InventoryClickEvent event, Horse horse, ItemStack saddle) {
        debug(horse + "Saddled.");
        if (!usePermissions || event.getWhoClicked().hasPermission("portablehorses.saddle")) {
            saveToSaddle(horse, saddle);
            horse.getInventory().setSaddle(saddle);
        }
    }

    public void onUnsaddled(InventoryClickEvent event, Horse horse, ItemStack saddle) {
        debug(horse + "Unsaddled.");
        if (!usePermissions || event.getWhoClicked().hasPermission("portablehorses.unsaddle")) {
            if (!storeArmor) {
                if (horse.getInventory().getArmor() != null && horse.getInventory().getArmor().getType() != Material.AIR) {
                    horse.getWorld().dropItem(horse.getLocation(), horse.getInventory().getArmor());
                    horse.getInventory().setArmor(null);
                }
            }
            if (!storeInventory && horse.isCarryingChest()) {
                ItemStack toDrop;
                for (int i=2;i<horse.getInventory().getContents().length;i++) {
                    toDrop = horse.getInventory().getItem(i);
                    horse.getWorld().dropItem(horse.getLocation(), toDrop);
                    horse.getInventory().setItem(i, null);
                }
            }
            saveToSaddle(horse, saddle);
            horse.remove();
        }
    }

    private boolean isPortableHorseSaddle(ItemStack currentItem) {
        if (currentItem.getType().equals(Material.SADDLE)) {
            if (currentItem.hasItemMeta()) {
                if (currentItem.getItemMeta().hasLore()) {
                    List<String> lore = currentItem.getItemMeta().getLore();
                    if (lore.size() > 1 && lore.get(0).startsWith(LORE_PREFIX)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onClickSaddle(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType().equals(Material.SADDLE)) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Location spawnLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
                Horse horse = (Horse) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.HORSE);
                restoreHorseFromSaddle(event.getItem(), horse);
                horse.getInventory().setSaddle(event.getItem());
                event.getPlayer().setItemInHand(null);
            }
        }
    }


}
