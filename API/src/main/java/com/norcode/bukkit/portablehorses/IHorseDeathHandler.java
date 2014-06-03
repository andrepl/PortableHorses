package com.norcode.bukkit.portablehorses;


import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public interface IHorseDeathHandler {
	public void handleHorseDeath(EntityDeathEvent event, ItemStack emptySaddle);
}
