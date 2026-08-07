package com.example.examplemod.client;

import com.example.examplemod.network.ModNetwork;
import com.example.examplemod.util.DisplayTransform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;

import java.util.Arrays;

public class DisplayEditorScreen extends Screen {
    private static final double MIN_SCALE = 0.01D;
    private static final long SEND_INTERVAL_NANOS = 50_000_000L;
    private static final String[] AXES = {"X", "Y", "Z"};

    private final int entityId;
    private final EditBox[] fields = new EditBox[9];
    private DisplayTransform.Values values;
    private Component status = Component.empty();
    private long lastSendNanos;
    private boolean pending;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private boolean narrowLayout;

    public DisplayEditorScreen(Display display) {
        this(display, DisplayTransform.read(display));
    }

    public DisplayEditorScreen(Display display, DisplayTransform.Values values) {
        super(Component.translatable("screen.examplemod.display_editor"));
        this.entityId = display.getId();
        this.values = values;
    }

    @Override
    protected void init() {
        Arrays.fill(fields, null);
        if (ClientEvents.editMode() == ClientEvents.EditMode.NUMERIC) {
            initNumeric();
        } else {
            initSliders();
        }
    }

    private void initNumeric() {
        narrowLayout = width < 480;
        setPanel(narrowLayout ? 150 : 270, narrowLayout ? 202 : 162);
        int labelsWidth = narrowLayout ? 8 : 62;
        int gap = 4;
        int fieldsX = panelX + labelsWidth;
        int fieldWidth = Math.max(36, (panelWidth - labelsWidth - 8 - gap * 2) / 3);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int index = row * 3 + column;
                EditBox field = new EditBox(font, fieldsX + column * (fieldWidth + gap),
                        panelY + (narrowLayout ? 51 + row * 43 : 45 + row * 25), fieldWidth, 20,
                        Component.translatable("screen.examplemod.value"));
                field.setMaxLength(16);
                field.setFilter(DisplayEditorScreen::isNumericInput);
                fields[index] = addRenderableWidget(field);
            }
        }
        setFieldValues(values);
        setInitialFocus(fields[0]);

        int gapWidth = 4;
        int buttonWidth = (panelWidth - 16 - gapWidth * 2) / 3;
        int buttonY = panelY + (narrowLayout ? 163 : 121);
        addRenderableWidget(Button.builder(Component.translatable("screen.examplemod.apply"), button -> applyNumeric())
                .bounds(panelX + 8, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.examplemod.reset"), button -> resetNumeric())
                .bounds(panelX + 8 + buttonWidth + gapWidth, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.examplemod.close"), button -> onClose())
                .bounds(panelX + 8 + (buttonWidth + gapWidth) * 2, buttonY, buttonWidth, 20).build());
    }

    private void initSliders() {
        narrowLayout = width < 480;
        setPanel(narrowLayout ? 150 : 210, 132);
        for (int axis = 0; axis < 3; axis++) {
            addRenderableWidget(new AxisSlider(panelX + 8, panelY + 55 + axis * 22,
                    panelWidth - 16, axis, normalized(axis)));
        }
    }

    private void setPanel(int preferredWidth, int preferredHeight) {
        panelWidth = Math.max(120, Math.min(preferredWidth, width - 16));
        panelHeight = Math.min(preferredHeight, height - 16);
        panelX = Math.max(8, width - panelWidth - 8);
        panelY = Math.max(8, height - panelHeight - 8);
    }

    private void applyNumeric() {
        DisplayTransform.Values parsed = readFields();
        if (parsed == null || !parsed.isValid()) {
            status = Component.translatable("screen.examplemod.invalid_short");
            return;
        }
        values = parsed;
        ModNetwork.sendUpdate(entityId, values);
        status = Component.translatable("screen.examplemod.applied");
    }

    private void resetNumeric() {
        values = DisplayTransform.DEFAULT;
        setFieldValues(values);
        ModNetwork.sendUpdate(entityId, values);
        status = Component.translatable("screen.examplemod.applied");
    }

    private DisplayTransform.Values readFields() {
        try {
            return new DisplayTransform.Values(
                    fieldValue(0), fieldValue(1), fieldValue(2),
                    fieldValue(3), fieldValue(4), fieldValue(5),
                    fieldValue(6), fieldValue(7), fieldValue(8));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private float fieldValue(int index) {
        return Float.parseFloat(fields[index].getValue());
    }

    private void setFieldValues(DisplayTransform.Values source) {
        float[] raw = {
                source.translationX(), source.translationY(), source.translationZ(),
                source.rotationX(), source.rotationY(), source.rotationZ(),
                source.scaleX(), source.scaleY(), source.scaleZ()
        };
        for (int index = 0; index < fields.length; index++) {
            fields[index].setValue(format(raw[index], 10000.0F));
        }
    }

    private double normalized(int axis) {
        double value = current(axis);
        if (ClientEvents.editMode() == ClientEvents.EditMode.SCALE) {
            value = Math.max(MIN_SCALE, value);
            return Mth.clamp(Math.log(value / MIN_SCALE)
                    / Math.log(DisplayTransform.MAX_SCALE / MIN_SCALE), 0.0D, 1.0D);
        }
        return Mth.clamp((value + DisplayTransform.MAX_ROTATION)
                / (DisplayTransform.MAX_ROTATION * 2.0D), 0.0D, 1.0D);
    }

    private float current(int axis) {
        if (ClientEvents.editMode() == ClientEvents.EditMode.SCALE) {
            return switch (axis) {
                case 0 -> values.scaleX();
                case 1 -> values.scaleY();
                default -> values.scaleZ();
            };
        }
        return switch (axis) {
            case 0 -> values.rotationX();
            case 1 -> values.rotationY();
            default -> values.rotationZ();
        };
    }

    private void setAxis(int axis, double sliderValue) {
        float value = ClientEvents.editMode() == ClientEvents.EditMode.SCALE
                ? (float) (MIN_SCALE * Math.pow(DisplayTransform.MAX_SCALE / MIN_SCALE, sliderValue))
                : (float) (-DisplayTransform.MAX_ROTATION
                + sliderValue * DisplayTransform.MAX_ROTATION * 2.0D);
        if (ClientEvents.editMode() == ClientEvents.EditMode.SCALE) {
            values = new DisplayTransform.Values(
                    values.translationX(), values.translationY(), values.translationZ(),
                    values.rotationX(), values.rotationY(), values.rotationZ(),
                    axis == 0 ? value : values.scaleX(),
                    axis == 1 ? value : values.scaleY(),
                    axis == 2 ? value : values.scaleZ());
        } else {
            values = new DisplayTransform.Values(
                    values.translationX(), values.translationY(), values.translationZ(),
                    axis == 0 ? value : values.rotationX(),
                    axis == 1 ? value : values.rotationY(),
                    axis == 2 ? value : values.rotationZ(),
                    values.scaleX(), values.scaleY(), values.scaleZ());
        }
        send(false);
    }

    private void send(boolean force) {
        pending = true;
        long now = System.nanoTime();
        if (force || now - lastSendNanos >= SEND_INTERVAL_NANOS) {
            ModNetwork.sendUpdate(entityId, values);
            lastSendNanos = now;
            pending = false;
        }
    }

    private void flush() {
        if (pending) {
            send(true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (hasShiftDown() && ClientEvents.editMode() != ClientEvents.EditMode.NUMERIC) {
            flush();
            ClientEvents.toggleSliderMode();
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        flush();
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.MODE_KEY.matches(keyCode, scanCode)) {
            flush();
            ClientEvents.toggleEditMode();
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        flush();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xA0000000);
        Component mode = Component.translatable(ClientEvents.editMode().translationKey());
        graphics.drawString(font, mode, panelX + 7, panelY + 6, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.examplemod.mode_hint",
                        ClientEvents.MODE_KEY.getTranslatedKeyMessage()),
                panelX + 7, panelY + 18, 0xA0A0A0, false);

        if (ClientEvents.editMode() == ClientEvents.EditMode.NUMERIC) {
            renderNumericLabels(graphics);
        } else {
            graphics.drawString(font, Component.translatable(narrowLayout
                            ? "screen.examplemod.slider_hint_short" : "screen.examplemod.view_hint"),
                    panelX + 7, panelY + 30, 0xA0A0A0, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderNumericLabels(GuiGraphics graphics) {
        if (narrowLayout) {
            int fieldWidth = (panelWidth - 24) / 3;
            for (int row = 0; row < 3; row++) {
                Component label = Component.translatable(switch (row) {
                    case 0 -> "screen.examplemod.position_short";
                    case 1 -> "screen.examplemod.rotation_short";
                    default -> "screen.examplemod.scale_short";
                });
                int labelY = panelY + 32 + row * 43;
                graphics.drawString(font, label, panelX + 7, labelY, 0xFFFFFF, false);
                for (int axis = 0; axis < 3; axis++) {
                    int x = panelX + 8 + axis * (fieldWidth + 4) + fieldWidth / 2
                            - font.width(AXES[axis]) / 2;
                    graphics.drawString(font, AXES[axis], x, labelY + 10, 0xA0A0A0, false);
                }
            }
            graphics.drawString(font, status, panelX + 8, panelY + 187, 0xFFFF55, false);
            return;
        }

        int fieldAreaX = panelX + 62;
        int available = panelWidth - 70;
        for (int axis = 0; axis < 3; axis++) {
            int x = fieldAreaX + axis * available / 3 + available / 6 - font.width(AXES[axis]) / 2;
            graphics.drawString(font, AXES[axis], x, panelY + 33, 0xA0A0A0, false);
        }
        graphics.drawString(font, Component.translatable("screen.examplemod.position_short"),
                panelX + 7, panelY + 51, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.examplemod.rotation_short"),
                panelX + 7, panelY + 76, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.examplemod.scale_short"),
                panelX + 7, panelY + 101, 0xFFFFFF, false);
        graphics.drawString(font, status, panelX + 8, panelY + 146, 0xFFFF55, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class AxisSlider extends AbstractSliderButton {
        private final int axis;

        private AxisSlider(int x, int y, int width, int axis, double value) {
            super(x, y, width, 20, Component.empty(), value);
            this.axis = axis;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(
                    ClientEvents.editMode() == ClientEvents.EditMode.SCALE
                            ? "screen.examplemod.scale_slider" : "screen.examplemod.rotation_slider",
                    AXES[axis], format(current(axis), 100.0F)));
        }

        @Override
        protected void applyValue() {
            setAxis(axis, value);
        }
    }

    private static String format(float value, float precision) {
        return Float.toString(Math.round(value * precision) / precision);
    }

    private static boolean isNumericInput(String value) {
        return value.chars().allMatch(character -> character == '-' || character == '.'
                || Character.isDigit(character));
    }
}
