package com.taobao.koi.rollbackmod.rollback;

import net.minecraft.server.MinecraftServer;

public final class SavedDataAccess {
    private SavedDataAccess() {
    }

    public static RollbackSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(RollbackSavedData::load, RollbackSavedData::new, RollbackSavedData.DATA_NAME);
    }
}