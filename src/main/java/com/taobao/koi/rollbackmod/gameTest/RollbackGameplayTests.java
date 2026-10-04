package com.taobao.koi.rollbackmod.gameTest;

import com.mojang.authlib.GameProfile;
import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.item.InhalerItem;
import com.taobao.koi.rollbackmod.item.ModItems;
import com.taobao.koi.rollbackmod.rollback.BlockRollbackManager;
import com.taobao.koi.rollbackmod.rollback.DayCounterManager;
import com.taobao.koi.rollbackmod.rollback.RollbackManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 玩法级“游戏内”回归测试：运行在真实服务端 GameTest 环境中（真实 tick / 世界 / 实体 / 事件总线）。
 * <p>
 * 覆盖：化茧存档、蜕皮回溯（位置/血量/背包/耐久/消耗药芯）、致命伤害回溯、
 * 死亡事件回溯、方块改动回溯、天数换算。
 * <p>
 * 说明：每个用例是独立的 {@code @GameTest}，由 GameTest 框架在同一台真实服务端上串行执行；
 * 用例内部会清空“模拟玩家列表”并临时关闭多人同步，避免用例之间相互影响。
 */
public class RollbackGameplayTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollbackGameplayTests.class);

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> playersOf(MinecraftServer server) {
        try {
            java.lang.reflect.Field field = PlayerList.class.getDeclaredField("players");
            field.setAccessible(true);
            return (List<ServerPlayer>) field.get(server.getPlayerList());
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to access player list", exception);
        }
    }

    private static void clearPlayers(MinecraftServer server) {
        playersOf(server).clear();
    }

    private static ServerPlayer spawnMockPlayer(MinecraftServer server, ServerLevel level, String name) {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name));
        player.connection = new ServerGamePacketListenerImpl(server, connection, player);
        playersOf(server).add(player);
        return player;
    }

    private static ItemStack inhalerWith(ItemStack core) {
        ItemStack inhaler = new ItemStack(ModItems.INHALER.get());
        InhalerItem.insertCore(inhaler, core);
        return inhaler;
    }

    private static void giveMainHand(ServerPlayer player, ItemStack inhaler) {
        player.getInventory().setItem(0, inhaler);
        player.getInventory().selected = 0;
    }

    private static void assertNear(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 0.01D, message + " (expected " + expected + ", got " + actual + ")");
    }

    // 1. 化茧药芯：通过真实的“使用物品”流程立即创建存档点，并消耗药芯。
    @GameTest(template = "empty", templateNamespace = "rollbackmod", batch = "rb_1_cocoon")
    public static void cocoonCoreCreatesCheckpoint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        boolean prevSync = RollbackConfig.syncInventoryAndHealth;
        try {
            RollbackConfig.syncInventoryAndHealth = false;
            clearPlayers(server);
            ServerPlayer player = spawnMockPlayer(server, level, "cocoon-user");
            RollbackManager.deleteCheckpoint(server);
            helper.assertTrue(!RollbackManager.hasCheckpoint(server), "should start without a checkpoint");

            ItemStack inhaler = inhalerWith(new ItemStack(ModItems.COCOON_CORE.get()));
            giveMainHand(player, inhaler);
            inhaler.finishUsingItem(level, player);

            helper.assertTrue(RollbackManager.hasCheckpoint(server), "cocoon core should create a checkpoint");
            helper.assertTrue(InhalerItem.getCores(inhaler).isEmpty(), "cocoon core should be consumed after use");
            helper.succeed();
        } catch (RuntimeException exception) {
            LOGGER.error("cocoonCoreCreatesCheckpoint failed", exception);
            throw exception;
        } finally {
            RollbackConfig.syncInventoryAndHealth = prevSync;
            clearPlayers(server);
        }
    }

    // 2. 蜕皮药芯：长按吸入后回溯，位置/血量/背包恢复，吸入器耐久 -1，触发用的药芯被消耗。
    @GameTest(template = "empty", templateNamespace = "rollbackmod", batch = "rb_2_molting")
    public static void moltingCoreRollsBackAndConsumesCore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        boolean prevSync = RollbackConfig.syncInventoryAndHealth;
        boolean prevDestroy = RollbackConfig.destroyAllCoresOnRollback;
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos away = helper.absolutePos(new BlockPos(28, 8, 28));
        try {
            RollbackConfig.syncInventoryAndHealth = false;
            RollbackConfig.destroyAllCoresOnRollback = false;
            clearPlayers(server);
            ServerPlayer player = spawnMockPlayer(server, level, "molting-user");
            player.teleportTo(level, start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
            player.setHealth(12.0F);

            ItemStack inhaler = inhalerWith(new ItemStack(ModItems.MOLTING_CORE.get()));
            giveMainHand(player, inhaler);
            RollbackManager.createCheckpoint(server, "gameplay_molting");

            player.teleportTo(level, away.getX() + 0.5D, away.getY(), away.getZ() + 0.5D, 0.0F, 0.0F);
            player.setHealth(3.0F);
            player.getInventory().setItem(0, inhaler);

            inhaler.finishUsingItem(level, player);

            assertNear(helper, player.getX(), start.getX() + 0.5D, "x should be restored to the checkpoint");
            assertNear(helper, player.getZ(), start.getZ() + 0.5D, "z should be restored to the checkpoint");
            assertNear(helper, player.getHealth(), 12.0F, "health should be restored to the checkpoint");
            ItemStack restored = player.getInventory().getItem(0);
            helper.assertTrue(restored.getItem() == ModItems.INHALER.get(), "inhaler should be restored in slot 0");
            helper.assertTrue(InhalerItem.getCores(restored).isEmpty(), "triggering molting core should be consumed");
            helper.assertTrue(restored.getDamageValue() == 0, "inhaler should have infinite durability (no damage taken)");
            helper.succeed();
        } catch (RuntimeException exception) {
            LOGGER.error("moltingCoreRollsBackAndConsumesCore failed", exception);
            throw exception;
        } finally {
            RollbackConfig.syncInventoryAndHealth = prevSync;
            RollbackConfig.destroyAllCoresOnRollback = prevDestroy;
            clearPlayers(server);
        }
    }

    // 3. 致命伤害（>= 当前生命）触发回溯，而不是掉血/死亡。
    @GameTest(template = "empty", templateNamespace = "rollbackmod", batch = "rb_3_fatal")
    public static void lethalDamageRollsBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        boolean prevSync = RollbackConfig.syncInventoryAndHealth;
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos away = helper.absolutePos(new BlockPos(28, 8, 28));
        try {
            RollbackConfig.syncInventoryAndHealth = false;
            clearPlayers(server);
            ServerPlayer player = spawnMockPlayer(server, level, "fatal-user");
            player.teleportTo(level, start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
            player.setHealth(12.0F);
            RollbackManager.createCheckpoint(server, "gameplay_fatal");

            player.teleportTo(level, away.getX() + 0.5D, away.getY(), away.getZ() + 0.5D, 0.0F, 0.0F);
            player.setHealth(12.0F);
            // genericKill 绕过无敌帧/护甲，等价于 /kill 的致死伤害，能可靠走完伤害事件管线
            player.hurt(player.damageSources().genericKill(), 100.0F);

            assertNear(helper, player.getX(), start.getX() + 0.5D, "fatal damage should roll the player back");
            assertNear(helper, player.getHealth(), 12.0F, "health should be restored after fatal-damage rollback");
            helper.succeed();
        } catch (RuntimeException exception) {
            LOGGER.error("lethalDamageRollsBack failed", exception);
            throw exception;
        } finally {
            RollbackConfig.syncInventoryAndHealth = prevSync;
            clearPlayers(server);
        }
    }

    // 4. 死亡事件路径：死亡被拦截并回溯到存档点。
    @GameTest(template = "empty", templateNamespace = "rollbackmod", batch = "rb_4_death")
    public static void deathEventRollsBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        boolean prevSync = RollbackConfig.syncInventoryAndHealth;
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos away = helper.absolutePos(new BlockPos(28, 8, 28));
        try {
            RollbackConfig.syncInventoryAndHealth = false;
            clearPlayers(server);
            ServerPlayer player = spawnMockPlayer(server, level, "death-user");
            player.teleportTo(level, start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
            player.setHealth(14.0F);
            RollbackManager.createCheckpoint(server, "gameplay_death");

            player.teleportTo(level, away.getX() + 0.5D, away.getY(), away.getZ() + 0.5D, 0.0F, 0.0F);
            player.setHealth(0.0F);
            player.die(player.damageSources().genericKill());

            assertNear(helper, player.getX(), start.getX() + 0.5D, "death should roll the player back to the checkpoint");
            assertNear(helper, player.getHealth(), 14.0F, "health should be restored after death rollback");
            helper.succeed();
        } catch (RuntimeException exception) {
            LOGGER.error("deathEventRollsBack failed", exception);
            throw exception;
        } finally {
            RollbackConfig.syncInventoryAndHealth = prevSync;
            clearPlayers(server);
        }
    }

    // 5. 方块改动（破坏/放置/爆炸共用入口）在回溯后恢复到存档点状态。
    @GameTest(template = "empty", templateNamespace = "rollbackmod", batch = "rb_5_block")
    public static void blockChangeRestoredOnRollback(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        try {
            clearPlayers(server);
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            BlockState original = Blocks.STONE.defaultBlockState();
            level.setBlock(pos, original, 3);
            RollbackManager.createCheckpoint(server, "gameplay_block");

            BlockRollbackManager.rememberBlockBeforeChange(level, pos, original);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(pos).isAir(), "block should be removed before rollback");

            boolean rolledBack = RollbackManager.rollback(server, "gameplay_block");

            helper.assertTrue(rolledBack, "rollback should succeed");
            helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "block change should be restored");
            helper.succeed();
        } catch (RuntimeException exception) {
            LOGGER.error("blockChangeRestoredOnRollback failed", exception);
            throw exception;
        } finally {
            clearPlayers(server);
        }
    }

    // 6. 天数换算：屏幕天数由世界时间推导（第 (dayTime/24000)+1 天）。
    @GameTest(template = "empty", templateNamespace = "rollbackmod", batch = "rb_6_day")
    public static void dayCounterComputesFromWorldTime(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        long saved = overworld.getDayTime();
        try {
            overworld.setDayTime(24000L);
            helper.assertTrue(DayCounterManager.currentDay(overworld.getServer()) == 2L, "dayTime 24000 should be day 2");
            overworld.setDayTime(24000L * 5L + 100L);
            helper.assertTrue(DayCounterManager.currentDay(overworld.getServer()) == 6L, "dayTime day5+100 should be day 6");
            helper.succeed();
        } catch (RuntimeException exception) {
            LOGGER.error("dayCounterComputesFromWorldTime failed", exception);
            throw exception;
        } finally {
            overworld.setDayTime(saved);
        }
    }

    // 7. 两格方块（床）：破坏一侧会把另一侧一并记下，回溯后整张床完整恢复（不会掉成物品）。
    @GameTest(template = "empty", templateNamespace = "rollbackmod", batch = "rb_7_bed")
    public static void bedBreakRollsBackBothHalves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        try {
            clearPlayers(server);
            BlockPos foot = helper.absolutePos(new BlockPos(6, 1, 6));
            level.getChunk(foot.getX() >> 4, foot.getZ() >> 4);
            BlockState bedFoot = Blocks.RED_BED.defaultBlockState()
                    .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.NORTH);
            BlockPos head = foot.relative(BedBlock.getConnectedDirection(bedFoot));
            BlockState bedHead = bedFoot.setValue(BedBlock.PART, BedPart.HEAD);
            level.setBlock(foot, bedFoot, 3);
            level.setBlock(head, bedHead, 3);
            helper.assertTrue(level.getBlockEntity(head) != null, "bed half should have a block entity");
            RollbackManager.createCheckpoint(server, "gameplay_bed");

            // 模拟破坏床的一侧：记录该格 + 另一半，然后两格都变成空气
            BlockRollbackManager.rememberBlockAndConnected(level, foot, level.getBlockState(foot));
            level.setBlock(foot, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(head, Blocks.AIR.defaultBlockState(), 3);

            boolean rolledBack = RollbackManager.rollback(server, "gameplay_bed");
            helper.assertTrue(rolledBack, "rollback should succeed");
            helper.assertTrue(level.getBlockState(foot).is(Blocks.RED_BED), "bed foot should be restored");
            helper.assertTrue(level.getBlockState(head).is(Blocks.RED_BED), "bed head should be restored");
            helper.succeed();
        } catch (RuntimeException exception) {
            LOGGER.error("bedBreakRollsBackBothHalves failed", exception);
            throw exception;
        } finally {
            clearPlayers(server);
        }
    }

    // 8. “状态与方块实体不匹配”的坏记录不得中断整次回溯（旧版会崩在 BedBlockEntity 构造）。
    @GameTest(template = "empty", templateNamespace = "rollbackmod", batch = "rb_8_badbe")
    public static void mismatchedBlockEntityRecordDoesNotAbortRollback(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        try {
            clearPlayers(server);
            BlockPos head = helper.absolutePos(new BlockPos(6, 1, 7));
            level.getChunk(head.getX() >> 4, head.getZ() >> 4);
            BlockState bedHead = Blocks.RED_BED.defaultBlockState()
                    .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.NORTH);
            level.setBlock(head, bedHead, 3);
            helper.assertTrue(level.getBlockEntity(head) != null, "bed half should have a block entity");
            RollbackManager.createCheckpoint(server, "gameplay_badbe");

            // 旧实现会把“床的方块实体”配给“空气”这条记录，回溯时崩在 BedBlockEntity.<init>
            BlockRollbackManager.rememberBlockBeforeChange(level, head, Blocks.AIR.defaultBlockState());
            level.setBlock(head, Blocks.AIR.defaultBlockState(), 3);

            boolean rolledBack = RollbackManager.rollback(server, "gameplay_badbe");
            helper.assertTrue(rolledBack, "rollback must not abort on a mismatched block-entity record");
            helper.assertTrue(level.getBlockState(head).isAir(), "recorded state (air) should be restored");
            helper.succeed();
        } catch (RuntimeException exception) {
            LOGGER.error("mismatchedBlockEntityRecordDoesNotAbortRollback failed", exception);
            throw exception;
        } finally {
            clearPlayers(server);
        }
    }

    // 9. 非事件路径的方块改动（活塞/流体/作物等共用 setBlock）也应被记录并回溯（验证 setBlock 钩子）。
    @GameTest(template = "empty", templateNamespace = "rollbackmod", batch = "rb_9_setblock")
    public static void nonEventBlockChangeIsRolledBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        try {
            clearPlayers(server);
            BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            RollbackManager.createCheckpoint(server, "gameplay_setblock");

            // 直接改方块（不经过任何 Forge 事件），模拟活塞/流体/作物等路径
            level.setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK), "block should be changed");

            boolean rolledBack = RollbackManager.rollback(server, "gameplay_setblock");
            helper.assertTrue(rolledBack, "rollback should succeed");
            helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "non-event block change should be rolled back");
            helper.succeed();
        } catch (RuntimeException exception) {
            LOGGER.error("nonEventBlockChangeIsRolledBack failed", exception);
            throw exception;
        } finally {
            clearPlayers(server);
        }
    }

    // 10. 全量方块快照：即使改动完全没被“改动清单”记录，回溯也能按快照逐块覆盖还原。
    @GameTest(template = "empty", templateNamespace = "rollbackmod", batch = "rb_10_snapshot")
    public static void fullBlockSnapshotRestoresUntrackedChange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        try {
            clearPlayers(server);
            BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            RollbackManager.createCheckpoint(server, "gameplay_snapshot");

            level.setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK), "block should be changed");
            // 清空“改动清单”，模拟“这个改动完全没被记录”，只靠全量快照还原
            BlockRollbackManager.clearChangedBlocks(com.taobao.koi.rollbackmod.rollback.RollbackSavedData.get(server));

            boolean rolledBack = RollbackManager.rollback(server, "gameplay_snapshot");
            helper.assertTrue(rolledBack, "rollback should succeed");
            helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "full snapshot should restore the untracked change");
            helper.succeed();
        } catch (RuntimeException exception) {
            LOGGER.error("fullBlockSnapshotRestoresUntrackedChange failed", exception);
            throw exception;
        } finally {
            clearPlayers(server);
        }
    }
}
