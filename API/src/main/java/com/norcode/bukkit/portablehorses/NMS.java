package com.norcode.bukkit.portablehorses;

import org.bukkit.ChatColor;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;

public abstract class NMS {
    public static final String LORE_PREFIX = ChatColor.DARK_GREEN + "" + ChatColor.DARK_PURPLE + "" + ChatColor.GRAY;
    public static final String DISPLAY_NAME = "Portable Horse";
    public abstract void saveToSaddle(Horse horse, ItemStack saddle);
    public abstract void restoreHorseFromSaddle(ItemStack stack, Horse horse);
	public abstract LivingEntity getProjectileShooter(Projectile p);
}
