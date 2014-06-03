package com.norcode.bukkit.portablehorses.v1_7_R3_2;

import com.norcode.bukkit.portablehorses.IHorseDeathHandler;
import org.bukkit.entity.Horse;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

public class HorseDeathHandler implements IHorseDeathHandler {
	public void handleHorseDeath(EntityDeathEvent event, ItemStack emptySaddle) {
		Horse h = (Horse) event.getEntity();
		Iterator<ItemStack> sit = event.getDrops().iterator();
		ItemStack s = null;
		boolean found = false;
		ItemStack invSaddle = h.getInventory().getSaddle();
		while (sit.hasNext()) {
			s = sit.next();
			if (s.equals(invSaddle)) {
				sit.remove();
				found = true;
				break;
			}
		}
		if (found) {
			event.getDrops().add(emptySaddle);
		}
	}
}
