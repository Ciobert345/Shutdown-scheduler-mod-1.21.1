package com.ciobert.shutdown.scheduler;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class Networking {

    public static final Identifier CONFIG_SYNC_ID = Identifier.of(ShutdownScheduler.MOD_ID, "config_sync");
    public static final Identifier ACTION_ID = Identifier.of(ShutdownScheduler.MOD_ID, "action");

    public record ConfigSyncPayload(String json, boolean showGui) implements CustomPayload {
        public static final Id<ConfigSyncPayload> ID = new Id<>(CONFIG_SYNC_ID);
        public static final PacketCodec<PacketByteBuf, ConfigSyncPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.string(1048576), ConfigSyncPayload::json,
                PacketCodecs.BOOL, ConfigSyncPayload::showGui,
                ConfigSyncPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ActionPayload(String action, String data) implements CustomPayload {
        public static final Id<ActionPayload> ID = new Id<>(ACTION_ID);
        public static final PacketCodec<PacketByteBuf, ActionPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, ActionPayload::action,
                PacketCodecs.STRING, ActionPayload::data,
                ActionPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
    }
}
