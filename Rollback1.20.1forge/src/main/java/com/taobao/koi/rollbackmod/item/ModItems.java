package com.taobao.koi.rollbackmod.item;

import com.taobao.koi.rollbackmod.RollbackMod;
import com.taobao.koi.rollbackmod.core.CoreType;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RollbackMod.MOD_ID);

    /** 吸入器：装入 1 枚药芯，长按右键（喝水动作）触发。**无限耐久**（无最大耐久，永不损坏）。 */
    public static final RegistryObject<Item> INHALER = ITEMS.register("inhaler",
            () -> new InhalerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> COCOON_CORE = registerCore(CoreType.COCOON);
    public static final RegistryObject<Item> MOLTING_CORE = registerCore(CoreType.MOLTING);
    public static final RegistryObject<Item> TOWER_CORE = registerCore(CoreType.TOWER);

    private ModItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static Optional<CoreType> getCoreType(ItemStack stack) {
        if (stack.getItem() instanceof CoreItem coreItem) {
            return Optional.of(coreItem.getCoreType());
        }
        return Optional.empty();
    }

    public static boolean isCore(ItemStack stack) {
        return getCoreType(stack).isPresent();
    }

    private static RegistryObject<Item> registerCore(CoreType type) {
        return ITEMS.register(type.registryName(),
                () -> new CoreItem(type, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    }
}
