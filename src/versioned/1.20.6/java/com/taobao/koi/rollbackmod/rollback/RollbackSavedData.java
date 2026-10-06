package com.taobao.koi.rollbackmod.rollback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.ServerLevelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RollbackSavedData extends SavedData {
    public static final String DATA_NAME = "rollbackmod_state";
    private static final Logger LOGGER = LoggerFactory.getLogger(RollbackSavedData.class);

    private Checkpoint checkpoint;
    private boolean initialCheckpointCreated;
    private long countdownStartDay;
    private boolean countdownExpired;
    private long lastAnnouncedDay;
    private final Map<String, BlockSnapshotRecord> changedBlocks = new LinkedHashMap<>();
    private final Set<UUID> selfDestructedPlayers = new HashSet<>();

    public static RollbackSavedData get(MinecraftServer server) {
        return SavedDataAccess.get(server);
    }

    public static RollbackSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RollbackSavedData data = new RollbackSavedData();
        data.initialCheckpointCreated = tag.getBoolean("initialCheckpointCreated");
        if (tag.contains("countdownStartDay", Tag.TAG_LONG)) {
            data.countdownStartDay = Math.max(1L, tag.getLong("countdownStartDay"));
        }
        data.countdownExpired = tag.getBoolean("countdownExpired");
        if (tag.contains("lastAnnouncedDay", Tag.TAG_LONG)) {
            data.lastAnnouncedDay = Math.max(1L, tag.getLong("lastAnnouncedDay"));
        }
        if (tag.contains("checkpoint", Tag.TAG_COMPOUND)) {
            try {
                data.checkpoint = Checkpoint.load(tag.getCompound("checkpoint"));
            } catch (RuntimeException exception) {
                LOGGER.warn("Discarding invalid rollback checkpoint data", exception);
            }
        }
        int[] selfDestructedPlayerIds = tag.getIntArray("selfDestructedPlayers");
        for (int i = 0; i + 3 < selfDestructedPlayerIds.length; i += 4) {
            long most = ((long) selfDestructedPlayerIds[i] << 32) | (selfDestructedPlayerIds[i + 1] & 0xFFFFFFFFL);
            long least = ((long) selfDestructedPlayerIds[i + 2] << 32) | (selfDestructedPlayerIds[i + 3] & 0xFFFFFFFFL);
            data.selfDestructedPlayers.add(new UUID(most, least));
        }
        ListTag blockList = tag.getList("changedBlocks", Tag.TAG_COMPOUND);
        for (Tag rawBlock : blockList) {
            try {
                BlockSnapshotRecord record = BlockSnapshotRecord.load((CompoundTag) rawBlock);
                data.changedBlocks.put(blockKey(record.dimension(), record.pos()), record);
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid rollback block snapshot", exception);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("initialCheckpointCreated", initialCheckpointCreated);
        tag.putLong("countdownStartDay", countdownStartDay);
        tag.putBoolean("countdownExpired", countdownExpired);
        tag.putLong("lastAnnouncedDay", lastAnnouncedDay);
        if (checkpoint != null) {
            tag.put("checkpoint", checkpoint.save());
        }
        int[] selfDestructedPlayerIds = new int[selfDestructedPlayers.size() * 4];
        int selfDestructedPlayerIndex = 0;
        for (UUID playerId : selfDestructedPlayers) {
            selfDestructedPlayerIds[selfDestructedPlayerIndex++] = (int) (playerId.getMostSignificantBits() >> 32);
            selfDestructedPlayerIds[selfDestructedPlayerIndex++] = (int) playerId.getMostSignificantBits();
            selfDestructedPlayerIds[selfDestructedPlayerIndex++] = (int) (playerId.getLeastSignificantBits() >> 32);
            selfDestructedPlayerIds[selfDestructedPlayerIndex++] = (int) playerId.getLeastSignificantBits();
        }
        tag.putIntArray("selfDestructedPlayers", selfDestructedPlayerIds);
        ListTag blockList = new ListTag();
        for (BlockSnapshotRecord record : changedBlocks.values()) {
            blockList.add(record.save());
        }
        tag.put("changedBlocks", blockList);
        return tag;
    }

    public boolean hasCheckpoint() {
        return checkpoint != null;
    }

    public Checkpoint getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Checkpoint checkpoint) {
        this.checkpoint = checkpoint;
        setDirty();
    }

    public void clearCheckpoint() {
        this.checkpoint = null;
        setDirty();
    }

    public boolean isInitialCheckpointCreated() {
        return initialCheckpointCreated;
    }

    public void setInitialCheckpointCreated(boolean initialCheckpointCreated) {
        this.initialCheckpointCreated = initialCheckpointCreated;
        setDirty();
    }

    public boolean isSelfDestructed(UUID playerId) {
        return selfDestructedPlayers.contains(playerId);
    }

    public void markSelfDestructed(UUID playerId) {
        if (selfDestructedPlayers.add(playerId)) {
            setDirty();
        }
    }

    public Set<UUID> getSelfDestructedPlayers() {
        return Set.copyOf(selfDestructedPlayers);
    }

    public void clearSelfDestructedPlayers() {
        if (!selfDestructedPlayers.isEmpty()) {
            selfDestructedPlayers.clear();
            setDirty();
        }
    }

    public Collection<BlockSnapshotRecord> getChangedBlocks() {
        return changedBlocks.values();
    }

    public void rememberChangedBlock(BlockSnapshotRecord record) {
        String key = blockKey(record.dimension(), record.pos());
        if (!changedBlocks.containsKey(key)) {
            changedBlocks.put(key, record);
            setDirty();
        }
    }

    public void clearChangedBlocks() {
        changedBlocks.clear();
        setDirty();
    }

    public long getCountdownStartDay() {
        return countdownStartDay;
    }

    public void setCountdownStartDay(long countdownStartDay) {
        this.countdownStartDay = Math.max(1L, countdownStartDay);
        setDirty();
    }

    public boolean isCountdownExpired() {
        return countdownExpired;
    }

    public void setCountdownExpired(boolean countdownExpired) {
        this.countdownExpired = countdownExpired;
        setDirty();
    }

    public long getLastAnnouncedDay() {
        return lastAnnouncedDay;
    }

    public void setLastAnnouncedDay(long lastAnnouncedDay) {
        this.lastAnnouncedDay = Math.max(1L, lastAnnouncedDay);
        setDirty();
    }

    /**
     * 存档点：全量“存档覆盖”式快照。
     * <p>
     * 与旧版逐字段快照不同，这里保存的是整个世界的完整序列化状态
     * （每个维度的世界时间/天气、所有非玩家实体、所有已加载区块的方块实体，
     * 以及每个玩家的完整存档 NBT——含其他模组写入的能力/持久数据），
     * 回溯时直接整体覆盖，避免多模组环境下部分数据无法回溯。
     */
    public record Checkpoint(Map<String, LevelSnapshot> levels, Map<UUID, PlayerSnapshot> players) {
        public static Checkpoint capture(MinecraftServer server) {
            Map<String, LevelSnapshot> levels = new HashMap<>();
            for (ServerLevel level : server.getAllLevels()) {
                levels.put(level.dimension().location().toString(), LevelSnapshot.capture(level));
            }
            Map<UUID, PlayerSnapshot> players = new HashMap<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                players.put(player.getUUID(), PlayerSnapshot.capture(player));
            }
            return new Checkpoint(levels, players);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            CompoundTag levelsTag = new CompoundTag();
            for (Map.Entry<String, LevelSnapshot> entry : levels.entrySet()) {
                levelsTag.put(entry.getKey(), entry.getValue().save());
            }
            tag.put("levels", levelsTag);
            CompoundTag playersTag = new CompoundTag();
            for (Map.Entry<UUID, PlayerSnapshot> entry : players.entrySet()) {
                playersTag.put(entry.getKey().toString(), entry.getValue().save());
            }
            tag.put("players", playersTag);
            return tag;
        }

        public static Checkpoint load(CompoundTag tag) {
            if (!tag.contains("levels", Tag.TAG_COMPOUND)) {
                throw new IllegalStateException("Checkpoint is missing level snapshots");
            }
            Map<String, LevelSnapshot> levels = new HashMap<>();
            CompoundTag levelsTag = tag.getCompound("levels");
            for (String key : levelsTag.getAllKeys()) {
                levels.put(key, LevelSnapshot.load(levelsTag.getCompound(key)));
            }
            Map<UUID, PlayerSnapshot> players = new HashMap<>();
            CompoundTag playersTag = tag.getCompound("players");
            for (String key : playersTag.getAllKeys()) {
                try {
                    players.put(UUID.fromString(key), PlayerSnapshot.load(playersTag.getCompound(key)));
                } catch (RuntimeException exception) {
                    LOGGER.warn("Skipping invalid rollback player snapshot {}", key, exception);
                }
            }
            return new Checkpoint(levels, players);
        }

        public Optional<PlayerSnapshot> getSnapshot(UUID playerId) {
            return Optional.ofNullable(players.get(playerId));
        }
    }

    /** 单个维度的全量快照。 */
    public record LevelSnapshot(
            long dayTime,
            boolean raining,
            int rainTime,
            boolean thundering,
            int thunderTime,
            int clearWeatherTime,
            ListTag entities,
            Map<String, CompoundTag> blockEntities
    ) {
        public static LevelSnapshot capture(ServerLevel level) {
            ListTag entityTag = new ListTag();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ServerPlayer || entity.getVehicle() != null) {
                    continue;
                }
                CompoundTag nbt = new CompoundTag();
                if (entity.saveAsPassenger(nbt)) {
                    entityTag.add(nbt);
                }
            }

            Map<String, CompoundTag> blockEntityTag = new HashMap<>();
            for (ChunkPos pos : ChunkTracker.getLoadedPositions(level)) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    blockEntityTag.put(
                            Long.toString(blockEntity.getBlockPos().asLong()),
                            blockEntity.saveWithFullMetadata(level.registryAccess())
                    );
                }
            }

            return new LevelSnapshot(
                    level.getDayTime(),
                    level.isRaining(),
                    ((ServerLevelData) level.getLevelData()).getRainTime(),
                    level.isThundering(),
                    ((ServerLevelData) level.getLevelData()).getThunderTime(),
                    ((ServerLevelData) level.getLevelData()).getClearWeatherTime(),
                    entityTag,
                    blockEntityTag
            );
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("dayTime", dayTime);
            tag.putBoolean("raining", raining);
            tag.putInt("rainTime", rainTime);
            tag.putBoolean("thundering", thundering);
            tag.putInt("thunderTime", thunderTime);
            tag.putInt("clearWeatherTime", clearWeatherTime);
            tag.put("entities", entities.copy());
            CompoundTag blockEntityTag = new CompoundTag();
            for (Map.Entry<String, CompoundTag> entry : blockEntities.entrySet()) {
                blockEntityTag.put(entry.getKey(), entry.getValue().copy());
            }
            tag.put("blockEntities", blockEntityTag);
            return tag;
        }

        public static LevelSnapshot load(CompoundTag tag) {
            Map<String, CompoundTag> blockEntityTag = new HashMap<>();
            CompoundTag rawBlockEntities = tag.getCompound("blockEntities");
            for (String key : rawBlockEntities.getAllKeys()) {
                blockEntityTag.put(key, rawBlockEntities.getCompound(key).copy());
            }
            return new LevelSnapshot(
                    tag.getLong("dayTime"),
                    tag.getBoolean("raining"),
                    tag.getInt("rainTime"),
                    tag.getBoolean("thundering"),
                    tag.getInt("thunderTime"),
                    tag.getInt("clearWeatherTime"),
                    tag.getList("entities", Tag.TAG_COMPOUND),
                    blockEntityTag
            );
        }

        /** 同步恢复维度时间/天气，并清空当前非玩家实体；实体/方块实体由 {@link RollbackRestoreQueue} 分帧恢复。 */
        public void restoreWorldState(ServerLevel level) {
            level.setDayTime(dayTime);
            level.setWeatherParameters(clearWeatherTime, rainTime, raining, thundering);

            List<Entity> toDiscard = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) {
                    toDiscard.add(entity);
                }
            }
            for (Entity entity : toDiscard) {
                entity.discard();
            }
        }
    }

    /** 玩家完整存档快照（含模组能力数据，即 saveWithoutId 的全量 NBT）。 */
    public record PlayerSnapshot(UUID playerId, String dimension, CompoundTag data) {
        public static PlayerSnapshot capture(ServerPlayer player) {
            CompoundTag tag = new CompoundTag();
            player.saveWithoutId(tag);
            return new PlayerSnapshot(player.getUUID(), player.serverLevel().dimension().location().toString(), tag);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("playerId", playerId);
            tag.putString("dimension", dimension);
            tag.put("data", data.copy());
            return tag;
        }

        public static PlayerSnapshot load(CompoundTag tag) {
            return new PlayerSnapshot(
                    tag.getUUID("playerId"),
                    tag.getString("dimension"),
                    tag.getCompound("data").copy()
            );
        }

        public void restore(ServerPlayer player, MinecraftServer server) {
            ServerLevel targetLevel = resolveLevel(server, dimension).orElse(player.serverLevel());
            ListTag pos = data.getList("Pos", Tag.TAG_DOUBLE);
            ListTag rotation = data.getList("Rotation", Tag.TAG_FLOAT);

            // 1. 位置：显式传送（不依赖 load()），保证回溯后位置一定改变
            if (pos.size() == 3 && rotation.size() == 2) {
                player.teleportTo(
                        targetLevel,
                        pos.getDouble(0),
                        pos.getDouble(1),
                        pos.getDouble(2),
                        rotation.getFloat(0),
                        rotation.getFloat(1)
                );
            } else {
                LOGGER.warn("Rollback player snapshot has invalid Pos/Rotation, keeping current position for {}", playerId);
            }

            // 2. 背包：显式清空 + 加载，保证回溯后背包一定恢复
            player.getInventory().clearContent();
            player.getInventory().load(data.getList("Inventory", Tag.TAG_COMPOUND).copy());
            player.getInventory().selected = Mth.clamp(data.getInt("SelectedItemSlot"), 0, 8);

            // 3. 状态：显式恢复生命/效果/食物/经验/火焰/氧气
            player.removeAllEffects();
            for (Tag rawEffect : data.getList("ActiveEffects", Tag.TAG_COMPOUND)) {
                MobEffectInstance effect = MobEffectInstance.load((CompoundTag) rawEffect);
                if (effect != null) {
                    player.addEffect(effect);
                }
            }
            player.setAbsorptionAmount(data.getFloat("AbsorptionAmount"));
            player.setHealth(Mth.clamp(data.getFloat("Health"), 1.0F, player.getMaxHealth()));
            player.getFoodData().readAdditionalSaveData(data);
            player.experienceLevel = data.getInt("XpLevel");
            player.totalExperience = data.getInt("XpTotal");
            player.experienceProgress = data.getFloat("XpP");
            player.setRemainingFireTicks(data.getShort("Fire"));
            player.setAirSupply(data.getShort("Air"));
            player.fallDistance = data.getFloat("FallDistance");

            player.deathTime = 0;
            player.hurtTime = 0;
            player.hurtDuration = 0;
            player.invulnerableTime = 20;
            player.setPose(Pose.STANDING);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            player.hurtMarked = true;

            // 4. 其他模组数据（Capability 等）：整体加载兜底，失败不影响上面的核心恢复
            try {
                player.load(data.copy());
            } catch (RuntimeException exception) {
                LOGGER.warn("Full player snapshot load failed for {}, keeping explicit restore", playerId, exception);
            }
            player.deathTime = 0;
            player.setHealth(Mth.clamp(player.getHealth(), 1.0F, player.getMaxHealth()));

            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            if (player.connection != null) {
                player.connection.send(new ClientboundSetExperiencePacket(
                        player.experienceProgress,
                        player.totalExperience,
                        player.experienceLevel
                ));
            }
        }
    }

    public static Optional<ServerLevel> resolveLevel(MinecraftServer server, String dimensionId) {
        ResourceLocation location = ResourceLocation.tryParse(dimensionId);
        if (location == null) {
            return Optional.empty();
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, location);
        return Optional.ofNullable(server.getLevel(key));
    }

    private static String blockKey(String dimension, BlockPos pos) {
        return dimension + "|" + pos.asLong();
    }

    public record BlockSnapshotRecord(
            String dimension,
            BlockPos pos,
            BlockState state,
            CompoundTag blockEntityTag
    ) {
        public static BlockSnapshotRecord capture(ServerLevel level, BlockPos pos) {
            return capture(level, pos, level.getBlockState(pos));
        }

        public static BlockSnapshotRecord capture(ServerLevel level, BlockPos pos, BlockState state) {
            // 仅当记录的状态就是该位置当前方块时才附带方块实体；否则（如放置方块时记录的是被替换掉的旧方块）
            // 该方块实体属于新方块，强行配对会在回溯时用错误的方块类型创建方块实体而崩溃
            // （例如把床方块实体创建在空气上 -> AirBlock cannot be cast to BedBlock）。
            BlockEntity blockEntity = level.getBlockState(pos) == state ? level.getBlockEntity(pos) : null;
            CompoundTag tag = blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess());
            return new BlockSnapshotRecord(
                    level.dimension().location().toString(),
                    pos.immutable(),
                    state,
                    tag
            );
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension);
            tag.put("pos", NbtUtils.writeBlockPos(pos));
            tag.put("state", NbtUtils.writeBlockState(state));
            if (blockEntityTag != null) {
                tag.put("blockEntity", blockEntityTag.copy());
            }
            return tag;
        }

        public static BlockSnapshotRecord load(CompoundTag tag) {
            CompoundTag blockEntityTag = tag.contains("blockEntity", Tag.TAG_COMPOUND)
                    ? tag.getCompound("blockEntity")
                    : null;
            return new BlockSnapshotRecord(
                    tag.getString("dimension"),
                    NbtUtils.readBlockPos(tag, "pos").orElseThrow(),
                    NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("state")),
                    blockEntityTag
            );
        }

        public void restore(MinecraftServer server) {
            resolveLevel(server, dimension).ifPresent(level -> {
                try {
                    BlockState oldState = level.getBlockState(pos);
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    level.removeBlockEntity(pos);
                    // 仅当恢复后的方块确实带方块实体时才写回，避免在空气等方块上创建方块实体导致崩溃
                    if (blockEntityTag != null && state.hasBlockEntity()) {
                        BlockEntity blockEntity = BlockEntity.loadStatic(pos, state, blockEntityTag.copy(), level.registryAccess());
                        if (blockEntity != null) {
                            level.setBlockEntity(blockEntity);
                        }
                    }
                    level.sendBlockUpdated(pos, oldState, state, 3);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Skipping failed rollback of block {} in {}", pos, dimension, exception);
                }
            });
        }
    }
}
