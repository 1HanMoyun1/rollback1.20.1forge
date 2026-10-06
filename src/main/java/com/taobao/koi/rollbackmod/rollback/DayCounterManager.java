package com.taobao.koi.rollbackmod.rollback;

import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.network.DayInfoPacket;
import com.taobao.koi.rollbackmod.network.ModNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class DayCounterManager {
    private static final long TICKS_PER_DAY = 24000L;

    private DayCounterManager() {
    }

    public static long currentDay(MinecraftServer server) {
        return Math.max(1L, Math.floorDiv(server.overworld().getDayTime(), TICKS_PER_DAY) + 1L);
    }

    public static void ensureInitialized(MinecraftServer server, RollbackSavedData data) {
        if (data.getCountdownStartDay() <= 0L) {
            data.setCountdownStartDay(currentDay(server));
        }
        if (data.getLastAnnouncedDay() <= 0L) {
            data.setLastAnnouncedDay(currentDay(server));
        }
    }

    public static void onCheckpointCreated(MinecraftServer server, RollbackSavedData data) {
        long day = currentDay(server);
        data.setCountdownStartDay(day);
        data.setLastAnnouncedDay(day);
        data.setCountdownExpired(false);
        syncToAll(server);
    }

    private static void send(MinecraftServer server, DayInfoPacket packet) {
        ModNetworking.sendToAll(server, packet);
    }

    /**
     * 回溯完成：播放滚动特效。
     * <p>
     * 同一天不播放（按反馈）；天数变化时两端数字由服务端算好（带方向语义），
     * 客户端按“大数自上而下、小数自下而上”滚动。
     */
    public static void onRollbackFinished(MinecraftServer server, RollbackSavedData data) {
        long day = currentDay(server);
        int newNumber = displayNumber(day, data);
        int previousDay = safeInt(data.getLastAnnouncedDay());
        data.setLastAnnouncedDay(day);
        data.setCountdownExpired(false);
        int oldNumber = displayNumber(previousDay, data);
        if (oldNumber != newNumber) {
            send(server, buildPacket(server, true, oldNumber, newNumber));
            return;
        }
        syncToAll(server);
    }

    public static void tick(MinecraftServer server) {
        RollbackSavedData data = RollbackSavedData.get(server);
        ensureInitialized(server, data);

        if (server.overworld().getGameTime() % 20L == 0L) {
            long day = currentDay(server);
            int previousNumber = safeInt(RollbackConfig.countdownMode
                    ? remainingDays(data.getLastAnnouncedDay(), data)
                    : data.getLastAnnouncedDay());
            if (day > data.getLastAnnouncedDay()) {
                data.setLastAnnouncedDay(day);
                // 任何跨天（自然流逝 / 睡觉）都播放黑屏时间特效
                int newNumber = displayNumber(day, data);
                if (previousNumber != newNumber) {
                    send(server, buildPacket(server, true, previousNumber, newNumber));
                } else {
                    syncToAll(server);
                }
            } else {
                syncToAll(server);
            }
            checkCountdownExpiry(server, data);
        }
    }

    public static void syncTo(ServerPlayer player) {
        ModNetworking.sendToPlayer(player, buildPacket(player.getServer(), false, -1, -1));
    }

    public static void syncToAll(MinecraftServer server) {
        send(server, buildPacket(server, false, -1, -1));
    }

    private static DayInfoPacket buildPacket(MinecraftServer server, boolean playAnimation, int fromNumber, int toNumber) {
        RollbackSavedData data = RollbackSavedData.get(server);
        ensureInitialized(server, data);
        long day = currentDay(server);
        int remainingDays = remainingDays(day, data);
        return new DayInfoPacket(
                RollbackConfig.showDayHud,
                RollbackConfig.countdownMode,
                safeInt(day),
                remainingDays,
                playAnimation,
                fromNumber,
                toNumber
        );
    }

    /** 屏幕上显示的数字：普通模式是当前天数，倒计时模式是剩余天数。 */
    private static int displayNumber(long day, RollbackSavedData data) {
        long elapsed = Math.max(0L, day - data.getCountdownStartDay());
        long remaining = Math.max(0L, RollbackConfig.countdownDays - elapsed);
        return safeInt(RollbackConfig.countdownMode ? remaining : day);
    }

    private static int remainingDays(long day, RollbackSavedData data) {
        long elapsed = Math.max(0L, day - data.getCountdownStartDay());
        long remaining = Math.max(0L, RollbackConfig.countdownDays - elapsed);
        return safeInt(remaining);
    }

    private static void checkCountdownExpiry(MinecraftServer server, RollbackSavedData data) {
        if (!RollbackConfig.countdownMode || data.isCountdownExpired()) {
            return;
        }
        if (remainingDays(currentDay(server), data) > 0) {
            return;
        }
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(Component.translatable("message.rollbackmod.countdown_expired"), true);
        }
        data.setCountdownExpired(true);
        SelfDestructManager.destroyAll(server, SelfDestructManager.Cause.FATE_EXHAUSTED);
    }

    private static int safeInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }
}
