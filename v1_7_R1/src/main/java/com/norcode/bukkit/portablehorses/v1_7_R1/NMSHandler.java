package com.norcode.bukkit.portablehorses.v1_7_R1;

import com.norcode.bukkit.portablehorses.NMS;
import net.minecraft.server.v1_7_R1.EntityHorse;
import net.minecraft.server.v1_7_R1.NBTCompressedStreamTools;
import net.minecraft.server.v1_7_R1.NBTTagCompound;

import net.minecraft.util.io.netty.buffer.ByteBuf;
import net.minecraft.util.io.netty.buffer.Unpooled;
import net.minecraft.util.io.netty.handler.codec.base64.Base64;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_7_R1.entity.CraftHorse;
import org.bukkit.entity.Horse;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;


public class NMSHandler extends NMS {



    private LinkedList<String> nbtToLore(NBTTagCompound tag) {
        if (tag.hasKey("SaddleItem")) {
            tag.remove("SaddleItem");
        }

		ByteBuf tagdata = Unpooled.wrappedBuffer(NBTCompressedStreamTools.a(tag));
		LinkedList<String> lines = new LinkedList<String>();
        String encoded = Base64.encode(tagdata).toString(Charset.defaultCharset());
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
        ByteBuf decoded = null;
        try {
            decoded = Base64.decode(Unpooled.copiedBuffer(data.getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
        }
		byte[] bytes = new byte[decoded.readableBytes()];
		decoded.getBytes(0, bytes);
		NBTTagCompound tag = NBTCompressedStreamTools.a(bytes);
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
