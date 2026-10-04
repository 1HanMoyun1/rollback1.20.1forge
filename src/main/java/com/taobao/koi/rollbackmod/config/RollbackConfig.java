package com.taobao.koi.rollbackmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组配置：JSON 文件（config/rollbackmod.json）+ Cloth Config 界面编辑。
 * 所有键名与 Forge/NeoForge/Fabric 三平台一致，界面文本双语（见语言文件）。
 */
public final class RollbackConfig {
    // ===== 回溯 =====
    public static boolean enableDeathRollback = true;
    public static boolean destroyAllCoresOnRollback = true;

    // ===== 多人 =====
    public static boolean rollbackAllPlayersOnDeath = true;
    public static boolean syncInventoryAndHealth = true;

    // ===== dontgethurt 联动 =====
    public static boolean dontgethurtIntegration = true;

    // ===== 天数显示 =====
    public static boolean showDayHud = true;
    public static boolean countdownMode = false;
    public static int countdownDays = 7;

    // ===== 倒计时 =====
    public static boolean cocoonExtendsCountdownDay = true;

    private static Path configPath;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RollbackConfig() {
    }

    public static void load(Path path) {
        configPath = path;
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    GSON.fromJson(reader, ConfigFile.class).apply();
                }
            } else {
                save();
            }
        } catch (IOException | RuntimeException exception) {
            // 配置损坏时回退默认值并重写
            reset();
            save();
        }
    }

    public static void save() {
        if (configPath == null) {
            return;
        }
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(ConfigFile.capture(), writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static void reset() {
        enableDeathRollback = true;
        destroyAllCoresOnRollback = true;
        rollbackAllPlayersOnDeath = true;
        syncInventoryAndHealth = true;
        dontgethurtIntegration = true;
        showDayHud = true;
        countdownMode = false;
        countdownDays = 7;
        cocoonExtendsCountdownDay = true;
    }

    /** 与 JSON 文件一一对应的 DTO（缺省字段保持默认值）。 */
    private static final class ConfigFile {
        Boolean enable_death_rollback;
        Boolean destroy_all_cores_on_rollback;
        Boolean rollback_all_players_on_death;
        Boolean sync_inventory_and_health;
        Boolean dontgethurt_integration;
        Boolean show_day_hud;
        Boolean countdown_mode;
        Integer countdown_days;
        Boolean cocoon_extends_countdown_day;

        static ConfigFile capture() {
            ConfigFile file = new ConfigFile();
            file.enable_death_rollback = enableDeathRollback;
            file.destroy_all_cores_on_rollback = destroyAllCoresOnRollback;
            file.rollback_all_players_on_death = rollbackAllPlayersOnDeath;
            file.sync_inventory_and_health = syncInventoryAndHealth;
            file.dontgethurt_integration = dontgethurtIntegration;
            file.show_day_hud = showDayHud;
            file.countdown_mode = countdownMode;
            file.countdown_days = countdownDays;
            file.cocoon_extends_countdown_day = cocoonExtendsCountdownDay;
            return file;
        }

        void apply() {
            if (enable_death_rollback != null) enableDeathRollback = enable_death_rollback;
            if (destroy_all_cores_on_rollback != null) destroyAllCoresOnRollback = destroy_all_cores_on_rollback;
            if (rollback_all_players_on_death != null) rollbackAllPlayersOnDeath = rollback_all_players_on_death;
            if (sync_inventory_and_health != null) syncInventoryAndHealth = sync_inventory_and_health;
            if (dontgethurt_integration != null) dontgethurtIntegration = dontgethurt_integration;
            if (show_day_hud != null) showDayHud = show_day_hud;
            if (countdown_mode != null) countdownMode = countdown_mode;
            if (countdown_days != null) countdownDays = countdown_days;
            if (cocoon_extends_countdown_day != null) {
                cocoonExtendsCountdownDay = cocoon_extends_countdown_day;
            }
        }
    }
}
