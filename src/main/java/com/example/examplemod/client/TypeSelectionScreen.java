package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.item.DisplayKind;
import com.example.examplemod.item.DisplaySelection;
import com.example.examplemod.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TypeSelectionScreen extends Screen {
    private static final int PAGE_SIZE = 5;

    private final InteractionHand hand;
    private final DisplaySelection current;
    private final List<Button> resultButtons = new ArrayList<>();
    private DisplayKind kind;
    private EditBox search;
    private List<Entry> results = List.of();
    private int page;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private Component status = Component.empty();

    public TypeSelectionScreen(InteractionHand hand) {
        super(Component.translatable("screen.examplemod.type_menu"));
        this.hand = hand;
        ItemStack stack = Minecraft.getInstance().player == null
                ? ItemStack.EMPTY : Minecraft.getInstance().player.getItemInHand(hand);
        this.current = DisplaySelection.get(stack);
        this.kind = current.kind();
    }

    @Override
    protected void init() {
        panelWidth = Math.max(250, Math.min(380, width - 16));
        panelX = Math.max(8, (width - panelWidth) / 2);
        panelY = Math.max(8, (height - 220) / 2);
        resultButtons.clear();

        int typeWidth = (panelWidth - 20) / 3;
        for (int index = 0; index < DisplayKind.values().length; index++) {
            DisplayKind option = DisplayKind.values()[index];
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.examplemod.type." + option.id()),
                            button -> chooseKind(option))
                    .bounds(panelX + 6 + index * (typeWidth + 4), panelY + 28, typeWidth, 20)
                    .build());
        }

        search = new EditBox(font, panelX + 6, panelY + 54, panelWidth - 12, 20,
                Component.translatable("screen.examplemod.content"));
        search.setMaxLength(128);
        search.setHint(Component.translatable(kind == DisplayKind.TEXT
                ? "screen.examplemod.text_hint" : "screen.examplemod.search_hint"));
        if (kind == DisplayKind.TEXT && current.kind() == DisplayKind.TEXT) {
            search.setValue(current.content());
        }
        search.setResponder(value -> {
            page = 0;
            refreshResults();
        });
        addRenderableWidget(search);

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int resultSlot = slot;
            Button button = Button.builder(Component.empty(), ignored -> selectResult(resultSlot))
                    .bounds(panelX + 6, panelY + 80 + slot * 22, panelWidth - 12, 20)
                    .build();
            resultButtons.add(addRenderableWidget(button));
        }
        refreshResults();
        setInitialFocus(search);
    }

    private void chooseKind(DisplayKind selectedKind) {
        kind = selectedKind;
        page = 0;
        rebuildWidgets();
    }

    private void refreshResults() {
        if (search == null) {
            return;
        }
        if (kind == DisplayKind.TEXT) {
            for (Button button : resultButtons) {
                button.visible = false;
            }
            Button button = resultButtons.get(0);
            button.visible = true;
            button.setMessage(Component.translatable("screen.examplemod.select_text"));
            return;
        }

        String needle = search.getValue().toLowerCase(Locale.ROOT);
        results = registryEntries().stream()
                .filter(entry -> needle.isBlank()
                        || entry.id.toString().contains(needle)
                        || entry.name.getString().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(entry -> entry.id.toString()))
                .toList();
        int maxPage = Math.max(0, (results.size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(maxPage, page));

        for (int slot = 0; slot < resultButtons.size(); slot++) {
            int resultIndex = page * PAGE_SIZE + slot;
            Button button = resultButtons.get(slot);
            button.visible = resultIndex < results.size();
            if (button.visible) {
                Entry entry = results.get(resultIndex);
                button.setMessage(Component.translatable("screen.examplemod.selection_entry",
                        Component.translatable("screen.examplemod.type." + kind.id()), entry.name));
            }
        }
    }

    private List<Entry> registryEntries() {
        List<Entry> entries = new ArrayList<>();
        if (kind == DisplayKind.BLOCK) {
            for (Block block : ForgeRegistries.BLOCKS.getValues()) {
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                if (id != null && block != Blocks.AIR) {
                    entries.add(new Entry(id, block.getName()));
                }
            }
        } else {
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id != null && item != Items.AIR) {
                    entries.add(new Entry(id, item.getDescription()));
                }
            }
        }
        return entries;
    }

    private void selectResult(int slot) {
        if (kind == DisplayKind.TEXT) {
            select(new DisplaySelection(kind, search.getValue()));
            return;
        }
        int index = page * PAGE_SIZE + slot;
        if (index < results.size()) {
            select(new DisplaySelection(kind, results.get(index).id.toString()));
        }
    }

    private void select(DisplaySelection selection) {
        if (minecraft.player == null || !selection.isValid()) {
            status = Component.translatable("screen.examplemod.invalid_content");
            return;
        }
        ItemStack stack = minecraft.player.getItemInHand(hand);
        if (stack.is(ExampleMod.DISPLAY_EDITOR.get())) {
            DisplaySelection.set(stack, selection);
            ModNetwork.sendSelection(hand, selection);
            onClose();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (kind != DisplayKind.TEXT && !results.isEmpty()) {
            page += delta > 0 ? -1 : 1;
            refreshResults();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (kind == DisplayKind.TEXT && keyCode == 257) {
            select(new DisplaySelection(kind, search.getValue()));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, panelY + 8, 0xFFFFFF);
        Component currentText = Component.translatable("screen.examplemod.current_selection",
                Component.translatable("screen.examplemod.type." + current.kind().id()), current.content());
        graphics.drawCenteredString(font, currentText, width / 2, panelY + 18, 0xA0A0A0);
        if (kind != DisplayKind.TEXT) {
            Component pageText = Component.translatable("screen.examplemod.page",
                    results.isEmpty() ? 0 : page + 1,
                    results.isEmpty() ? 0 : (results.size() - 1) / PAGE_SIZE + 1);
            graphics.drawCenteredString(font, pageText, width / 2, panelY + 194, 0xA0A0A0);
        }
        graphics.drawCenteredString(font, status, width / 2, panelY + 206, 0xFF5555);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Entry(ResourceLocation id, Component name) {
    }

}
