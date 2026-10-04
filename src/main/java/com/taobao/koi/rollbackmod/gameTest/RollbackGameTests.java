package com.taobao.koi.rollbackmod.gameTest;

import com.mojang.authlib.GameProfile;
import com.taobao.koi.rollbackmod.rollback.RollbackManager;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 回归测试：验证回溯会把玩家的位置、血量与背包恢复为存档点状态
 * （对应“回溯位置不变 / 背包不受影响”的 bug 修复）。
 * <p>
 * 说明：不调用 PlayerList.placeNewPlayer（Forge 会向无通道的假连接注入
 * 网络过滤器导致 NPE），改为手动挂一个空连接并把玩家加入玩家列表。
 */
public class RollbackGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollbackGameTests.class);

    @GameTest(template = "empty", templateNamespace = "rollbackmod")
    public static void rollbackRestoresPositionHealthAndInventory(GameTestHelper helper) {
        try {
            runTest(helper);
        } catch (RuntimeException exception) {
            LOGGER.error("RollbackGameTests failed", exception);
            throw exception;
        }
    }

    private static void runTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "test-mock-player"));
        player.connection = new ServerGamePacketListenerImpl(server, connection, player);
        try {
            java.lang.reflect.Field playersField = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            playersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<ServerPlayer> players = (java.util.List<ServerPlayer>) playersField.get(server.getPlayerList());
            players.add(player);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to add mock player to player list", exception);
        }

        // 存档点：位置 A、血量 10、快捷栏第一格有钻石
        player.teleportTo(level, 10.5D, 64.0D, 12.5D, 90.0F, 0.0F);
        player.setHealth(10.0F);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        RollbackManager.createCheckpoint(server, "gametest");

        // 存档之后：移动、回满血、清空背包
        player.teleportTo(level, 30.5D, 70.0D, 40.5D, 0.0F, 0.0F);
        player.setHealth(20.0F);
        player.getInventory().clearContent();

        boolean rolledBack = RollbackManager.rollback(server, "gametest");

        helper.assertTrue(rolledBack, "rollback should succeed");
        helper.assertTrue(
                Math.abs(player.getX() - 10.5D) < 0.01D
                        && Math.abs(player.getY() - 64.0D) < 0.01D
                        && Math.abs(player.getZ() - 12.5D) < 0.01D,
                "position should be restored to the checkpoint"
        );
        helper.assertTrue(Math.abs(player.getHealth() - 10.0F) < 0.01F, "health should be restored to 10");
        helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND), "inventory should be restored");
        helper.succeed();
    }

    /**
     * 回归测试：世界创建时的存档点里没有玩家，玩家后来加入时补拍快照进存档点；
     * 之后死亡回溯必须把玩家送回加入时的位置（对应“fabric 回溯不移动位置”的 bug）。
     */
    @GameTest(template = "empty", templateNamespace = "rollbackmod")
    public static void rollbackRestoresPlayerAddedAfterCheckpoint(GameTestHelper helper) {
        try {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();

            // 先创建存档点（此时还没有玩家）
            RollbackManager.createCheckpoint(server, "gametest_empty");

            // 玩家加入并补拍快照
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "test-mock-player"));
            player.connection = new ServerGamePacketListenerImpl(server, connection, player);
            try {
                java.lang.reflect.Field playersField = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
                playersField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<ServerPlayer> players = (java.util.List<ServerPlayer>) playersField.get(server.getPlayerList());
                players.add(player);
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to add mock player to player list", exception);
            }

            player.teleportTo(level, 5.5D, 64.0D, 7.5D, 0.0F, 0.0F);
            RollbackManager.addPlayerToCheckpoint(server, player);

            // 玩家离开加入点，死亡回溯应回到加入点
            player.teleportTo(level, 40.5D, 70.0D, 45.5D, 0.0F, 0.0F);

            boolean rolledBack = RollbackManager.rollback(server, "gametest_empty", null, 0, false);

            helper.assertTrue(rolledBack, "rollback should succeed");
            helper.assertTrue(
                    Math.abs(player.getX() - 5.5D) < 0.01D
                            && Math.abs(player.getY() - 64.0D) < 0.01D
                            && Math.abs(player.getZ() - 7.5D) < 0.01D,
                    "player position should be restored to the join snapshot"
            );
            helper.succeed();
        } catch (RuntimeException exception) {
            LOGGER.error("RollbackGameTests failed", exception);
            throw exception;
        }
    }
}

