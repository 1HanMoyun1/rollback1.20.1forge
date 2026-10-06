package com.taobao.koi.rollbackmod.client;

import com.taobao.koi.rollbackmod.network.DayInfoPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 天数显示与跨天/回溯特效：
 * <ul>
 * <li>左上角常驻“第 X 天 / 还剩 X 天”（可在配置中关闭）；</li>
 * <li>跨天（自然流逝 / 睡觉 / 回溯）时全屏变黑并居中显示时间，数字按滚动规则滚动，
 * 前后文字固定不动；黑屏注册在**最上层**，会盖住聊天栏与动作栏消息。</li>
 * </ul>
 */
public final class ClientDayHud {
    /** 黑屏总时长（放慢后约 5 秒，配合服务端分帧加载存档世界数据）。 */
    private static final long ROLLBACK_SCREEN_DURATION_MS = 5000L;

    private static boolean showHud;
    private static boolean countdownMode;
    private static int currentDay = 1;
    private static int remainingDays;

    // 记录的是「屏幕上显示的数字」，而不是天数本身，便于倒计时模式下正确判断大小
    private static int transitionFromNumber = -1;
    private static int transitionToNumber = -1;
    private static long transitionStartedAt = -1L;
    // 最近一次真正播放过的转场，用于忽略重复下发的同一转场（否则动画会一直停在起始帧）
    private static int lastPlayedFrom = Integer.MIN_VALUE;
    private static int lastPlayedTo = Integer.MIN_VALUE;
    private static long lastPlayedAt = -1L;

    private ClientDayHud() {
    }

    /** 注册 HUD：天数常驻层 + 最上层黑屏层（具体注册事件按 MC 版本在 ClientDayHudRegistration 里实现）。 */
    public static void register(IEventBus modBus) {
        ClientDayHudRegistration.register(modBus);
    }

    public static void update(DayInfoPacket packet) {
        showHud = packet.showHud();
        countdownMode = packet.countdownMode();
        remainingDays = packet.remainingDays();
        int day = packet.day();
        if (packet.playAnimation()) {
            // 服务端给出两端数字（大数自上而下、小数自下而上由客户端按大小判定）
            beginTransition(packet.fromNumber(), packet.toNumber());
        }
        currentDay = day;
    }

    private static void beginTransition(int fromNumber, int toNumber) {
        if (toNumber < 0) {
            return;
        }
        if (fromNumber == toNumber) {
            // 两端数字一致时给一个可辨识的「原地滚入」动画
            fromNumber = toNumber + 1;
        }
        long now = Util.getMillis();
        // 同一次转场被重复下发时（例如服务端多次 sync）不重启动画，
        // 否则画面会一直停在起始帧，看起来就像“上一个数字没消失”。
        if (fromNumber == lastPlayedFrom && toNumber == lastPlayedTo
                && now - lastPlayedAt < ROLLBACK_SCREEN_DURATION_MS) {
            return;
        }
        lastPlayedFrom = fromNumber;
        lastPlayedTo = toNumber;
        lastPlayedAt = now;
        transitionFromNumber = fromNumber;
        transitionToNumber = toNumber;
        transitionStartedAt = now;
    }

