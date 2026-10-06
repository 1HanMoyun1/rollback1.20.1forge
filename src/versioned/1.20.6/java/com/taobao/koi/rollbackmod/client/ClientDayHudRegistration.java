package com.taobao.koi.rollbackmod.client;

import com.taobao.koi.rollbackmod.RollbackMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ClientDayHudRegistration {
    private ClientDayHudRegistration() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientDayHudRegistration::onAddGuiOverlayLayers);
    }

    private static void onAddGuiOverlayLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(ForgeLayeredDraw.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(RollbackMod.MOD_ID, "day_hud"),
                (guiGraphics, partialTick) -> ClientDayHud.renderDayHud(guiGraphics));
        event.getLayeredDraw().addAbove(ForgeLayeredDraw.VANILLA_ROOT,
                ResourceLocation.fromNamespaceAndPath(RollbackMod.MOD_ID, "rollback_transition"),
                (guiGraphics, partialTick) -> ClientDayHud.renderTransition(guiGraphics));
    }
}