package com.taobao.koi.rollbackmod.item;

import com.taobao.koi.rollbackmod.core.CoreEffectRegistry;
import com.taobao.koi.rollbackmod.core.CoreType;
import com.taobao.koi.rollbackmod.core.CoreUseResult;
import com.taobao.koi.rollbackmod.core.ICoreEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 吸入器：内部只能装 {@link #MAX_CORES} 枚药芯。
 * <ul>
 * <li>鼠标拿起药芯右键点击吸入器（背包/容器界面）：装入药芯；主手吸入器 + 副手药芯右键同样可装入；</li>
 * <li>鼠标空手右键点击吸入器（背包/容器界面）：取回药芯；</li>
 * <li>装有药芯时长按右键（喝水动画）：吸入并触发药芯效果；单击则会提示需要长按。</li>
 * </ul>
 */
public class InhalerItem extends Item {
    /** 只能装一枚药芯。 */
    public static final int MAX_CORES = 1;
    public static final int USE_DURATION_TICKS = 32;
    private static final String CORES_TAG = "Cores";

    public InhalerItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    /** 背包界面：鼠标拿着药芯右键点击吸入器装入；鼠标空手右键点击吸入器取回药芯。 */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack inhaler, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        if (other.isEmpty()) {
            ItemStack taken = takeFirstCore(inhaler);
            if (taken.isEmpty()) {
                return false;
            }
            access.set(taken);
            player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 1.0F);
            return true;
        }
        if (hasCores(inhaler) || !ModItems.isCore(other)) {
            return false;
        }
        Component coreName = other.getHoverName();
        if (!insertCore(inhaler, other)) {
            return false;
        }
        other.shrink(1);
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.rollbackmod.core_installed", coreName), true);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack inhaler = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(inhaler);
        }
        if (level.isClientSide()) {
            if (hasCores(inhaler)) {
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(inhaler);
            }
            if (ModItems.isCore(player.getOffhandItem())) {
                return InteractionResultHolder.success(inhaler);
            }
            return InteractionResultHolder.fail(inhaler);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(inhaler);
        }

        // 空吸入器：副手持药芯右键 → 装入（背包界面鼠标右键吸入器同样可装入）
        if (!hasCores(inhaler)) {
            ItemStack offhand = player.getOffhandItem();
            if (ModItems.isCore(offhand)) {
                Component coreName = offhand.getHoverName();
                if (insertCore(inhaler, offhand)) {
                    if (!player.getAbilities().instabuild) {
                        offhand.shrink(1);
                    }
                    player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.0F);
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.rollbackmod.core_installed", coreName), true);
                    return InteractionResultHolder.success(inhaler);
                }
            }
            serverPlayer.displayClientMessage(Component.translatable("message.rollbackmod.inhaler_empty_hint"), true);
            return InteractionResultHolder.fail(inhaler);
        }

        // 装有药芯：长按右键（喝水动作）吸入并触发效果
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(inhaler);
    }

    /** 单击（未长按完成）时提示需要长按吸入。 */
    @Override
    public void releaseUsing(ItemStack inhaler, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide() || timeLeft <= 0 || !hasCores(inhaler)) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.rollbackmod.inhaler_hold_to_inhale"), true);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack inhaler, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            if (!hasCores(inhaler)) {
                return inhaler;
            }
            Optional<CoreType> type = peekFirstCoreType(inhaler);
            if (type.isEmpty()) {
                return inhaler;
            }
            ICoreEffect effect = CoreEffectRegistry.get(type.get());
            if (effect != null) {
                ItemStack coreStack = peekFirstCore(inhaler).orElse(ItemStack.EMPTY);
                CoreUseResult result = effect.onUseInhaler(player, inhaler, coreStack);
                if (result.message() != null) {
                    player.displayClientMessage(result.message(), true);
                }
                if (result.success() && result.consumeCore() && !player.getAbilities().instabuild) {
                    takeFirstCore(inhaler);
                }
            }
        }
        return inhaler;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<ItemStack> cores = getCores(stack);
        if (cores.isEmpty()) {
            tooltip.add(Component.translatable("item.rollbackmod.inhaler.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.rollbackmod.inhaler.installed", cores.get(0).getHoverName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rollbackmod.inhaler.removable").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ===== 药芯存取（1.20.5+ 用 DataComponents.CONTAINER） =====

    public static boolean hasCores(ItemStack inhaler) {
        return !getCores(inhaler).isEmpty();
    }

    public static List<ItemStack> getCores(ItemStack inhaler) {
        List<ItemStack> cores = new ArrayList<>();
        for (ItemStack core : inhaler.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems()) {
            cores.add(core);
        }
        return cores;
    }

    public static boolean insertCore(ItemStack inhaler, ItemStack core) {
        if (core.isEmpty() || !ModItems.isCore(core)) {
            return false;
        }
        List<ItemStack> cores = getCores(inhaler);
        if (cores.size() >= MAX_CORES) {
            return false;
        }
        ItemStack copy = core.copy();
        copy.setCount(1);
        cores.add(copy);
        inhaler.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(cores));
        return true;
    }

    public static Optional<ItemStack> peekFirstCore(ItemStack inhaler) {
        List<ItemStack> cores = getCores(inhaler);
        return cores.isEmpty() ? Optional.empty() : Optional.of(cores.get(0));
    }

    public static Optional<CoreType> peekFirstCoreType(ItemStack inhaler) {
        return peekFirstCore(inhaler).flatMap(ModItems::getCoreType);
    }

    public static ItemStack takeFirstCore(ItemStack inhaler) {
        List<ItemStack> cores = getCores(inhaler);
        if (cores.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = cores.remove(0);
        if (cores.isEmpty()) {
            inhaler.remove(DataComponents.CONTAINER);
        } else {
            inhaler.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(cores));
        }
        return taken;
    }

    public static void clearCores(ItemStack inhaler) {
        inhaler.remove(DataComponents.CONTAINER);
    }
}