    public static void renderDayHud(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !showHud || isTransitionActive()) {
            return;
        }
        Font font = minecraft.font;
        guiGraphics.drawString(font, textFor(currentDay), 4, 4, 0xFFFFFFFF, true);
    }

    public static void renderTransition(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !isTransitionActive()) {
            return;
        }
        renderTransitionScreen(guiGraphics, minecraft);
    }

    private static boolean isTransitionActive() {
        if (transitionStartedAt <= 0L) {
            return false;
        }
        long elapsed = Util.getMillis() - transitionStartedAt;
        if (elapsed >= ROLLBACK_SCREEN_DURATION_MS) {
            transitionStartedAt = -1L;
            return false;
        }
        return true;
    }

    /** 全屏黑屏 + 居中时间：只有数字上下滚动，前后文字固定不动。 */
    private static void renderTransitionScreen(GuiGraphics guiGraphics, Minecraft minecraft) {
        long elapsed = Util.getMillis() - transitionStartedAt;

        float fadeIn = Mth.clamp(elapsed / 500.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((ROLLBACK_SCREEN_DURATION_MS - elapsed) / 600.0F, 0.0F, 1.0F);
        float visibility = Math.min(fadeIn, fadeOut);

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        guiGraphics.fill(0, 0, width, height, Mth.clamp((int) (255.0F * visibility), 0, 255) << 24);

        Font font = minecraft.font;
        // 整段黑屏 5s，数字滚动约 1.5s：旧数字在前 55% 内滚走淡出，之后只剩新数字
        float scrollT = Mth.clamp(elapsed / 1500.0F, 0.0F, 1.0F);
        float eased = easeInOutCubic(scrollT);
        float lineHeight = (font.lineHeight + 6.0F) * 3.0F;

        // 方向按“数值大小”判定：大数从上方往下滚入，小数从下方往上滚入
        // （如第 3 天：4 自上而下滚入、2 自下而上滚入；还剩 4 天同理：5 自上而下、3 自下而上）
        boolean newIsLarger = transitionToNumber > transitionFromNumber;

        float oldOffset;
        float newOffset;
        if (newIsLarger) {
            // 新数字更大：新数字从上方滚入，旧数字向下滚出
            newOffset = (1.0F - eased) * -lineHeight;
            oldOffset = eased * lineHeight;
        } else {
            // 新数字更小：新数字从下方滚入，旧数字向上滚出
            newOffset = (1.0F - eased) * lineHeight;
            oldOffset = -eased * lineHeight;
        }

        int alpha = Mth.clamp((int) (255.0F * visibility), 0, 255);

        // 旧数字只在前 55% 的时间里滚走并淡出，之后彻底不再绘制，避免残留
        float oldT = Mth.clamp(scrollT / 0.55F, 0.0F, 1.0F);
        boolean drawOld = transitionFromNumber != transitionToNumber && oldT < 1.0F;
        int oldAlpha = (int) (alpha * (1.0F - oldT));

        // 文字固定，只有数字滚动
        String prefix = labelPrefix();
        String suffix = labelSuffix();
        String oldNumber = Integer.toString(transitionFromNumber);
        String newNumber = Integer.toString(transitionToNumber);
        int numberWidth = Math.max(font.width(oldNumber), font.width(newNumber));
        int totalWidth = font.width(prefix) + numberWidth + font.width(suffix);
        int left = width / 2 / 3 - totalWidth / 2;
        int baseY = height / 2 / 3 - font.lineHeight / 2;
        int numberLeft = left + font.width(prefix);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(3.0F, 3.0F, 1.0F);
        guiGraphics.drawString(font, prefix, left, baseY, colorWithAlpha(0xFFFFFF, alpha), true);
        guiGraphics.drawString(font, suffix, numberLeft + numberWidth, baseY, colorWithAlpha(0xFFFFFF, alpha), true);
        if (drawOld && oldAlpha > 2) {
            // 旧数字：滑走一半距离就开始淡出，55% 时完全消失
            guiGraphics.drawString(font, oldNumber, numberLeft + (numberWidth - font.width(oldNumber)) / 2,
                    (int) (baseY + oldOffset / 3.0F), colorWithAlpha(0xFFFFFF, oldAlpha), true);
        }
        guiGraphics.drawString(font, newNumber, numberLeft + (numberWidth - font.width(newNumber)) / 2,
                (int) (baseY + newOffset / 3.0F), colorWithAlpha(0xFFFFFF, (int) (alpha * scrollT)), true);
        guiGraphics.pose().popPose();
    }

    private static String labelPrefix() {
        return Component.translatable(countdownMode
                ? "hud.rollbackmod.remaining_days.prefix"
                : "hud.rollbackmod.day.prefix").getString();
    }

    private static String labelSuffix() {
        return Component.translatable(countdownMode
                ? "hud.rollbackmod.remaining_days.suffix"
                : "hud.rollbackmod.day.suffix").getString();
    }

    private static int numberFor(int day) {
        if (countdownMode) {
            return remainingDays + (currentDay - day);
        }
        return day;
    }

    private static Component textFor(int day) {
        if (countdownMode) {
            return Component.translatable("hud.rollbackmod.remaining_days", numberFor(day));
        }
        return Component.translatable("hud.rollbackmod.day", day);
    }

    private static int colorWithAlpha(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static float easeInOutCubic(float t) {
        return t < 0.5F
                ? 4.0F * t * t * t
                : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F;
    }
}
