package org.shininet.bukkit.itemrenamer;


import com.comphenix.protocol.utility.MinecraftReflection;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.google.common.base.Preconditions;

public abstract class CompoundStore {

    protected ItemStack stack;

    protected CompoundStore(ItemStack stack) {
        Preconditions.checkNotNull(stack, "stack cannot be NULL.");
        this.stack = stack;
        if (!MinecraftReflection.isCraftItemStack(stack)) {
            this.stack = MinecraftReflection.getBukkitItemStack(stack);
        }
    }

    /**
     * Retrieve the item stack where the additional compound will be stored.
     * @return Item stack where the compound will be stored.
     */
    public ItemStack getStack() {
        return stack;
    }

    /**
     * Save a given compound to the given item stack.
     * @param compound - the compound to save.
     * @return The current item stack.
     */
    public abstract ItemStack saveCompound(NbtCompound compound);

    /**
     * Load the saved compound.
     * @return The saved compound, or NULL.
     */
    public abstract NbtCompound loadCompound();

    /**
     * Retrieve a compound store that saves and loads the compound directly in the NbtCompound tag.
     * @param stack - the ItemStack whose tag will be used to save and load NbtCompounds.
     * @param key - the compound key.
     * @return The compound store.
     */
    public static CompoundStore getNativeStore(ItemStack stack, final String key) {
        return new CompoundStore(stack) {
            @Override
            public ItemStack saveCompound(NbtCompound compound) {
                getCompound(stack).put(key, compound);
                return stack;
            }

            @Override
            public NbtCompound loadCompound() {
                NbtCompound data = getCompound(stack);

                // Check for our marker
                if (data.containsKey(key)) {
                    return data.getCompound(key);
                } else {
                    return null;
                }
            }

            /**
             * Retrieve the NbtCompound that stores additional data in an ItemStack.
             * @param stack - the item stack.
             * @return The additional NbtCompound.
             */
            private NbtCompound getCompound(ItemStack stack) {
                // It should have been a compound in the API ...
                return NbtFactory.asCompound(NbtFactory.fromItemTag(stack));
            }
        };
    }

    /**
     * Determine if the display name of the given item is used to set the name of a mob in any way.
     * <p>
     * This includes spawner eggs and naming tags.
     * @stack - the stack to check.
     * @return TRUE if it does, FALSE otherwise.
     */
    private static boolean isNamingItem(ItemStack stack) {
        return stack.getType() == Material.MONSTER_EGG ||
                stack.getType() == Material.MONSTER_EGGS ||
                stack.getType() == Material.NAME_TAG;
    }
}