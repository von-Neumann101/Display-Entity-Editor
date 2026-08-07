package com.example.examplemod.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

public record DisplaySelection(DisplayKind kind, String content) {
    private static final String CONTENT_TAG = "DisplayEntityContent";

    public static DisplaySelection get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return new DisplaySelection(DisplayKind.get(stack), tag == null ? "" : tag.getString(CONTENT_TAG));
    }

    public static DisplaySelection get(Player player, ItemStack stack) {
        CompoundTag data = player.getPersistentData();
        String content = data.contains(CONTENT_TAG)
                ? data.getString(CONTENT_TAG) : get(stack).content;
        return new DisplaySelection(DisplayKind.get(player, stack), content);
    }

    public static void set(ItemStack stack, DisplaySelection selection) {
        DisplayKind.set(stack, selection.kind);
        stack.getOrCreateTag().putString(CONTENT_TAG, selection.content);
    }

    public static void set(Player player, DisplaySelection selection) {
        DisplayKind.set(player, selection.kind);
        player.getPersistentData().putString(CONTENT_TAG, selection.content);
    }

    public boolean isValid() {
        if (content.length() > 128) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(content);
        return switch (kind) {
            case BLOCK -> id != null && ForgeRegistries.BLOCKS.getValue(id) != null
                    && ForgeRegistries.BLOCKS.getValue(id) != Blocks.AIR;
            case ITEM -> id != null && ForgeRegistries.ITEMS.getValue(id) != null
                    && ForgeRegistries.ITEMS.getValue(id) != Items.AIR;
            case TEXT -> !content.isBlank() && !content.contains("\n") && !content.contains("\r");
        };
    }

    public BlockState blockState(BlockState fallback) {
        ResourceLocation id = ResourceLocation.tryParse(content);
        Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
        return kind == DisplayKind.BLOCK && block != null && block != Blocks.AIR
                ? block.defaultBlockState() : fallback;
    }

    public ItemStack itemStack(BlockState fallback) {
        ResourceLocation id = ResourceLocation.tryParse(content);
        net.minecraft.world.item.Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return kind == DisplayKind.ITEM && item != null && item != Items.AIR
                ? new ItemStack(item) : new ItemStack(fallback.getBlock().asItem());
    }

    public Component text(BlockState fallback) {
        return kind == DisplayKind.TEXT && isValid() ? Component.literal(content) : fallback.getBlock().getName();
    }
}
