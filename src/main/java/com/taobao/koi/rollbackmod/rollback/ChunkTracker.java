package com.taobao.koi.rollbackmod.rollback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * 记录每个维度当前已加载的区块坐标。
 * <p>
 * 存档覆盖式回溯需要在创建存档点时枚举所有已加载区块里的方块实体
 * （模组机器等），而 1.20.1 没有公开的“遍历已加载区块”API，
 * 因此通过 {@code ChunkEvent.Load/Unload} 维护这份登记表。
 */
public final class ChunkTracker {
    private static final Map<ServerLevel, Set<ChunkPos>> LOADED = new HashMap<>();

    private ChunkTracker() {
    }

    public static void onChunkLoad(ServerLevel level, ChunkPos pos) {
        LOADED.computeIfAbsent(level, key -> new HashSet<>()).add(new ChunkPos(pos.x, pos.z));
    }

    public static void onChunkUnload(ServerLevel level, ChunkPos pos) {
        Set<ChunkPos> loaded = LOADED.get(level);
        if (loaded != null) {
            loaded.remove(pos);
        }
    }

    public static Set<ChunkPos> getLoadedPositions(ServerLevel level) {
        Set<ChunkPos> loaded = LOADED.get(level);
        return loaded == null ? Set.of() : new HashSet<>(loaded);
    }

    public static void clearAll() {
        LOADED.clear();
    }
}
