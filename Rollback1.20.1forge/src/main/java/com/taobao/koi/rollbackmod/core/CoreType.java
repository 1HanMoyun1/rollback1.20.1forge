package com.taobao.koi.rollbackmod.core;

/** 轻量化后仅保留三种药芯：化茧（存档）、蜕皮（回溯）、高塔（自毁）。 */
public enum CoreType {
    COCOON("cocoon_core"),
    MOLTING("molting_core"),
    TOWER("tower_core");

    private final String registryName;

    CoreType(String registryName) {
        this.registryName = registryName;
    }

    public String registryName() {
        return registryName;
    }
}
