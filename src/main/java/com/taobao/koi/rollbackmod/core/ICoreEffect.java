package com.taobao.koi.rollbackmod.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface ICoreEffect {
    CoreType type();

    CoreUseResult onUseInhaler(ServerPlayer player, ItemStack inhaler, ItemStack coreStack);
}
