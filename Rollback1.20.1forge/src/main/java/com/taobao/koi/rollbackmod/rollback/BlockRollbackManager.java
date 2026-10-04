package com.taobao.koi.rollbackmod.rollback;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlockRollbackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockRollbackManager.class);
    private static boolean restoringBlocks;

    private BlockRollbackManager() {
    }

    public static boolean isRestoringBlocks() {
        return restoringBlocks;
    }

    public static void rememberBlockBeforeChange(ServerLevel level, BlockPos pos) {
        rememberBlockBeforeChange(level, pos, level.getBlockState(pos));
    }

    public static void rememberBlockBeforeChange(ServerLevel level, BlockPos pos, BlockState originalState) {
        if (restoringBlocks || !RollbackManager.hasCheckpoint(level.getServer())) {
            return;
        }
        RollbackSavedData.get(level.getServer())
                .rememberChangedBlock(RollbackSavedData.BlockSnapshotRecord.capture(level, pos, originalState));
    }

    /**
     * 记录该方块及其“另一半”（床 / 门 / 高花高草等两格方块）。
     * <p>破坏两格方块时只有其中一格会触发事件，另一半被连带移除却无人记录，
     * 回溯时只恢复一格会得到无效的床（掉落成物品）。这里把另一半一并记下。
     */
    public static void rememberBlockAndConnected(ServerLevel level, BlockPos pos, BlockState state) {
        rememberBlockBeforeChange(level, pos, state);
        BlockPos connected = connectedHalf(state, pos);
        if (connected != null) {
            rememberBlockBeforeChange(level, connected);
        }
    }

    private static BlockPos connectedHalf(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof BedBlock) {
            return pos.relative(BedBlock.getConnectedDirection(state));
        }
        if (state.getBlock() instanceof DoorBlock) {
            return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        if (state.getBlock() instanceof DoublePlantBlock) {
            return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        return null;
    }

    public static void restoreChangedBlocks(MinecraftServer server, RollbackSavedData data) {
        restoringBlocks = true;
        try {
            for (RollbackSavedData.BlockSnapshotRecord record : new ArrayList<>(data.getChangedBlocks())) {
                try {
                    record.restore(server);
                } catch (RuntimeException exception) {
                    // 单个方块恢复失败不能中断整次回溯，否则会出现“只回溯了一部分”
                    LOGGER.warn("Skipping failed block rollback at {}", record.pos(), exception);
                }
            }
            data.clearChangedBlocks();
        } finally {
            restoringBlocks = false;
        }
    }

    public static void clearChangedBlocks(RollbackSavedData data) {
        data.clearChangedBlocks();
    }

    /** 存档点创建时抓取全量方块快照（所有维度）。 */
    public static void captureFullSnapshot(MinecraftServer server) {
        BlockSnapshotStore.clear();
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                BlockSnapshotStore.capture(level);
            }
        }
    }

    /** 回溯时按全量方块快照逐块覆盖（同时抑制改动记录）。 */
    public static void restoreFullSnapshot(MinecraftServer server) {
        restoringBlocks = true;
        try {
            if (server != null) {
                for (ServerLevel level : server.getAllLevels()) {
                    BlockSnapshotStore.restore(level);
                }
            }
        } finally {
            restoringBlocks = false;
        }
    }

    /** 删除存档点时清空快照。 */
    public static void clearFullSnapshot() {
        BlockSnapshotStore.clear();
    }
}
