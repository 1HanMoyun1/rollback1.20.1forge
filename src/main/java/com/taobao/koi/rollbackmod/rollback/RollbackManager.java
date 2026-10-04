package com.taobao.koi.rollbackmod.rollback;

import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.item.InhalerItem;
import com.taobao.koi.rollbackmod.item.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class RollbackManager {
    private static boolean rollingBack;
    private static boolean rollbackJustHappened;

    private RollbackManager() {
    }

    public static boolean isRollingBack() {
        return rollingBack;
    }

    /** 本次伤害处理中是否已经发生过回溯（用于多人共享伤害时打断循环，避免同一 tick 内多次回溯）。 */
    public static boolean consumeRollbackJustHappened() {
        boolean value = rollbackJustHappened;
        rollbackJustHappened = false;
        return value;
    }

    public static boolean isRollbackJustHappened() {
        return rollbackJustHappened;
    }

    public static void clearRollbackJustHappened() {
        rollbackJustHappened = false;
    }

    public static boolean hasCheckpoint(MinecraftServer server) {
        return RollbackSavedData.get(server).hasCheckpoint();
    }

    public static void deleteCheckpoint(MinecraftServer server) {
        if (server == null) {
            return;
        }
        RollbackSavedData data = RollbackSavedData.get(server);
        data.clearCheckpoint();
        data.setInitialCheckpointCreated(true);
        BlockRollbackManager.clearChangedBlocks(data);
        RollbackRestoreQueue.clear();
        BlockRollbackManager.clearFullSnapshot();
        DayCounterManager.syncToAll(server);
        server.saveEverything(false, true, false);
    }

    /** 世界创建/首次加载时存档一次；此后不再自动存档，存档全靠道具。 */
    public static void ensureInitialCheckpoint(MinecraftServer server) {
        RollbackSavedData data = RollbackSavedData.get(server);
        if (!data.isInitialCheckpointCreated()) {
            createCheckpoint(server, "initial_world_load");
            data.setInitialCheckpointCreated(true);
        }
    }

    public static void createCheckpoint(MinecraftServer server, String reason) {
        if (server == null) {
            return;
        }
        RollbackSavedData data = RollbackSavedData.get(server);
        data.setCheckpoint(RollbackSavedData.Checkpoint.capture(server));
        data.clearSelfDestructedPlayers();
        BlockRollbackManager.clearChangedBlocks(data);
        RollbackRestoreQueue.clear();
        BlockRollbackManager.captureFullSnapshot(server);
        DayCounterManager.onCheckpointCreated(server, data);
    }

    public static boolean rollback(MinecraftServer server, String reason) {
        return rollback(server, reason, null, 0, false);
    }

    /**
     * 全量“存档覆盖”式回溯。
     *
     * @param durabilityPlayer      触发回溯的玩家（用于回溯后重新扣除吸入器耐久）
     * @param durabilityCost        吸入器耐久消耗（死亡回溯为 0，蜕皮药芯为 1）
     * @param consumeTriggeredCore  回溯覆盖背包后，是否消耗触发回溯的药芯（蜕皮）
     */
    public static boolean rollback(MinecraftServer server, String reason, ServerPlayer durabilityPlayer, int durabilityCost,
                                   boolean consumeTriggeredCore) {
        if (server == null || rollingBack) {
            return false;
        }
        RollbackSavedData data = RollbackSavedData.get(server);
        RollbackSavedData.Checkpoint checkpoint = data.getCheckpoint();
        if (checkpoint == null) {
            return false;
        }

        rollingBack = true;
        try {
            // 世界状态（时间/天气）与实体清理同步执行，实体/方块实体的恢复分帧加载
            RollbackRestoreQueue.clear();
            for (var entry : checkpoint.levels().entrySet()) {
                RollbackSavedData.resolveLevel(server, entry.getKey())
                        .ifPresent(level -> {
                            entry.getValue().restoreWorldState(level);
                            RollbackRestoreQueue.enqueue(level, entry.getValue().entities(), entry.getValue().blockEntities());
                        });
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                checkpoint.getSnapshot(player.getUUID())
                        .orElseGet(() -> RollbackSavedData.PlayerSnapshot.capture(player))
                        .restore(player, server);
            }

            BlockRollbackManager.restoreFullSnapshot(server);
            BlockRollbackManager.restoreChangedBlocks(server, data);

            if (RollbackConfig.destroyAllCoresOnRollback) {
                destroyCoreItems(server);
            }

            // 吸入器耐久与触发药芯不受存档覆盖影响：恢复背包后重新扣除
            if (durabilityPlayer != null && !durabilityPlayer.getAbilities().instabuild) {
                if (durabilityCost > 0) {
                    applyInhalerDurabilityCost(durabilityPlayer, durabilityCost);
                }
                if (consumeTriggeredCore) {
                    consumeTriggeredCore(durabilityPlayer);
                }
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.displayClientMessage(Component.translatable("message.rollbackmod.rolled_back"), true);
            }
            DayCounterManager.onRollbackFinished(server, data);
            rollbackJustHappened = true;
            return true;
        } finally {
            rollingBack = false;
            data.setDirty();
        }
    }

    /** 玩家加入时补拍快照进存档点（世界创建时存档点里还没有玩家）。 */
    public static void addPlayerToCheckpoint(MinecraftServer server, ServerPlayer player) {
        RollbackSavedData data = RollbackSavedData.get(server);
        RollbackSavedData.Checkpoint checkpoint = data.getCheckpoint();
        if (checkpoint == null || checkpoint.players().containsKey(player.getUUID())) {
            return;
        }
        checkpoint.players().put(player.getUUID(), RollbackSavedData.PlayerSnapshot.capture(player));
        data.setDirty();
    }

    private static void applyInhalerDurabilityCost(ServerPlayer player, int cost) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof InhalerItem) {
            mainHand.hurtAndBreak(cost, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            return;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof InhalerItem) {
                stack.hurtAndBreak(cost, player, p -> stack.setCount(0));
                return;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof InhalerItem) {
            offhand.hurtAndBreak(cost, player, p -> p.broadcastBreakEvent(InteractionHand.OFF_HAND));
        }
    }

    /** 回溯恢复背包后，消耗触发回溯的那枚药芯（从吸入器里取出）。 */
    private static void consumeTriggeredCore(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof InhalerItem && !InhalerItem.takeFirstCore(mainHand).isEmpty()) {
            return;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof InhalerItem && !InhalerItem.takeFirstCore(stack).isEmpty()) {
                return;
            }
        }
    }

    private static void destroyCoreItems(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            removeCores(player.getInventory().items);
            removeCores(player.getInventory().armor);
            removeCores(player.getInventory().offhand);
            // 吸入器里的药芯一并清除
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof InhalerItem) {
                    InhalerItem.clearCores(stack);
                }
            }
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        }
    }

    private static void removeCores(NonNullList<ItemStack> items) {
        for (int i = 0; i < items.size(); i++) {
            if (ModItems.isCore(items.get(i))) {
                items.set(i, ItemStack.EMPTY);
            }
        }
    }
}
