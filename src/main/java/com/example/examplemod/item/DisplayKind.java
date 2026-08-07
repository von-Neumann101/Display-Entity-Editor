package com.example.examplemod.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public enum DisplayKind {
    BLOCK("block"),
    ITEM("item"),
    TEXT("text");

    private static final String TAG = "DisplayEntityType";
    private final String id;

    DisplayKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DisplayKind get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? BLOCK : fromId(tag.getString(TAG));
    }

    public static DisplayKind get(Player player, ItemStack stack) {
        CompoundTag data = player.getPersistentData();
        return data.contains(TAG) ? fromId(data.getString(TAG)) : get(stack);
    }

    private static DisplayKind fromId(String id) {
        for (DisplayKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return BLOCK;
    }

    public static void set(ItemStack stack, DisplayKind kind) {
        stack.getOrCreateTag().putString(TAG, kind.id);
    }

    public static void set(Player player, DisplayKind kind) {
        player.getPersistentData().putString(TAG, kind.id);
    }
}
