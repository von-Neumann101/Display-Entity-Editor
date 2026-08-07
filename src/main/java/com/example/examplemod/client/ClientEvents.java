package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.util.DisplayTransform;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping TYPE_MENU_KEY = new KeyMapping(
            "key.examplemod.type_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.examplemod");
    public static final KeyMapping MODE_KEY = new KeyMapping(
            "key.examplemod.mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.examplemod");

    private static int selectedEntityId = -1;
    private static EditMode editMode = EditMode.NUMERIC;
    private static EditMode viewEditMode = EditMode.SCALE;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.player == null || minecraft.screen != null) {
            return;
        }

        while (TYPE_MENU_KEY.consumeClick()) {
            InteractionHand hand = editorHand(minecraft);
            if (hand != null) {
                minecraft.setScreen(new TypeSelectionScreen(hand));
            }
        }
        while (MODE_KEY.consumeClick()) {
            toggleEditMode();
            Display selected = selectedDisplay();
            if (selected != null) {
                minecraft.setScreen(new DisplayEditorScreen(selected));
            }
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !minecraft.player.getItemInHand(event.getHand()).is(ExampleMod.DISPLAY_EDITOR.get())) {
            return;
        }

        Display display = findDisplay(minecraft);
        if (display == null || (!event.isAttack() && !event.isUseItem())) {
            return;
        }

        selectedEntityId = display.getId();
        if (event.isUseItem()) {
            minecraft.setScreen(new DisplayEditorScreen(display));
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void renderSelection(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Display display = selectedDisplay();
        if (display == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        AABB bounds = DisplayTransform.visualBounds(display).move(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(event.getPoseStack(), lines, bounds, 0.2F, 1.0F, 0.2F, 1.0F);
        buffers.endBatch(RenderType.lines());
    }

    public static Display selectedDisplay() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        Entity entity = minecraft.level.getEntity(selectedEntityId);
        return entity instanceof Display display ? display : null;
    }

    public static EditMode editMode() {
        return editMode;
    }

    public static void toggleEditMode() {
        if (editMode == EditMode.NUMERIC) {
            editMode = viewEditMode;
        } else {
            viewEditMode = editMode;
            editMode = EditMode.NUMERIC;
        }
    }

    public static void toggleSliderMode() {
        editMode = editMode == EditMode.SCALE ? EditMode.ROTATION : EditMode.SCALE;
        viewEditMode = editMode;
    }

    private static InteractionHand editorHand(Minecraft minecraft) {
        if (minecraft.player.getMainHandItem().is(ExampleMod.DISPLAY_EDITOR.get())) {
            return InteractionHand.MAIN_HAND;
        }
        return minecraft.player.getOffhandItem().is(ExampleMod.DISPLAY_EDITOR.get())
                ? InteractionHand.OFF_HAND : null;
    }

    private static Display findDisplay(Minecraft minecraft) {
        Vec3 start = minecraft.player.getEyePosition(1.0F);
        double reach = minecraft.player.getEntityReach();
        Vec3 end = start.add(minecraft.player.getViewVector(1.0F).scale(reach));
        double closestDistance = reach * reach;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) {
            closestDistance = Math.min(closestDistance, start.distanceToSqr(minecraft.hitResult.getLocation()));
        }

        Display closest = null;
        AABB searchArea = new AABB(start, end).inflate(8.0D);
        for (Entity entity : minecraft.level.getEntities(minecraft.player, searchArea,
                candidate -> candidate instanceof Display)) {
            Display display = (Display) entity;
            AABB bounds = DisplayTransform.visualBounds(display);
            Vec3 hit = bounds.contains(start) ? start : bounds.clip(start, end).orElse(null);
            if (hit != null) {
                double distance = start.distanceToSqr(hit);
                if (distance <= closestDistance) {
                    closestDistance = distance;
                    closest = display;
                }
            }
        }
        return closest;
    }

    private static void renderHud(GuiGraphics graphics, int width) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null
                || editorHand(minecraft) == null) {
            return;
        }
        Component mode = Component.translatable(editMode.translationKey);
        Component hint = Component.translatable("hud.examplemod.mode_hint", MODE_KEY.getTranslatedKeyMessage());
        graphics.drawString(minecraft.font, mode, width - minecraft.font.width(mode) - 8, 8, 0xFFFFFF, true);
        graphics.drawString(minecraft.font, hint, width - minecraft.font.width(hint) - 8, 19, 0xA0A0A0, true);
    }

    public enum EditMode {
        NUMERIC("hud.examplemod.numeric"),
        SCALE("hud.examplemod.scale"),
        ROTATION("hud.examplemod.rotation");

        private final String translationKey;

        EditMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(TYPE_MENU_KEY);
            event.register(MODE_KEY);
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("display_editor_mode",
                    (gui, graphics, partialTick, width, height) -> renderHud(graphics, width));
        }
    }
}
