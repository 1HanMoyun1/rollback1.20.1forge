package com.taobao.koi.rollbackmod.rollback;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 回溯世界数据的“分帧加载”队列。
 * <p>
 * 实体与方块实体的恢复不再一次性同步完成，而是每 tick 处理一小批、必要时强制
 * 加载区块——既能避免大回溯瞬间卡顿，也能把存档点里已加载区块的方块实体完整写回
 * （此前区块卸载后 {@code getBlockState} 返回空气、方块实体被静默丢弃，导致回溯不完整）。
 */
public final class RollbackRestoreQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollbackRestoreQueue.class);
    private static final int ENTITIES_PER_TICK = 6;
    private static final int BLOCK_ENTITIES_PER_TICK = 48;

    private static final Deque<PendingEntity> ENTITIES = new ArrayDeque<>();
    private static final Deque<PendingBlockEntity> BLOCK_ENTITIES = new ArrayDeque<>();

    private RollbackRestoreQueue() {
    }

    public static void enqueue(ServerLevel level, ListTag entities, Map<String, CompoundTag> blockEntities) {
        for (Tag raw : entities) {
            ENTITIES.add(new PendingEntity(level, ((CompoundTag) raw).copy()));
        }
        for (Map.Entry<String, CompoundTag> entry : blockEntities.entrySet()) {
            BLOCK_ENTITIES.add(new PendingBlockEntity(level, entry.getKey(), entry.getValue().copy()));
        }
    }

    public static boolean isActive() {
        return !ENTITIES.isEmpty() || !BLOCK_ENTITIES.isEmpty();
    }

    public static void clear() {
        ENTITIES.clear();
        BLOCK_ENTITIES.clear();
    }

    public static void tick() {
        for (int i = 0; i < ENTITIES_PER_TICK && !ENTITIES.isEmpty(); i++) {
            PendingEntity pending = ENTITIES.poll();
            try {
                Entity restored = EntityType.loadEntityRecursive(pending.nbt, pending.level, Function.identity());
                if (restored != null) {
                    pending.level.tryAddFreshEntityWithPassengers(restored);
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid rollback entity snapshot in {}", pending.level.dimension().location(), exception);
            }
        }

        for (int i = 0; i < BLOCK_ENTITIES_PER_TICK && !BLOCK_ENTITIES.isEmpty(); i++) {
            PendingBlockEntity pending = BLOCK_ENTITIES.poll();
            try {
                BlockPos pos = BlockPos.of(Long.parseLong(pending.key));
                // 强制加载区块，确保方块实体能写回（区块未加载时会被静默丢弃）
                pending.level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                BlockState state = pending.level.getBlockState(pos);
                if (!state.hasBlockEntity()) {
                    // 该位置已不是方块实体方块（例如已被还原为空气），跳过，避免用错误方块类型创建方块实体
                    continue;
                }
                pending.level.removeBlockEntity(pos);
                BlockEntity blockEntity = BlockEntity.loadStatic(pos, state, pending.nbt, pending.level.registryAccess());
                if (blockEntity != null) {
                    pending.level.setBlockEntity(blockEntity);
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid rollback block entity snapshot in {}", pending.level.dimension().location(), exception);
            }
        }
    }

    private record PendingEntity(ServerLevel level, CompoundTag nbt) {
    }

    private record PendingBlockEntity(ServerLevel level, String key, CompoundTag nbt) {
    }
}
