package com.taobao.koi.rollbackmod.core;

import net.minecraft.network.chat.Component;

public record CoreUseResult(boolean success, boolean consumeCore, Component message) {
    public static CoreUseResult success(boolean consumeCore, Component message) {
        return new CoreUseResult(true, consumeCore, message);
    }

    public static CoreUseResult fail(Component message) {
        return new CoreUseResult(false, false, message);
    }
}
