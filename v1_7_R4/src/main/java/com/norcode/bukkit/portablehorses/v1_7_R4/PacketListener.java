package com.norcode.bukkit.portablehorses.v1_7_R4;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerOptions;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.utility.StreamSerializer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.comphenix.protocol.wrappers.nbt.NbtList;
import com.comphenix.protocol.wrappers.nbt.NbtType;
import com.comphenix.protocol.wrappers.nbt.io.NbtTextSerializer;
import com.google.common.eventbus.EventBus;
import com.norcode.bukkit.portablehorses.IPacketListener;
import com.norcode.bukkit.portablehorses.NMS;
import net.minecraft.server.v1_7_R4.ChatComponentText;
import net.minecraft.server.v1_7_R4.ChatHoverable;
import net.minecraft.server.v1_7_R4.EnumHoverAction;
import net.minecraft.server.v1_7_R4.IChatBaseComponent;
import net.minecraft.server.v1_7_R4.MojangsonParser;
import net.minecraft.server.v1_7_R4.NBTTagCompound;
import net.minecraft.server.v1_7_R4.NBTTagList;
import net.minecraft.server.v1_7_R4.NBTTagString;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.shininet.bukkit.itemrenamer.component.Component;
import org.shininet.bukkit.itemrenamer.component.SpigotStackWriterComponent;
import org.shininet.bukkit.itemrenamer.merchant.MerchantRecipe;
import org.shininet.bukkit.itemrenamer.merchant.MerchantRecipeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class PacketListener implements IPacketListener {
	public JavaPlugin plugin;
	public ProtocolManager protocolManager;
	private NbtTextSerializer serializer;

	public PacketListener(JavaPlugin plugin) {
		this.plugin = plugin;
		protocolManager = ProtocolLibrary.getProtocolManager();
        if (SpigotStackWriterComponent.isRequired()) {
            Component workaround = new SpigotStackWriterComponent(ProtocolLibrary.getProtocolManager());
            workaround.register(plugin, new EventBus());
        }
		this.registerListeners();

	}

	public void registerListeners() {
		PacketAdapter.AdapterParameteters params = PacketAdapter.params()
				.plugin(plugin)
				.listenerPriority(ListenerPriority.HIGH)
				.types(PacketType.Play.Server.SET_SLOT,
					   PacketType.Play.Server.WINDOW_ITEMS,
					   PacketType.Play.Server.CUSTOM_PAYLOAD,
					   PacketType.Play.Server.CHAT);

		protocolManager.addPacketListener(new PacketAdapter(params) {
			@Override
			public void onPacketSending(PacketEvent event) {
				PacketContainer packet = event.getPacket();
					Player player = event.getPlayer();
					if (event.getPacketType().equals(PacketType.Play.Server.CHAT)) {
						StructureModifier<WrappedChatComponent> cm = packet.getChatComponents();
						WrappedChatComponent unfiltered;
						WrappedChatComponent filtered;
						for (int i=0; i<cm.size(); i++) {
							unfiltered = cm.read(i);
							cm.write(i, WrappedChatComponent.fromHandle(
									filterChatComponent((IChatBaseComponent) cm.read(i).getHandle())));
						}

					} else
					if (event.getPacketType().equals(PacketType.Play.Server.SET_SLOT)) {
						StructureModifier<ItemStack> sm = packet.getItemModifier();
						for (int i = 0; i < sm.size(); i++) {
							filterLore(sm.read(i));
						}
					} else if (event.getPacketType().equals(PacketType.Play.Server.WINDOW_ITEMS)) {
						StructureModifier<ItemStack[]> smArray = packet.getItemArrayModifier();
						for (int i = 0; i < smArray.size(); i++) {
							filterLore(smArray.read(i));
						}
					} else if (event.getPacketType().equals(PacketType.Play.Server.CUSTOM_PAYLOAD)) {
						String packetName = packet.getStrings().read(0);
						// Make sure this is a merchant list
						if (packetName.equals("MC|TrList")) {
							try {
								byte[] result = processMerchantList(packet.getByteArrays().read(0));
								packet.getByteArrays().write(0, result);
							} catch (IOException e) {
								plugin.getLogger().warning("Couldn't access merchant list");
							}
						}
					}
			}
		});
		params = PacketAdapter.params()
				.plugin(plugin)
				.listenerPriority(ListenerPriority.HIGH)
				.options(ListenerOptions.INTERCEPT_INPUT_BUFFER)
				.types(PacketType.Play.Client.SET_CREATIVE_SLOT);

		protocolManager.addPacketListener(new PacketAdapter(params) {

			@Override
			public void onPacketReceiving(PacketEvent event) {
				if (event.getPacketType().equals(PacketType.Play.Client.SET_CREATIVE_SLOT)
						|| event.getPacketType().equals(PacketType.Play.Server.SET_SLOT)) {
					DataInputStream input = event.getNetworkMarker().getInputStream();
					if (input == null) {
						return;
					}

					try {
						// Read slot
						input.readShort();
						// read  & unfilter itemstack
						ItemStack stack = readItemStack(input, new StreamSerializer());
						unfilterLore(stack);
						// And write it back
						event.getPacket().getItemModifier().write(0, stack);

					} catch (IOException e) {
						// Just let ProtocolLib handle it
						throw new RuntimeException("Cannot undo NBT scrubber.", e);
					}
				}
			}
		});

	}

	private IChatBaseComponent filterChatComponent(IChatBaseComponent chat) {
		ChatHoverable hover = chat.getChatModifier().i();
		if (hover != null && hover.a() == EnumHoverAction.SHOW_ITEM) {
			NBTTagCompound stack = (NBTTagCompound) MojangsonParser.parse(hover.b().e());
			if (stack.getShort("id") == 329 && stack.hasKey("tag")) {
				NBTTagCompound tag = stack.getCompound("tag");
				if (tag.hasKey("display")) {
					NBTTagCompound display = tag.getCompound("display");
					if (display.hasKey("Lore")) {
						NBTTagList lore = display.getList("Lore", 8);
						NBTTagList newLore = new NBTTagList();
						String line;
						for (int i=0;i<lore.size();i++) {
							line = lore.getString(i);
							if (line.startsWith(ChatColor.BLACK.toString())) {
								continue;
							}
							newLore.add(new NBTTagString(line));
						}
						display.set("Lore", newLore);
						chat.getChatModifier().a(new ChatHoverable(EnumHoverAction.SHOW_ITEM, new ChatComponentText(stack.toString())));
					}
				}
			}
		}

		for (IChatBaseComponent child: (List<IChatBaseComponent>) chat.a()) {
			filterChatComponent(child);
		}
		return chat;
	}

	/**
	 * Moves the encoded horse data from the NBT Tag,
	 * back into the lore of the given itemstack.
	 * @param stack an ItemStack coming from the a client-to-server packet
	 * @return the same itemstack prepared for server side use.
	 */
	public ItemStack unfilterLore(ItemStack stack) {
		if (stack != null) {
			if (stack.hasItemMeta() && stack.getItemMeta().hasLore()) {
				if (stack.getItemMeta().getLore().get(0).startsWith(NMS.LORE_PREFIX) || stack.getItemMeta().getLore().get(0).equals("empty")) {
					if (!MinecraftReflection.isCraftItemStack(stack)) {
						stack = MinecraftReflection.getBukkitItemStack(stack);
					}
					NbtCompound tag = NbtFactory.asCompound(NbtFactory.fromItemTag(stack));
					if (tag.containsKey("PORTABLEHORSE")) {
						ItemMeta meta = stack.getItemMeta();
						LinkedList<String> lore = new LinkedList<String>();
						for (String s: meta.getLore()) {
							if (!s.startsWith(ChatColor.BLACK.toString())) {
								lore.add(s);
							}
						}
						NbtList dataList = tag.getList("PORTABLEHORSE");
						dataList.setElementType(NbtType.TAG_STRING);
						Iterator<String> it = dataList.iterator();
						while (it.hasNext()) {
							lore.add(it.next());
						}
						meta.setLore(lore);
						stack.setItemMeta(meta);
					}
				}
			}
		}
		return stack;
	}


	public ItemStack[] filterLore(ItemStack[] stacks) {
		for (int i=0;i<stacks.length;i++) {
			if (stacks[i] != null) {
				stacks[i] = filterLore(stacks[i]);
			}
		}
		return stacks;
	}

	/**
	 * Move the encoded horse data from the lore of the given itemstack
	 * into a custom NBT Tag for use in server-to-client packets.
	 * @param stack the itemstack to filter.
	 * @return the same itemstack, filtered.
	 */
	public ItemStack filterLore(ItemStack stack) {
		if (stack != null) {
			if (stack.hasItemMeta() && stack.getItemMeta().hasLore()) {
				if (stack.getItemMeta().getLore().get(0).startsWith(NMS.LORE_PREFIX) || stack.getItemMeta().getLore().get(0).equals("empty")) {
					if (!MinecraftReflection.isCraftItemStack(stack)) {
						stack = MinecraftReflection.getBukkitItemStack(stack);
					}
					ItemMeta meta = stack.getItemMeta();
					List<String> lore = meta.getLore();
					List<String> data = new LinkedList<String>();
					LinkedList<String> newLore = new LinkedList<String>();
					for (String line: lore) {
						if (line.startsWith(ChatColor.BLACK.toString())) {
							data.add(line);
						} else {
							newLore.add(line);
						}
					}
					meta.setLore(newLore);
					stack.setItemMeta(meta);
					NbtCompound tag = NbtFactory.asCompound(NbtFactory.fromItemTag(stack));
					if (!tag.containsKey("ench")) {
						tag.put("ench", NbtFactory.ofList("ench"));
					}
					tag.put("PORTABLEHORSE", NbtFactory.ofList("PORTABLEHORSE", data));
				}
			}
			return stack;
		}
		return null;
	}

	/**
	 * Read an ItemStack from a input stream without "scrubbing" the NBT content.
	 * @param input - the input stream.
	 * @param serializer - methods for serializing Minecraft object.
	 * @return The deserialized item stack.
	 * @throws IOException If anything went wrong.
	 */
	private ItemStack readItemStack(DataInputStream input, StreamSerializer serializer) throws IOException {
		ItemStack result = null;
		short type = input.readShort();

		if (type >= 0) {
			byte amount = input.readByte();
			short damage = input.readShort();

			result = new ItemStack(type, amount, damage);
			NbtCompound tag = serializer.deserializeCompound(input);

			if (tag != null) {
				result = MinecraftReflection.getBukkitItemStack(result);
				NbtFactory.setItemTag(result, tag);
			}
		}
		return result;
	}

	private byte[] processMerchantList(byte[] data) throws IOException {
		ByteArrayInputStream source = new ByteArrayInputStream(data);
		DataInputStream input = new DataInputStream(source);

		int containerCounter = input.readInt();
		MerchantRecipeList list = MerchantRecipeList.readRecipiesFromStream(input);

		// Process each and every item stack
		for (MerchantRecipe recipe : list) {
			recipe.setItemToBuy(filterLore(recipe.getItemToBuy()));
			recipe.setSecondItemToBuy(filterLore(recipe.getSecondItemToBuy()));
			recipe.setItemToSell(filterLore(recipe.getItemToSell()));
		}

		// Write the result back
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		DataOutputStream output = new DataOutputStream(buffer);

		output.writeInt(containerCounter);
		list.writeRecipiesToStream(output);
		return buffer.toByteArray();
	}

}