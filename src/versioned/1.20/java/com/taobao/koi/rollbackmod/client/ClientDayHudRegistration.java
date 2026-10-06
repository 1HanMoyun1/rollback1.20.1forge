package com.taobao.koi.rollbackmod.client;

import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ClientDayHudRegistration {
    private ClientDayHudRegistration() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientDayHudRegistration::onRegisterGuiOverlays);
    }

    private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "day_hud",
                (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> ClientDayHud.renderDayHud(guiGraphics));
        event.registerAboveAll("rollback_transition",
                (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> ClientDayHud.renderTransition(guiGraphics));
    }
}