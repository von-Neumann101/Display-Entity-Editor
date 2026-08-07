package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.network.ModNetwork;
import com.example.examplemod.util.DisplayTransform;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping TYPE_MENU_KEY = new KeyMapping(
            "key.examplemod.type_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.examplemod");
    public static final KeyMapping MODE_KEY = new KeyMapping(
            "key.examplemod.mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.examplemod");
    public static final KeyMapping GROUP_MODE_KEY = new KeyMapping(
            "key.examplemod.group_mode", KeyConflictContext.IN_GAME, KeyModifier.ALT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, "key.categories.examplemod");
    public static final KeyMapping CLEAR_GROUP_KEY = new KeyMapping(
            "key.examplemod.clear_group", KeyConflictContext.IN_GAME, KeyModifier.ALT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.examplemod");

    private static int selectedEntityId = -1;
    private static EditMode editMode = EditMode.NUMERIC;
    private static EditMode viewEditMode = EditMode.SCALE;
    private static final Map<Integer, Set<UUID>> SELECTION_GROUPS = new HashMap<>();
    private static int currentGroup;
    private static FilterMode filterMode = FilterMode.EXCLUDE;

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
        while (GROUP_MODE_KEY.consumeClick()) {
            if (editorHand(minecraft) != null) {
                filterMode = filterMode == FilterMode.EXCLUDE ? FilterMode.ONLY : FilterMode.EXCLUDE;
                clearInvalidSelection();
            }
        }
        while (CLEAR_GROUP_KEY.consumeClick()) {
            if (editorHand(minecraft) != null && !currentMembers().isEmpty()) {
                SELECTION_GROUPS.remove(currentGroup);
                clearInvalidSelection();
                ModNetwork.sendClearGroup(currentGroup);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || editorHand(minecraft) == null
                || !Screen.hasShiftDown() || event.getScrollDelta() == 0.0D) {
            return;
        }
        int count = groupCount();
        currentGroup = Math.floorMod(currentGroup + (event.getScrollDelta() > 0.0D ? -1 : 1), count);
        clearInvalidSelection();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !minecraft.player.getItemInHand(event.getHand()).is(ExampleMod.DISPLAY_EDITOR.get())) {
            return;
        }

        if (!event.isAttack() && !event.isUseItem()) {
            return;
        }

        Display display = findDisplay(minecraft, ClientEvents::isSelectable);
        if (display == null) {
            if (event.isAttack() && findDisplay(minecraft, ignored -> true) != null) {
                cancelInteraction(event);
            }
            return;
        }

        if (event.isAttack()) {
            if (Screen.hasAltDown()) {
                changeGroup(display);
            } else {
                selectedEntityId = display.getId();
            }
        } else {
            selectedEntityId = display.getId();
            minecraft.setScreen(new DisplayEditorScreen(display));
        }
        cancelInteraction(event);
    }

    private static void cancelInteraction(InputEvent.InteractionKeyMappingTriggered event) {
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

    private static void loadGroups(CompoundTag data) {
        SELECTION_GROUPS.clear();
        for (String groupKey : data.getAllKeys()) {
            try {
                int group = Integer.parseInt(groupKey);
                if (group < 0 || group >= ExampleMod.MAX_SELECTION_GROUPS) {
                    continue;
                }
                Set<UUID> members = new HashSet<>();
                CompoundTag memberData = data.getCompound(groupKey);
                for (String entityKey : memberData.getAllKeys()) {
                    if (memberData.getBoolean(entityKey)) {
                        try {
                            members.add(UUID.fromString(entityKey));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                if (!members.isEmpty()) {
                    SELECTION_GROUPS.put(group, members);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        clearInvalidSelection();
    }

    private static int groupCount() {
        int count = ExampleMod.SELECTION_GROUP_COUNT.get();
        currentGroup = Math.min(currentGroup, count - 1);
        return count;
    }

    private static Set<UUID> currentMembers() {
        groupCount();
        return SELECTION_GROUPS.computeIfAbsent(currentGroup, ignored -> new HashSet<>());
    }

    private static boolean isSelectable(Display display) {
        boolean member = currentMembers().contains(display.getUUID());
        return filterMode == FilterMode.ONLY ? member : !member;
    }

    private static void changeGroup(Display display) {
        boolean add = filterMode == FilterMode.EXCLUDE;
        selectedEntityId = -1;
        ModNetwork.sendGroupChange(currentGroup, display.getUUID(), add);
    }

    private static void clearInvalidSelection() {
        Display selected = selectedDisplay();
        if (selected != null && !isSelectable(selected)) {
            selectedEntityId = -1;
        }
    }

    private static InteractionHand editorHand(Minecraft minecraft) {
        if (minecraft.player.getMainHandItem().is(ExampleMod.DISPLAY_EDITOR.get())) {
            return InteractionHand.MAIN_HAND;
        }
        return minecraft.player.getOffhandItem().is(ExampleMod.DISPLAY_EDITOR.get())
                ? InteractionHand.OFF_HAND : null;
    }

    private static Display findDisplay(Minecraft minecraft, Predicate<Display> filter) {
        Vec3 start = minecraft.player.getEyePosition(1.0F);
        double reach = minecraft.player.getEntityReach();
        Vec3 end = start.add(minecraft.player.getViewVector(1.0F).scale(reach));
        double closestDistance = reach * reach;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            closestDistance = Math.min(closestDistance, start.distanceToSqr(minecraft.hitResult.getLocation()));
        }

        Display closest = null;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof Display display) || !filter.test(display)) {
                continue;
            }
            Vec3 hit = DisplayTransform.rayIntersection(display, start, end).orElse(null);
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
        int count = groupCount();
        boolean compact = width < 640;
        Component status = Component.translatable(width < 420
                        ? "hud.examplemod.group_short" : "hud.examplemod.group",
                currentGroup + 1, count, currentMembers().size(),
                Component.translatable(filterMode.translationKey));
        Component hint = Component.translatable(compact
                        ? "hud.examplemod.group_hint_short" : "hud.examplemod.group_hint",
                GROUP_MODE_KEY.getTranslatedKeyMessage());
        Component action = Component.translatable(compact
                        ? (filterMode == FilterMode.EXCLUDE
                        ? "hud.examplemod.group_add_short" : "hud.examplemod.group_remove_short")
                        : (filterMode == FilterMode.EXCLUDE
                        ? "hud.examplemod.group_add" : "hud.examplemod.group_remove"),
                CLEAR_GROUP_KEY.getTranslatedKeyMessage());
        graphics.drawString(minecraft.font, status, 8, 8, 0xFFFFFF, true);
        graphics.drawString(minecraft.font, hint, 8, 19, 0xA0A0A0, true);
        graphics.drawString(minecraft.font, action, 8, 30, 0xA0A0A0, true);
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

    private enum FilterMode {
        EXCLUDE("hud.examplemod.exclude"),
        ONLY("hud.examplemod.only");

        private final String translationKey;

        FilterMode(String translationKey) {
            this.translationKey = translationKey;
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
            event.register(GROUP_MODE_KEY);
            event.register(CLEAR_GROUP_KEY);
            ModNetwork.setGroupSyncHandler(ClientEvents::loadGroups);
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("display_editor_mode",
                    (gui, graphics, partialTick, width, height) -> renderHud(graphics, width));
        }
    }
}
