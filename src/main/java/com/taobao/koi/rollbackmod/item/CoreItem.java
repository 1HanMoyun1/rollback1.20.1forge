package com.taobao.koi.rollbackmod.item;

import com.taobao.koi.rollbackmod.core.CoreType;
import net.minecraft.world.item.Item;

public class CoreItem extends Item {
    private final CoreType coreType;

    public CoreItem(CoreType coreType, Properties properties) {
        super(properties);
        this.coreType = coreType;
    }

    public CoreType getCoreType() {
        return coreType;
    }
}
