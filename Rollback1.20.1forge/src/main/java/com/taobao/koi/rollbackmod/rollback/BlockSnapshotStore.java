package com.taobao.koi.rollbackmod.rollback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * 内存中的“全量方块快照”。
 * <p>
 * 存档点创建时，把每个维度**已加载区块**的方块状态整体拷一份（按 section 的紧凑调色板存储）；
 * 回溯时逐块 diff，只把与快照不同的方块写回。这样任何方块改动——包括活塞推动、沙砾下落、
 * 火焰蔓延、树叶凋零、作物生长、流体流动等**不经过事件**的路径——都会被还原，真正“覆盖存档”。
 * <p>
 * 只存内存、不写盘、不动 save 文件；新存档点会替换旧的快照，删除存档点时清空。
 */
public final class BlockSnapshotStore {
    /** 安全上限，避免极端情况下占用过多内存。 */
    private static final int MAX_CHUNKS = 8192;
    /** 只更新客户端、不触发邻接更新/形状更新/掉落，避免连锁反应。 */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final Map<ResourceKey<Level>, Map<Long, List<PalettedContainer<BlockState>>>> SNAPSHOTS = new HashMap<>();

    private BlockSnapshotStore() {
    }

    public static void capture(ServerLevel level) {
        Map<Long, List<PalettedContainer<BlockState>>> chunks = new HashMap<>();
        int count = 0;
        for (ChunkPos pos : ChunkTracker.getLoadedPositions(level)) {
            if (count >= MAX_CHUNKS) {
                break;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                continue;
            }
            LevelChunkSection[] sections = chunk.getSections();
            List<PalettedContainer<BlockState>> copies = new ArrayList<>(sections.length);
            for (LevelChunkSection section : sections) {
                copies.add(section.getStates().copy());
            }
            chunks.put(pos.toLong(), copies);
            count++;
        }
        SNAPSHOTS.put(level.dimension(), chunks);
    }

    public static void restore(ServerLevel level) {
        Map<Long, List<PalettedContainer<BlockState>>> chunks = SNAPSHOTS.get(level.dimension());
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, List<PalettedContainer<BlockState>>> entry : chunks.entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            LevelChunkSection[] sections = chunk.getSections();
            List<PalettedContainer<BlockState>> copies = entry.getValue();
            int minSection = chunk.getMinSection();
            int count = Math.min(sections.length, copies.size());
            for (int index = 0; index < count; index++) {
                LevelChunkSection section = sections[index];
                PalettedContainer<BlockState> snap = copies.get(index);
                // 两边都是纯空气 section 时直接跳过（绝大多数 section 都属于这种情况，省时间）
                if (section.hasOnlyAir() && !snap.maybeHas(state -> !state.isAir())) {
                    continue;
                }
                int baseY = (minSection + index) << 4;
                int baseX = chunkPos.getMinBlockX();
                int baseZ = chunkPos.getMinBlockZ();
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState want = snap.get(x, y, z);
                            if (section.getStates().get(x, y, z) != want) {
                                level.setBlock(new BlockPos(baseX + x, baseY + y, baseZ + z), want, FLAGS);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void clear() {
        SNAPSHOTS.clear();
    }
}
