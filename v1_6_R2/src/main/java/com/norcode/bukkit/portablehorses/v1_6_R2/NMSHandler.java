package com.norcode.bukkit.portablehorses.v1_6_R2;

import com.norcode.bukkit.portablehorses.NMS;
import net.minecraft.server.v1_6_R2.EntityHorse;
import net.minecraft.server.v1_6_R2.NBTCompressedStreamTools;
import net.minecraft.server.v1_6_R2.NBTTagCompound;
import net.minecraft.v1_6_R2.org.bouncycastle.util.encoders.Base64;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_6_R2.entity.CraftHorse;
import org.bukkit.entity.Horse;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedList;
import java.util.List;


public class NMSHandler extends NMS {



    private LinkedList<String> nbtToLore(NBTTagCompound tag) {
        if (tag.hasKey("SaddleItem")) {
            tag.remove("SaddleItem");
        }
        byte[] tagdata = NBTCompressedStreamTools.a(tag);
        LinkedList<String> lines = new LinkedList<String>();
        String encoded = new String(Base64.encode(tagdata));
        while (encoded.length() > 32760) {
            lines.add(ChatColor.BLACK + encoded.substring(0, 32760));
            encoded = encoded.substring(32760);
        }
        if (encoded.length() > 0) {
            lines.add(ChatColor.BLACK + encoded);
        }
        return lines;
    }

    private NBTTagCompound nbtFromLore(List<String> lore) {
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
        }
        NBTTagCompound tag = NBTCompressedStreamTools.a(decoded);
        return tag;
    }

    @Override
    public void saveToSaddle(Horse horse, ItemStack saddle) {
        NBTTagCompound tag = new NBTTagCompound();
        EntityHorse eh = ((CraftHorse) horse).getHandle();
        eh.b(tag);
        tag.setDouble("currentHP", horse.getHealth());
        ItemMeta meta = saddle.getItemMeta();
        meta.setDisplayName(DISPLAY_NAME);
        if (horse.getCustomName() != null) {
            meta.setDisplayName(horse.getCustomName());
        }
        LinkedList<String> lore = nbtToLore(tag);
        lore.addFirst(LORE_PREFIX + horse.getVariant().name() + "/" + horse.getColor().name());
        meta.setLore(lore);
        saddle.setItemMeta(meta);
    }

    @Override
    public void restoreHorseFromSaddle(ItemStack stack, Horse horse) {
        EntityHorse eh = ((CraftHorse) horse).getHandle();
        if (stack.hasItemMeta()) {
            List<String> lore = stack.getItemMeta().getLore();
            if (lore != null) {
                NBTTagCompound tag = nbtFromLore(lore);
                double hp = -1;
                if (tag.hasKey("currentHP")) {
                    hp = tag.getDouble("currentHP");
                    tag.remove("currentHP");
                }
                eh.a(tag);
                if (hp != -1) {
                    horse.setHealth(hp);
                }
            }
        }
    }
}
