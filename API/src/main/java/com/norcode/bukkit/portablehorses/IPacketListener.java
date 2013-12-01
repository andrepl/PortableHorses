package com.norcode.bukkit.portablehorses;

import org.bukkit.inventory.ItemStack;

public interface IPacketListener {
	void registerListeners();

	ItemStack unfilterLore(ItemStack stack);

	ItemStack[] filterLore(ItemStack[] stacks);

	ItemStack filterLore(ItemStack stack);
}
