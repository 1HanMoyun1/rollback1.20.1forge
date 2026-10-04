package com.taobao.koi.rollbackmod.mixin;

import com.taobao.koi.rollbackmod.rollback.BlockRollbackManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 对齐 Fabric：拦截所有改方块入口（setBlock / destroyBlock / removeBlock），
 * 把“改动前的方块”记入回溯存档点。
 * <p>
 * Forge/NeoForge 的事件（BreakEvent/EntityPlaceEvent/…）覆盖不全：
 * 活塞推动、沙砾下落、火焰蔓延、树叶凋零、作物生长、流体流动等都不经过事件。
 * 直接钩住 {@link Level#setBlock} 后，任何被修改的方块都会被记录，回溯真正“覆盖存档”。
 * <p>
 * 世界生成发生在 ProtoChunk/WorldGenRegion 上，不经过 Level.setBlock，因此不会被误记录。
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            at = @At("HEAD")
    )
    private void rollbackmod$trackSetBlock(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerLevel level) {
            BlockRollbackManager.rememberBlockBeforeChange(level, pos, level.getBlockState(pos));
        }
    }

    @Inject(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD")
    )
    private void rollbackmod$trackDestroyBlock(BlockPos pos, boolean drop, Entity entity, int flags, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerLevel level) {
            BlockRollbackManager.rememberBlockBeforeChange(level, pos, level.getBlockState(pos));
        }
    }

    @Inject(
            method = "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD")
    )
    private void rollbackmod$trackRemoveBlock(BlockPos pos, boolean move, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerLevel level) {
            BlockRollbackManager.rememberBlockBeforeChange(level, pos, level.getBlockState(pos));
        }
    }
}
