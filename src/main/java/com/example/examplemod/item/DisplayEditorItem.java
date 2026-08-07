package com.example.examplemod.item;

import com.example.examplemod.util.DisplayTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class DisplayEditorItem extends Item {
    public DisplayEditorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockState clickedState = level.getBlockState(context.getClickedPos());
            BlockPos target = context.getClickedPos().relative(context.getClickedFace());
            DisplaySelection selection = context.getPlayer() == null
                    ? DisplaySelection.get(stack) : DisplaySelection.get(context.getPlayer(), stack);
            if (context.getPlayer() != null) {
                DisplaySelection.set(context.getPlayer(), selection);
            }
            Display display = switch (selection.kind()) {
                case BLOCK -> new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
                case ITEM -> new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
                case TEXT -> new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
            };
            double y = target.getY() + (display instanceof Display.BlockDisplay ? 0.0D : 0.5D);
            display.setPos(target.getX() + 0.5D, y, target.getZ() + 0.5D);
            DisplayTransform.initialize(display, clickedState, selection);
            level.addFreshEntity(display);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
