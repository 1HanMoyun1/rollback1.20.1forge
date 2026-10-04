package com.taobao.koi.rollbackmod.effect;

import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.core.CoreType;
import com.taobao.koi.rollbackmod.core.CoreUseResult;
import com.taobao.koi.rollbackmod.core.ICoreEffect;
import com.taobao.koi.rollbackmod.rollback.DayCounterManager;
import com.taobao.koi.rollbackmod.rollback.RollbackManager;
import com.taobao.koi.rollbackmod.rollback.RollbackSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 化茧：立即创建存档点（原“断线”）。 */
public class CocoonCoreEffect implements ICoreEffect {
    @Override
    public CoreType type() {
        return CoreType.COCOON;
    }

    @Override
    public CoreUseResult onUseInhaler(ServerPlayer player, ItemStack inhaler, ItemStack coreStack) {
        RollbackManager.createCheckpoint(player.getServer(), "cocoon_core");

        // 倒计时模式下，化茧存档额外延长一天存活时间
        if (RollbackConfig.countdownMode && RollbackConfig.cocoonExtendsCountdownDay) {
            RollbackSavedData data = RollbackSavedData.get(player.getServer());
            data.setCountdownStartDay(data.getCountdownStartDay() + 1L);
            DayCounterManager.syncToAll(player.getServer());
            return CoreUseResult.success(true, Component.translatable("message.rollbackmod.checkpoint_created_extended"));
        }
        return CoreUseResult.success(true, Component.translatable("message.rollbackmod.checkpoint_created"));
    }
}
