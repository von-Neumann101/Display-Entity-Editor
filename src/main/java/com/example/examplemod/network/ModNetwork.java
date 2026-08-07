package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.item.DisplayKind;
import com.example.examplemod.item.DisplaySelection;
import com.example.examplemod.util.DisplayTransform;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class ModNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(UpdateDisplay.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateDisplay::encode)
                .decoder(UpdateDisplay::decode)
                .consumerMainThread(UpdateDisplay::handle)
                .add();
        CHANNEL.messageBuilder(SelectContent.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectContent::encode)
                .decoder(SelectContent::decode)
                .consumerMainThread(SelectContent::handle)
                .add();
    }

    public static void sendUpdate(int entityId, DisplayTransform.Values values) {
        CHANNEL.sendToServer(new UpdateDisplay(entityId, values));
    }

    public static void sendSelection(InteractionHand hand, DisplaySelection selection) {
        CHANNEL.sendToServer(new SelectContent(hand, selection.kind(), selection.content()));
    }

    private record UpdateDisplay(int entityId, DisplayTransform.Values values) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeInt(entityId);
            buffer.writeFloat(values.translationX());
            buffer.writeFloat(values.translationY());
            buffer.writeFloat(values.translationZ());
            buffer.writeFloat(values.rotationX());
            buffer.writeFloat(values.rotationY());
            buffer.writeFloat(values.rotationZ());
            buffer.writeFloat(values.scaleX());
            buffer.writeFloat(values.scaleY());
            buffer.writeFloat(values.scaleZ());
        }

        private static UpdateDisplay decode(FriendlyByteBuf buffer) {
            return new UpdateDisplay(buffer.readInt(), new DisplayTransform.Values(
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));
        }

        private static void handle(UpdateDisplay message, Supplier<NetworkEvent.Context> contextSupplier) {
            ServerPlayer player = contextSupplier.get().getSender();
            if (player == null || !holdsEditor(player) || !message.values.isValid()) {
                return;
            }

            Entity entity = player.serverLevel().getEntity(message.entityId);
            if (entity instanceof Display display && player.distanceToSqr(display) <= 64.0D) {
                DisplayTransform.apply(display, message.values);
            }
        }

        private static boolean holdsEditor(ServerPlayer player) {
            return player.getMainHandItem().is(ExampleMod.DISPLAY_EDITOR.get())
                    || player.getOffhandItem().is(ExampleMod.DISPLAY_EDITOR.get());
        }
    }

    private record SelectContent(InteractionHand hand, DisplayKind kind, String content) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeEnum(hand);
            buffer.writeEnum(kind);
            buffer.writeUtf(content, 128);
        }

        private static SelectContent decode(FriendlyByteBuf buffer) {
            return new SelectContent(buffer.readEnum(InteractionHand.class),
                    buffer.readEnum(DisplayKind.class), buffer.readUtf(128));
        }

        private static void handle(SelectContent message, Supplier<NetworkEvent.Context> contextSupplier) {
            ServerPlayer player = contextSupplier.get().getSender();
            if (player == null) {
                return;
            }
            ItemStack stack = player.getItemInHand(message.hand);
            DisplaySelection selection = new DisplaySelection(message.kind, message.content);
            if (stack.is(ExampleMod.DISPLAY_EDITOR.get()) && selection.isValid()) {
                DisplaySelection.set(stack, selection);
                DisplaySelection.set(player, selection);
                player.getInventory().setChanged();
            }
        }
    }
}
