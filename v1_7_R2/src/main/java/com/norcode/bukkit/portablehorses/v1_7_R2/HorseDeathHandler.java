package com.norcode.bukkit.portablehorses.v1_7_R2;

import com.norcode.bukkit.portablehorses.IHorseDeathHandler;
import org.bukkit.entity.Horse;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public class HorseDeathHandler implements IHorseDeathHandler {
	public void handleHorseDeath(EntityDeathEvent event, ItemStack emptySaddle) {
		Horse h = (Horse) event.getEntity();
		h.getInventory().setSaddle(emptySaddle);
	}
}
