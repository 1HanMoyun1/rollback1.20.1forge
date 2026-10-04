package com.taobao.koi.rollbackmod.effect;

import com.taobao.koi.rollbackmod.core.CoreType;
import com.taobao.koi.rollbackmod.core.CoreUseResult;
import com.taobao.koi.rollbackmod.core.ICoreEffect;
import com.taobao.koi.rollbackmod.rollback.RollbackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class MoltingCoreEffect implements ICoreEffect {
    @Override
    public CoreType type() {
        return CoreType.MOLTING;
    }

    @Override
    public CoreUseResult onUseInhaler(ServerPlayer player, ItemStack inhaler, ItemStack coreStack) {
        // 蜕皮回溯消耗 1 点吸入器耐久，并消耗吸入器里触发回溯的那枚药芯；
        // 两者都在回溯覆盖背包后重新扣除，不受存档覆盖影响
        boolean rolledBack = RollbackManager.rollback(player.getServer(), "molting_core", player, 1, true);
        return rolledBack
                ? CoreUseResult.success(false, Component.translatable("message.rollbackmod.rolled_back"))
                : CoreUseResult.fail(Component.translatable("message.rollbackmod.no_checkpoint"));
    }
}
