package com.taobao.koi.rollbackmod.rollback;

import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.event.CommonEvents;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * 多人“时间悖论”防护：所有在线玩家的生命值与背包被统一。
 * <ul>
 * <li>任一玩家受到伤害时，伤害被重定向给全部玩家——同一次致命攻击会让所有人同时死亡，
 *     从而只触发一次回溯；</li>
 * <li>周期性把“主玩家”（UUID 最小的在线玩家）的生命、饱食度与背包覆盖给其他玩家；</li>
 * <li>若检测到 dontgethurt 模组，重定向的伤害经由其 LivingDamageEvent 接管，
 *     自动转化为部位损伤，无需额外处理。</li>
 * </ul>
 */
public final class HiveManager {
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static boolean redistributing;
    private static int syncCounter;

    private HiveManager() {
    }

    /** 把对某一玩家的伤害重定向给全体在线玩家。返回 true 表示事件已被接管。 */
    public static boolean shareDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return false;
        }
        MinecraftServer server = victim.getServer();
        if (server == null || redistributing) {
            return false;
        }
        if (!RollbackConfig.syncInventoryAndHealth) {
            return false;
        }
        // 创造模式玩家既不产生共享伤害、也不受共享伤害影响
        if (victim.getAbilities().instabuild) {
            return false;
        }
        // 高塔/倒计时的自我毁灭死亡不参与共享
        if (victim.getPersistentData().getBoolean(CommonEvents.SKIP_NEXT_DEATH_ROLLBACK_TAG)) {
            return false;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.size() < 2) {
            return false;
        }

        float sharedAmount = event.getAmount();
        event.setCanceled(true);
        redistributing = true;
        try {
            // 以魔法伤害源重定向：绕过护甲与附魔，保证每个玩家受到的伤害
            // 恰好等于受害者结算后的最终伤害，且受害者的护甲不会被计算两次；
            // dontgethurt 联动开启时用魔法伤害（交由部位损伤系统接管），关闭时用普通伤害。
            for (ServerPlayer player : players) {
                if (player.getAbilities().instabuild) {
                    continue;
                }
                player.hurt(RollbackConfig.dontgethurtIntegration
                        ? player.damageSources().magic()
                        : player.damageSources().generic(), sharedAmount);
                if (RollbackManager.consumeRollbackJustHappened()) {
                    break;
                }
            }
        } finally {
            redistributing = false;
        }
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (!RollbackConfig.syncInventoryAndHealth) {
            return;
        }
        if (++syncCounter % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.size() < 2) {
            return;
        }
        ServerPlayer master = players.stream()
                .min(Comparator.comparing(ServerPlayer::getUUID))
                .orElse(null);
        if (master == null || master.isDeadOrDying() || master.isSpectator() || master.getAbilities().instabuild) {
            return;
        }

        ListTag masterInventory = new ListTag();
        master.getInventory().save(masterInventory);

        for (ServerPlayer player : players) {
            if (player == master || player.isDeadOrDying() || player.isSpectator() || player.getAbilities().instabuild) {
                continue;
            }
            ListTag own = new ListTag();
            player.getInventory().save(own);
            if (!own.equals(masterInventory)) {
                player.getInventory().load(masterInventory.copy());
                player.getInventory().selected = Mth.clamp(master.getInventory().selected, 0, 8);
                player.inventoryMenu.broadcastChanges();
                player.containerMenu.broadcastChanges();
            }
            if (player.getHealth() != master.getHealth()) {
                player.setHealth(Mth.clamp(master.getHealth(), 1.0F, player.getMaxHealth()));
            }
            if (player.getAbsorptionAmount() != master.getAbsorptionAmount()) {
                player.setAbsorptionAmount(master.getAbsorptionAmount());
            }
            if (player.getFoodData().getFoodLevel() != master.getFoodData().getFoodLevel()) {
                player.getFoodData().setFoodLevel(master.getFoodData().getFoodLevel());
            }
            if (Math.abs(player.getFoodData().getSaturationLevel() - master.getFoodData().getSaturationLevel()) > 0.01F) {
                player.getFoodData().setSaturation(master.getFoodData().getSaturationLevel());
            }
        }
    }
}
