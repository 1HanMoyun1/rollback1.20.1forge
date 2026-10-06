package com.taobao.koi.rollbackmod;

import com.taobao.koi.rollbackmod.config.ClothConfigScreen;
import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.core.CoreEffectRegistry;
import com.taobao.koi.rollbackmod.event.CommonEvents;
import com.taobao.koi.rollbackmod.item.ModCreativeTabs;
import com.taobao.koi.rollbackmod.item.ModItems;
import com.taobao.koi.rollbackmod.network.ModNetworking;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod(RollbackMod.MOD_ID)
public class RollbackMod {
    public static final String MOD_ID = "rollbackmod";

    public RollbackMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        RollbackConfig.load(FMLPaths.CONFIGDIR.get().resolve("rollbackmod.json"));

        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);

        modBus.addListener(this::commonSetup);

        CoreEffectRegistry.bootstrap();
        MinecraftForge.EVENT_BUS.register(CommonEvents.class);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientBootstrap::register);

        // Cloth Config 设置界面（可选依赖，客户端打开模组配置时使用）。
        // 未安装 cloth_config 时返回父界面，避免加载 ClothConfigScreen 触发 NoClassDefFoundError。
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) ->
                        net.minecraftforge.fml.ModList.get().isLoaded("cloth_config")
                                ? ClothConfigScreen.create(parent)
                                : parent)
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetworking::register);
    }

    private static final class ClientBootstrap {
        private static void register() {
            com.taobao.koi.rollbackmod.client.ClientDayHud.register(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
