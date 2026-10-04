package com.taobao.koi.rollbackmod.effect;

import com.taobao.koi.rollbackmod.core.CoreType;
import com.taobao.koi.rollbackmod.core.CoreUseResult;
import com.taobao.koi.rollbackmod.core.ICoreEffect;
import com.taobao.koi.rollbackmod.rollback.SelfDestructManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class TowerCoreEffect implements ICoreEffect {
    @Override
    public CoreType type() {
        return CoreType.TOWER;
    }

    @Override
    public CoreUseResult onUseInhaler(ServerPlayer player, ItemStack inhaler, ItemStack coreStack) {
        SelfDestructManager.destroy(player, SelfDestructManager.Cause.CHOSE_DESTRUCTION);
        return CoreUseResult.success(true, Component.translatable("message.rollbackmod.core_triggered"));
    }
}
