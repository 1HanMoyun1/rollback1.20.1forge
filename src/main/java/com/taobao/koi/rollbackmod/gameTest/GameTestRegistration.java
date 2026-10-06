package com.taobao.koi.rollbackmod.gameTest;

import com.taobao.koi.rollbackmod.RollbackMod;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 把 GameTest 注册放在 gameTest 包里：这样在 nongentarget 版本排除该包时，
 * 主类不会引用到不存在的类。
 */
@Mod.EventBusSubscriber(modid = RollbackMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GameTestRegistration {
    private GameTestRegistration() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(RollbackGameTests.class);
        event.register(RollbackGameplayTests.class);
    }
}
