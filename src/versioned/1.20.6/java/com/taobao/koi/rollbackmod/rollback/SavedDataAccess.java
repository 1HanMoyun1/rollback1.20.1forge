package com.taobao.koi.rollbackmod.rollback;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class SavedDataAccess {
    private SavedDataAccess() {
    }

    public static RollbackSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RollbackSavedData::new, RollbackSavedData::load, DataFixTypes.LEVEL),
                RollbackSavedData.DATA_NAME);
    }
}
