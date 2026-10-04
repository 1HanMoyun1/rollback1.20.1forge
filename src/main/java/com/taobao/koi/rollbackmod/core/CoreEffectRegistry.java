package com.taobao.koi.rollbackmod.core;

import com.taobao.koi.rollbackmod.effect.CocoonCoreEffect;
import com.taobao.koi.rollbackmod.effect.MoltingCoreEffect;
import com.taobao.koi.rollbackmod.effect.TowerCoreEffect;
import java.util.EnumMap;
import java.util.Map;

public final class CoreEffectRegistry {
    private static final Map<CoreType, ICoreEffect> EFFECTS = new EnumMap<>(CoreType.class);

    private CoreEffectRegistry() {
    }

    public static void bootstrap() {
        if (!EFFECTS.isEmpty()) {
            return;
        }
        register(new CocoonCoreEffect());
        register(new MoltingCoreEffect());
        register(new TowerCoreEffect());
    }

    public static ICoreEffect get(CoreType type) {
        return EFFECTS.get(type);
    }

    private static void register(ICoreEffect effect) {
        EFFECTS.put(effect.type(), effect);
    }
}
