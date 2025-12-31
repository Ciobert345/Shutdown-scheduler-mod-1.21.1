package com.ciobert.shutdown.scheduler;

import com.ciobert.shutdown.scheduler.screen.GroupEditScreen;
import com.ciobert.shutdown.scheduler.screen.ShutdownSchedulerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public class ShutdownSchedulerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Networking.ConfigSyncPayload.ID, (payload, context) -> {
            System.out.println("[ShutdownScheduler] RECEIVED CONFIG SYNC Payload: " + payload.json().length() + " chars");
            context.client().execute(() -> {
                Screen current = MinecraftClient.getInstance().currentScreen;
                if (current instanceof ShutdownSchedulerScreen s) {
                    s.refreshConfig(payload.json());
                } else if (current instanceof GroupEditScreen s) {
                    s.refreshConfig(payload.json());
                } else if (payload.showGui()) {
                    MinecraftClient.getInstance().setScreen(new ShutdownSchedulerScreen(payload.json()));
                }
            });
        });
    }
}
