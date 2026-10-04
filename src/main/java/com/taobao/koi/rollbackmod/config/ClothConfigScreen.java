package com.taobao.koi.rollbackmod.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config 配置界面（双语：所有文本走语言文件 translate key）。
 */
public final class ClothConfigScreen {
    private ClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.rollbackmod.title"))
                .setSavingRunnable(RollbackConfig::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // ===== 回溯 =====
        ConfigCategory rollback = builder.getOrCreateCategory(Component.translatable("config.rollbackmod.category.rollback"));
        rollback.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.rollbackmod.enable_death_rollback"), RollbackConfig.enableDeathRollback)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.rollbackmod.enable_death_rollback.tooltip"))
                .setSaveConsumer(value -> RollbackConfig.enableDeathRollback = value)
                .build());
        rollback.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.rollbackmod.destroy_all_cores_on_rollback"), RollbackConfig.destroyAllCoresOnRollback)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.rollbackmod.destroy_all_cores_on_rollback.tooltip"))
                .setSaveConsumer(value -> RollbackConfig.destroyAllCoresOnRollback = value)
                .build());

        // ===== 多人 =====
        ConfigCategory multiplayer = builder.getOrCreateCategory(Component.translatable("config.rollbackmod.category.multiplayer"));
        multiplayer.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.rollbackmod.rollback_all_players_on_death"), RollbackConfig.rollbackAllPlayersOnDeath)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.rollbackmod.rollback_all_players_on_death.tooltip"))
                .setSaveConsumer(value -> RollbackConfig.rollbackAllPlayersOnDeath = value)
                .build());
        multiplayer.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.rollbackmod.sync_inventory_and_health"), RollbackConfig.syncInventoryAndHealth)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.rollbackmod.sync_inventory_and_health.tooltip"))
                .setSaveConsumer(value -> RollbackConfig.syncInventoryAndHealth = value)
                .build());

        // ===== dontgethurt 联动 =====
        ConfigCategory dontgethurt = builder.getOrCreateCategory(Component.translatable("config.rollbackmod.category.dontgethurt"));
        dontgethurt.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.rollbackmod.dontgethurt_integration"), RollbackConfig.dontgethurtIntegration)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.rollbackmod.dontgethurt_integration.tooltip"))
                .setSaveConsumer(value -> RollbackConfig.dontgethurtIntegration = value)
                .build());

        // ===== 天数显示 =====
        ConfigCategory dayDisplay = builder.getOrCreateCategory(Component.translatable("config.rollbackmod.category.day_display"));
        dayDisplay.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.rollbackmod.show_day_hud"), RollbackConfig.showDayHud)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.rollbackmod.show_day_hud.tooltip"))
                .setSaveConsumer(value -> RollbackConfig.showDayHud = value)
                .build());
        dayDisplay.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.rollbackmod.countdown_mode"), RollbackConfig.countdownMode)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.rollbackmod.countdown_mode.tooltip"))
                .setSaveConsumer(value -> RollbackConfig.countdownMode = value)
                .build());
        dayDisplay.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.rollbackmod.countdown_days"), RollbackConfig.countdownDays)
                .setDefaultValue(7)
                .setMin(1).setMax(100000)
                .setTooltip(Component.translatable("config.rollbackmod.countdown_days.tooltip"))
                .setSaveConsumer(value -> RollbackConfig.countdownDays = value)
                .build());
        dayDisplay.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.rollbackmod.cocoon_extends_countdown_day"), RollbackConfig.cocoonExtendsCountdownDay)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.rollbackmod.cocoon_extends_countdown_day.tooltip"))
                .setSaveConsumer(value -> RollbackConfig.cocoonExtendsCountdownDay = value)
                .build());

        return builder.build();
    }
}
