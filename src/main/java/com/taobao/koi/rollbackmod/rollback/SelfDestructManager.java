package com.taobao.koi.rollbackmod.rollback;

import com.taobao.koi.rollbackmod.event.CommonEvents;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public final class SelfDestructManager {
    public enum Cause {
        FATE_EXHAUSTED("death.rollbackmod.fate_exhausted"),
        CHOSE_DESTRUCTION("death.rollbackmod.chose_destruction");

        private final String translationKey;

        Cause(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private SelfDestructManager() {
    }

    public static void destroy(ServerPlayer player, Cause cause) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        destroy(server, List.of(player), cause);
    }

    public static void destroyAll(MinecraftServer server, Cause cause) {
        destroy(server, List.copyOf(server.getPlayerList().getPlayers()), cause);
    }

    /**
     * 毁灭：以极限模式（hardcore）死亡。
     * <ol>
     * <li>摧毁存档点（这是主要目的）；</li>
     * <li>清空背包后真正杀死玩家（不触发回溯）；</li>
     * <li>复活时强制旁观者且无法修改游戏模式。</li>
     * </ol>
     */
    private static void destroy(MinecraftServer server, List<ServerPlayer> players, Cause cause) {
        if (players.isEmpty()) {
            return;
        }
        RollbackManager.deleteCheckpoint(server);
        RollbackSavedData data = RollbackSavedData.get(server);

        for (ServerPlayer player : players) {
            data.markSelfDestructed(player.getUUID());
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable(cause.translationKey, player.getDisplayName()),
                    false
            );
            player.getInventory().clearContent();
            player.getPersistentData().putBoolean(CommonEvents.SKIP_NEXT_DEATH_ROLLBACK_TAG, true);
            player.kill();
        }
        server.saveEverything(false, true, false);
    }

    public static boolean isLocked(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        RollbackSavedData data = RollbackSavedData.get(server);
        return data.isCountdownExpired() || data.isSelfDestructed(player.getUUID());
    }

    public static void enforce(ServerPlayer player) {
        if (!isLocked(player)) {
            return;
        }
        forceSpectator(player);
    }

    private static void forceSpectator(ServerPlayer player) {
        if (player.isDeadOrDying()) {
            return;
        }
        player.setHealth(Math.max(1.0F, player.getHealth()));
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }
        player.onUpdateAbilities();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }
}
