package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.item.DisplayKind;
import com.example.examplemod.item.DisplaySelection;
import com.example.examplemod.util.DisplayTransform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ModNetwork {
    private static final String VERSION = "2";
    private static final String GROUP_DATA_KEY = "DisplaySelectionGroups";
    private static final int MAX_GROUP_MEMBERS = 4096;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);
    private static Consumer<CompoundTag> groupSyncHandler = tag -> {
    };

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
        CHANNEL.messageBuilder(ChangeGroup.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChangeGroup::encode)
                .decoder(ChangeGroup::decode)
                .consumerMainThread(ChangeGroup::handle)
                .add();
        CHANNEL.messageBuilder(SyncGroups.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncGroups::encode)
                .decoder(SyncGroups::decode)
                .consumerMainThread(SyncGroups::handle)
                .add();
        MinecraftForge.EVENT_BUS.addListener(ModNetwork::onPlayerLogin);
    }

    public static void sendUpdate(int entityId, DisplayTransform.Values values) {
        CHANNEL.sendToServer(new UpdateDisplay(entityId, values));
    }

    public static void sendSelection(InteractionHand hand, DisplaySelection selection) {
        CHANNEL.sendToServer(new SelectContent(hand, selection.kind(), selection.content()));
    }

    public static void sendGroupChange(int group, UUID entityUuid, boolean add) {
        CHANNEL.sendToServer(new ChangeGroup(group, entityUuid, add));
    }

    public static void setGroupSyncHandler(Consumer<CompoundTag> handler) {
        groupSyncHandler = handler;
    }

    private static void syncGroups(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncGroups(groupData(player).copy()));
    }

    private static CompoundTag groupData(Player player) {
        CompoundTag persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
        CompoundTag groups = persistent.getCompound(GROUP_DATA_KEY);
        persistent.put(GROUP_DATA_KEY, groups);
        return groups;
    }

    private static boolean holdsEditor(ServerPlayer player) {
        return player.getMainHandItem().is(ExampleMod.DISPLAY_EDITOR.get())
                || player.getOffhandItem().is(ExampleMod.DISPLAY_EDITOR.get());
    }

    private static boolean canReachDisplay(ServerPlayer player, Display display) {
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 end = start.add(player.getViewVector(1.0F).scale(player.getEntityReach()));
        return DisplayTransform.rayIntersection(display, start, end).isPresent();
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncGroups(player);
        }
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

    private record ChangeGroup(int group, UUID entityUuid, boolean add) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(group);
            buffer.writeUUID(entityUuid);
            buffer.writeBoolean(add);
        }

        private static ChangeGroup decode(FriendlyByteBuf buffer) {
            return new ChangeGroup(buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean());
        }

        private static void handle(ChangeGroup message, Supplier<NetworkEvent.Context> contextSupplier) {
            ServerPlayer player = contextSupplier.get().getSender();
            if (player == null || !holdsEditor(player)
                    || message.group < 0 || message.group >= ExampleMod.MAX_SELECTION_GROUPS) {
                return;
            }
            Entity entity = player.serverLevel().getEntity(message.entityUuid);
            if (!(entity instanceof Display display) || !canReachDisplay(player, display)) {
                return;
            }

            CompoundTag groups = groupData(player);
            String groupKey = Integer.toString(message.group);
            CompoundTag members = groups.getCompound(groupKey);
            String entityKey = message.entityUuid.toString();
            if (message.add) {
                if (members.size() >= MAX_GROUP_MEMBERS && !members.contains(entityKey)) {
                    return;
                }
                members.putBoolean(entityKey, true);
            } else {
                members.remove(entityKey);
            }
            if (members.isEmpty()) {
                groups.remove(groupKey);
            } else {
                groups.put(groupKey, members);
            }
            syncGroups(player);
        }
    }

    private record SyncGroups(CompoundTag groups) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeNbt(groups);
        }

        private static SyncGroups decode(FriendlyByteBuf buffer) {
            CompoundTag groups = buffer.readNbt();
            return new SyncGroups(groups == null ? new CompoundTag() : groups);
        }

        private static void handle(SyncGroups message, Supplier<NetworkEvent.Context> contextSupplier) {
            groupSyncHandler.accept(message.groups.copy());
        }
    }
}
