# Rollback Mod 优化说明（CHANGES）

本文件说明按需求对开源模组 Rollback Mod（作者 HanMoyun）所做的全部优化，以及多平台移植情况。

## 第三十二轮：工程标准化 + 冒烟测试

### 标准化（三个工程一致）

- 补齐标准 Gradle Wrapper：`gradlew` / `gradlew.bat`，并重新生成**配套**的 `gradle-wrapper.jar`（带 Main-Class），
  Windows 上 `gradlew.bat build` 可直接使用。
- 仓库规范文件：`.gitignore`、`.gitattributes`、`.github/workflows/build.yml`（CI，按平台用对应 JDK）。
- 修复开箱即崩的问题：NeoForge/Fabric 的 `build.gradle` 之前**无默认 MC 版本**，不带 `-Pmc` 时
  `src/versioned/null/java` 不存在 → 编译失败。现改为默认 `1.21.1`（Forge 原本默认 `1.20.1`），`gradlew build` 可直接构建。
- 清理 NeoForge 根目录多余的 `gradle-wrapper.jar`。
- 验证：三平台 `gradlew.bat build`（不带参数）均 BUILD SUCCESSFUL。

### 冒烟测试（模组实际跑起来）

- **Forge 1.20.1**：`runGameTestServer` → **12 个 GameTest 全部通过**（`All 12 required tests passed`）。
- **NeoForge 1.21.1**：`runServer` → 模组加载（`Rollback Mod 1.1.0`）、专用服务器正常起服（`Done (1.439s)`）。
- **Fabric 1.21.1**：`runServer` → 模组加载（`rollbackmod 1.1.0`）、专用服务器正常起服（`Done (1.837s)`）。

### 由冒烟测试发现并修复

- NeoForge 专用服务器上出现 `RuntimeDistCleaner ... Attempted to load class net/minecraft/client/Minecraft for invalid dist DEDICATED_SERVER`：
  原因是 `isClientDist()` 用 `Class.forName("net.minecraft.client.Minecraft")` 探测客户端，会加载客户端类。
  已改为反射读 loader 的 `FMLEnvironment`（兼容两代 API：1.21–1.21.8 为 `dist` 字段，1.21.9+ 为 `getDist()` 方法）。
- 冒烟测试 1.21.11 时发现**开发依赖 cloth-config 写死为 15.0.140**（只适配 1.21/1.21.1），
  在新版 loader 上因 `FMLEnvironment.dist` 字段被移除而崩溃（`NoSuchFieldError`），把专用服务器带崩。
  已改为按 MC 版本区分：在 `versions/<版本>.properties` 新增 `cloth_version`，`build.gradle` 引用之：
  1.21/1.21.1→15.0.140，1.21.2/1.21.3→16.0.143，1.21.4→17.0.144，1.21.5→18.0.145，
  1.21.6–1.21.8→19.0.147，1.21.9/1.21.10→20.0.149，1.21.11→21.11.153（NeoForge 与 Fabric 同）。
- 屏蔽以上干扰后，1.21.11 暴露出**我们模组自身的真实运行时 bug**：注册物品时报
  `NullPointerException: Item id not set`。经查 NeoForge **自 21.2（1.21.2）起**要求
  `Item.Properties.setId(ResourceKey<Item>)`（否则 `Objects.requireNonNull(id, "Item id not set")` 抛错），
  而本模组一直用 `DeferredRegister.register(name, Supplier)` 未设 id。**编译期查不出，只在运行时炸**。
  已修复：将 `ModItems` 按版本隔离（`src/versioned/<版本>/`）：1.21/1.21.1 保持原样；
  1.21.2–1.21.11 改用 `register(name, key -> ...)` 并 `.setId(key)`。
- **（Fabric）同样存在 `setId` 问题**：实测 Fabric 1.21.11 也会报 `Item id not set`（原版行为，非 NeoForge 专属）。
  已将 Fabric `ModItems` 1.21.2–1.21.11 改为工厂式注册并 `.setId(...)`（1.21.2 编译验证 `Item.Properties.setId` 存在）。
- **（Fabric）伤害 Mixin 目标失效**：`LivingEntityMixin` 拦 `LivingEntity#hurt`，但原版 **1.21.2 起**
  该方法更名为 `hurtServer(ServerLevel,DamageSource,float)`（1.21.2 前为 `hurt(DamageSource,float)`）。
  因 mixin `required`，Fabric **1.21.2–1.21.11 运行时会在启动阶段崩**。
  已按版本分叉 `LivingEntityMixin`：1.21/1.21.1 仍拦 `hurt`；1.21.2–1.21.11 改拦 `hurtServer`。
- 项目 `README.md` 已同步更新（大版本范围、`gradlew` + `-Pmc` 构建、按版本 cloth、Fabric 的 `hurtServer`），
  并给每个工程补上自包含的 `CHANGES.md`。

### 最终冒烟测试结果

| 平台 | 测试 | 结果 |
| --- | --- | --- |
| Forge 1.20.1 | `runGameTestServer`（12 个 GameTest） | 全部通过 |
| NeoForge 1.21.1 / 1.21.5 / 1.21.11 | `runServer` | 模组加载 + 正常起服，无报错 |
| Fabric 1.21.1 / 1.21.5 / 1.21.11 | `runServer` | 模组加载 + 正常起服，无报错 |

说明：采取“边界 + 中间”抽样（大版本首尾与中间各一个）。其余小版本未逐一进游戏实测，
但使用同一套按版本隔离的代码（版本间差异已在编译期验证）。

## 第三十一轮：复查修复（许可证 / cloth 依赖 / 残留清理 / 源码包）

复查 30 个 jar 与 3 个源码包后发现并修复：

- **许可证统一为 GPL-3.0**：原先 jar 元数据为 `All Rights Reserved`，却与 Forge 工程里的 GPL v3 `LICENSE` 自相矛盾，且 NeoForge/Fabric 缺 `LICENSE`。
  现三个工程的 `mod_license` / `fabric.mod.json license` 统一为 `GPL-3.0`，并给 NeoForge/Fabric 补上 `LICENSE`（GPL v3 全文）。
- **cloth-config 统一为“可选 + isLoaded 守卫”**（三平台一致）：
  - Forge：配置界面工厂内 `ModList.get().isLoaded("cloth_config")` 判断，未装 cloth 时返回父界面（不再触发 `ClothConfigScreen` 的 `NoClassDefFoundError`）；
  - NeoForge：同上，用 `net.neoforged.fml.ModList`；
  - Fabric：`fabric.mod.json` 把 `cloth-config` 从 `depends` 移到 `suggests`；`ModMenuIntegration` 在 `FabricLoader.isModLoaded("cloth-config")` 为假时返回空工厂。
- **清理 Forge 残留 `1.20.5` 版本树**（该版本官方未发布；之前 6 个版本却对应 7 个树），
  已从工程移除（暂存于 `.cowork-temp/removed-1.20.5`，可恢复）。
- **修复 NeoForge 源码包缺失 `gradle-wrapper.properties`**（否则开源后无法用 wrapper 构建），已重新打包。
- 描述文本里的子版本号（如“for Forge 1.20.1”）改为大版本线（“1.20.x / 1.21.x”），避免与实际支持范围不符。
- 上述改动涉及主代码/元数据 → **三平台全量重新构建并刷新交付物**：Forge 6 + NeoForge 12 + Fabric 12 = 30 个 jar 全部 BUILD SUCCESSFUL（含 Forge 1.20.6 需用 JDK 21）。
- 三个源码包已重新打包：核心文件（build.gradle / LICENSE / gradle-wrapper.jar / gradle-wrapper.properties）齐全，Forge 不再含 1.20.5 残留。
- 校验：三类 jar 元数据已确认为 `license = GPL-3.0`；Fabric 的 `cloth-config` 已从 `depends` 移到 `suggests`。

## 第三十一轮补充：构建注意

- Forge 1.20.6 的工具链为 Java 21（其余 Forge 1.20–1.20.4 为 Java 17）；用 JDK 17 构建 1.20.6 会报 `Cannot find a Java installation ... languageVersion=21`。

## 第三十轮：Fabric 1.21.5–1.21.11 完成 + 全版本工具链统一

- 构建工具链升级：Gradle `8.12 → 8.14`、`fabric-loom 1.10.1 → 1.11.7 → 1.13.6`
  （1.21.5+ 的 Fabric API 用新 Loom 构建，旧版被拒；1.21.11 的 API 元数据又要求更高的 Loom）。
- 代码层沿用与 NeoForge 同源的适配（两者都是官方 Mojang 映射，类名一致，加载器无关的类可直接复用）：
  - 1.21.5：`Inventory.getNonEquipmentItems()/getSelectedSlot()/setSelectedSlot()` 替代私有的 `items/selected/armor/offhand`；
    NBT 取值改 Optional（`getXOr`、`getListOrEmpty`、`getCompoundOrEmpty`、`keySet()`、`store/read` + Codec）；
    `SavedData.Factory` → `SavedDataType`+`SavedDataAccess`；`appendHoverText(...,TooltipDisplay,Consumer,flag)`；
  - 1.21.6：`ValueInput/ValueOutput`（`TagValueInput/TagValueOutput`）、`Matrix3x2fStack`（`pushMatrix`）、
    `kill((ServerLevel) player.level())`、`Inventory.save/load` 改直接拷物品；
  - 1.21.9：`player.getServer()`/`victim.getServer()` 移除 → `player.level().getServer()`；
  - 1.21.11：`ResourceLocation`→`Identifier`、`net.minecraft.Util`→`net.minecraft.util.Util`、`ResourceKey.location()`→`identifier()`、
    `EntityProcessor.NOP`、原版 `SavedDataType(String, Supplier, Codec, DataFixTypes)`（无 context，Fabric 端把 registry 闭包进 codec）。
- 为让“一份源码覆盖整个大版本线”，把所有会随版本变化的类都做了**按版本隔离**（`src/versioned/<版本>/java`），
  main 中不再保留冲突副本（`HiveManager/RollbackManager/ClientDayHud/ModItems/ModCreativeTabs/DayInfoPacket` 等均已分叉）。
- **Fabric 全部 12 个版本构建成功：1.21、1.21.1、…、1.21.11**（产物入 `deliverables/fabric/`）；全量回归 12/12 通过。

## 第二十九轮：Fabric 构建工具链升级（Loom 1.9.2 → 1.10.1）

- 1.21.5+ 的 Fabric API 由较新 Loom 构建，旧 Loom 1.9.2 直接拒绝（`Mod was built with a newer version of Loom (1.10.1)`）→ 升级为 **1.10.1**；
  已复测 1.21.4 仍 BUILD SUCCESSFUL（未被带坏）。
- Fabric 已完成：1.21、1.21.1、1.21.2、1.21.3、1.21.4。
- Fabric 1.21.5+ 需与 NeoForge 同源的 NBT Optional / `SavedDataType`+Codec / Inventory 访问器重写（下一轮）。

## 第二十八轮：Fabric 1.21.2–1.21.4 完成（同源适配）

- Fabric 复用 NeoForge 的适配经验：`versions/<版本>.properties` 写 yarn/fabric-api（已批量获取 1.21.2–1.21.11）；
  1.21.2+ 的物品使用 API（`InteractionResult`/`ItemUseAnimation`）、`EntitySpawnReason`、`kill(level)`、
  `teleportTo(...,boolean)`、`getMinSectionY`、`readBlockState(provider)` 已按版本覆盖。
- **Fabric 已完成：1.21、1.21.1、1.21.2、1.21.3、1.21.4（5 份）**。
- 1.21.5+ 需与 NeoForge 同源的 NBT Optional/`SavedDataType`+Codec 重写（构建中）。

## 第二十七轮：NeoForge 全版本完成（1.21–1.21.11）

- 升级构建插件：`net.neoforged.moddev 2.0.78 → 2.0.148`（旧版 NeoForm 处理不了 1.21.9+ 的 jar）。
- 1.21.9–1.21.11 的适配：`ServerPlayer.getServer()/serverLevel()` 移除 → `player.level()`；
  `FMLLoader.getDist()` 变动 → 改用类存在性判断客户端；`EntityProcessor.NOP` 代替 `Function.identity()`（1.21.11）；
  1.21.11 把 `ResourceLocation`→`Identifier`、`net.minecraft.Util`→`net.minecraft.util.Util`、`ResourceKey.location()`→`identifier()`；
  `SavedData.Context` 在 1.21.11 直接是 `ServerLevel`（`context.registryAccess()`）。
- **NeoForge 全部 12 个版本均已构建成功：1.21、1.21.1、…、1.21.11**（产物已入 `deliverables/neoforge/`）。
- 全量回归已完成：12/12 均 BUILD SUCCESSFUL（重新校验后确认）。

## 第二十六轮：NeoForge 1.21.6–1.21.8 完成；1.21.9+ 卡在构建工具链

- **完成 1.21.6 / 1.21.7 / 1.21.8**（BUILD SUCCESSFUL）。1.21.6 的适配：
  - 新增序列化 API `ValueInput`/`ValueOutput`：实体/玩家快照改用 `TagValueOutput.createWithContext(...).buildResult()` 与 `TagValueInput.create(...)`；
  - `ItemStack.save(Provider)` 没了 → 用 `ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, provider), stack)`；
  - `ServerPlayer.serverLevel()` 移除 → `player.level()`；
  - HUD 的 `GuiGraphics.pose()` 改为 `Matrix3x2fStack`（`pushPose→pushMatrix`、`scale(3,3,1)→scale(3,3)`）；
  - `Inventory.save/load(ListTag)` → 改为直接拷物品（`getNonEquipmentItems` + `setItem`）。
- **1.21.9 / 1.21.10 / 1.21.11 卡在构建工具链**（非代码）：NeoForm `inject` 报
  `java.util.zip.ZipException: STORED entry missing size, compressed size, or crc-32`，
  是因为当前 `net.neoforged.moddev 2.0.78` 太旧、处理不了新版本 MC 的 jar。
  → 需升级 moddev（会牵扯所有版本，我会先升级再全量回归）。

**NeoForge 已完成：1.21–1.21.8（9 份）**。

## 第二十五轮：NeoForge 1.21.5 完成；1.21.6 又遇新一轮序列化大改

- **完成 1.21.5**（BUILD SUCCESSFUL）：NBT Optional 化、`SavedData`→`SavedDataType`+Codec、
  `CompoundTag.store/read`+`UUIDUtil.CODEC`、`BlockPos.CODEC`、`ItemStack.CODEC`、`TooltipDisplay`、
  `Inventory` 访问器、ListTag `getXOr` 等全部改完。
- **1.21.6 引入另一套新序列化 API**（`ValueInput`/`ValueOutput`）：`saveWithoutId`/`saveAsPassenger`
  要 `ValueOutput`；`Inventory.load` 要 `TypedInputList<ItemStackWithSlot>`；`FoodData.readAdditionalSaveData` 要 `ValueInput`；
  `PoseStack.scale` 也变了。→ 1.21.6/1.21.7/1.21.8 均卡在这里。
- **结论（回答“1.21.6 是否是唯一瓶颈”）**：不是。至少有 **两个不同的硬坎**：1.21.5（NBT/SavedData 大改）
  与 1.21.6（ValueInput/ValueOutput 大改）；1.21.9–1.21.11 很可能还有。Fabric 也会碰上同一批原版改动。
- NeoForge 已完成：1.21、1.21.1、1.21.2、1.21.3、1.21.4、**1.21.5**。

## 第二十四轮：NeoForge 1.21.5+ 持久化/NBT 重写（进行中）

1.21.5 起的原版改动面积较大：
- **持久化**：`SavedData` 重构为 `SavedDataType` + `Codec` → 已改为用 `CompoundTag.CODEC` 直存（复用现有 NBT 代码）。
- **NBT 读取 Optional 化**：`getInt/getLong/getBoolean/getString/getFloat/getShort` → `getXOr(...)`；
  `getCompound` → `getCompoundOrEmpty`；`getList` → `getListOrEmpty`；`getAllKeys` → `keySet`；`contains(key,type)` 移除。
- **物品**：`appendHoverText(..., TooltipDisplay, Consumer<Component>, TooltipFlag)`。
- 仍需处理：`CompoundTag.putUUID/getUUID` 移除（改用 `UUIDUtil.CODEC`）、`MobEffectInstance.load(...)` 签名变化。

状态：1.21.5 未完成（已推进大半）；1.21.6–1.21.11 沿用同一套适配后再逐个过。NeoForge 已完成 1.21–1.21.4。

## 第二十三轮：NeoForge 1.21.2–1.21.4 完成；1.21.5+ 遇更大改动

- **完成**：NeoForge `1.21`、`1.21.1`、`1.21.2`、`1.21.3`、`1.21.4`（均 BUILD SUCCESSFUL，产物已入 `deliverables/neoforge/`）。
- 1.21.2–1.21.4 的适配：物品使用 API（`InteractionResult`/`ItemUseAnimation`）、`EntitySpawnReason`、
  `kill(ServerLevel)`、`teleportTo(...,boolean)`、`getMinSectionY`、`readBlockState(provider)`、爆炸事件去掉方块列表。
- **1.21.5+ 遇到更大的原版改动，需专门重写**：
  - `CompoundTag.getList(String)` 改返回 `Optional<ListTag>`（另有 `getListOrEmpty`）；`contains(key,type)` 移除；
  - `Inventory.items/selected/armor/offhand` 全部私有化（改 `getNonEquipmentItems/getSelectedSlot/setItemSlot`）；
  - **`SavedData` 重构为 `SavedDataType` + `Codec`**（持久化层需用 Codec 重写，或改为自管 NBT 文件）。
- `1.21.9` / `1.21.10` / `1.21.11` 尚未尝试。

## 第二十二轮：NeoForge 1.21.2 移植评估

NeoForge 1.21.2（21.2.1-beta）试编失败，属真实 API 变动，需逐项适配：
- **物品使用 API 重写**：`InteractionResultHolder` / `UseAnim` 已移除 → 改用 `InteractionResult` / `ItemUseAnimation`（`InhalerItem` 的 use/useAnimation 全部重写）；
- `EntityType.loadEntityRecursive` 新增 `EntitySpawnReason` 参数；
- `LivingEntity.kill` 需 `ServerLevel` 参数；
- `ExplosionEvent.Detonate.getAffectedBlocks()` 变化；
- `Entity.teleportTo(...)` 新增 `Set<Relative>` 参数；
- `NbtUtils.readBlockState(...)` 签名变化；`ChunkAccess.getMinSection()` 变化。

结论：NeoForge 1.21.2–1.21.11 需逐版本移植（与 Forge 1.20.6 同级甚至更多），将按版本分批推进。

## 第二十一轮：Forge 1.20.6 完成（Forge 大版本收尾）

- 补齐 Forge 1.20.6：
  - `SavedData`/`BlockEntity`/`NBT` 改为带 `HolderLookup.Provider` 签名（`save(CompoundTag,Provider)`、
    `saveWithFullMetadata(Provider)`、`loadStatic(...,Provider)`、`readBlockPos(tag,key)`）；
  - `InhalerItem` 物品 NBT → 数据组件（`DataComponents.CONTAINER` 存药芯）；`appendHoverText` 签名 Level→TooltipContext；
  - 存读档/物品类改为按版本提供（`src/versioned/<版本>/java`），避免与旧版本冲突。
- **Forge 最终产物：1.20、1.20.1、1.20.2、1.20.3、1.20.4、1.20.6**（全部 BUILD SUCCESSFUL；1.20.5 官方未发布，跳过）。
- 下一步：NeoForge / Fabric 1.21.2–1.21.11（12×2，均已确认有发布）。

## 第二十轮：版本可用性核对 + Forge 收尾

- **Forge**：已完成 1.20–1.20.4（源码/产物均刷新）；1.20.5 官方**未发布**（maven 无 `1.20.5-*`）；
  1.20.6 需 Provider/NBT 层移植（已推进一半，按需求暂停）。
  附：Forge 的 1.21.2 也未发布（promotions 从 1.21.1 直接到 1.21.3）。
- **NeoForge**：1.21–1.21.11 全部有发布（NeoForge 21.0–21.11），**无跳版**。
- **Fabric**：1.21–1.21.11 全部存在，**无跳版**。
- 结论：NeoForge / Fabric 各 12 个版本（1.21–1.21.11）均可做。

## 第十九轮：Forge 1.20.5 / 1.20.6 续工评估

已解决：
- Forge 1.20.5+ 不再有 `reobfJar`（运行改用官方映射）→ `build.gradle` 改为按需绑定；
- 工具链按目标版本切换（1.20.5/1.20.6 需 Java 21）；
- HUD 注册按版本拆分（1.20.5+ 用 `AddGuiOverlayLayersEvent` + `ForgeLayeredDraw`）。

剩余（较大，下一轮）：1.20.5+ 的存读档/NBT 全部改为带 `HolderLookup.Provider` 签名：
`save(CompoundTag,Provider)`、`saveWithFullMetadata(Provider)`、`loadStatic(...,Provider)`、`readBlockPos(tag,key)`；
需版本化 `RollbackSavedData` / `RollbackRestoreQueue` / `RollbackManager`（吸入器已无耐久，耐久消耗可空实现）。
另外：Forge 可能未发布 1.20.5（promotions 只有 1.20.6），待确认。

## 第十八轮：多版本移植推进（Forge 1.20–1.20.4 完成）

- 新增“按版本覆盖”结构：`src/versioned/<版本>/java`（网络层 / 存档访问层）+ `versions/<版本>.properties`；
  `-Pmc=<版本>` 选择目标；GameTest 仅主版本(1.20.1)编译。
- **Forge 已完成：1.20、1.20.1、1.20.2、1.20.3、1.20.4**（各一份 jar，全部 BUILD SUCCESSFUL）：
  - 1.20.2+ 联网 API 重写：`ChannelBuilder` + `SimpleChannel` + `CustomPayloadEvent.Context`；
  - `SavedData` 工厂签名差异抽到 `SavedDataAccess` 按版本实现；
  - 发送接口抽象为 `ModNetworking.sendToPlayer/sendToAll`；
  - `java.toolchain` 按目标版本切换（1.20.5/1.20.6 需 Java 21）。
- 未完成：Forge 1.20.5 / 1.20.6（需升级 ForgeGradle 以支持 Forge 50.x，且要物品组件化改造）；
  NeoForge / Fabric 1.21.2–1.21.11。
- 交付目录按加载器分：`deliverables/forge`、`neoforge`、`fabric`；源码命名 `rollbackmod-<大版本>-<加载器>-source.zip`。

## 第十七轮：多版本移植评估（1.20.2+ / 1.21.2+ 需真实移植）

实测：Forge 1.20.2 直接编译失败——Forge 在该版本**重写了联网 API**
（`net.minecraftforge.network.simple.SimpleChannel` / `NetworkEvent` 已移除，改为 `RegisterPayloadHandlersEvent` + `CustomPayload`），
GameTest 相关符号也有变动。据此评估后续版本工作量：
- Forge 1.20.2 / 1.20.3 / 1.20.4：需重写联网层（Forge 新 payload API）+ 修 GameTest 引用；
- Forge 1.20.5 / 1.20.6：在上一项基础上，还需把物品 NBT 改成数据组件（`getOrCreateTag` 已移除）、
  `BlockEntity.loadStatic` / `saveWithFullMetadata` 签名变化；
- NeoForge / Fabric 1.21.2–1.21.11：每个小版本均有原版/加载器 API 变动，需逐版本适配。

结论：25 个版本需要**分批逐版本移植**（每个版本：下载工具链 + 改代码 + 编译调试），无法一轮完成。

## 第十六轮：多版本工程（一套源码 → 每个小版本一份 jar）

背景：需求要求 Forge 覆盖 1.20–1.20.6、NeoForge/Fabric 覆盖 1.21–1.21.11，
每个小版本各一份 jar，但仓库仍只保留 forge / neoforge / fabric 三个。

做法（每个加载器一个仓库、一套源码）：
- `build.gradle` 支持 `-Pmc=<游戏版本>`，从 `versions/<版本>.properties` 读取该版本的差异；
- 版本专用代码可放 `src/versioned/<版本>/java`（存在时自动纳入）；
- 仍可用默认（不带 `-Pmc`）构建。

已产出（相邻小版本，源码无差异）：
- Forge：`rollbackmod-1.1.0-1.20-forge.jar`、`-1.20.1-forge.jar`；
- NeoForge：`-1.21-neoforge.jar`、`-1.21.1-neoforge.jar`；
- Fabric：`-1.21-fabric.jar`、`-1.21.1-fabric.jar`。

待办：1.20.2–1.20.6、1.21.2–1.21.11 需逐版本移植（API 变动大），将分批推进。

## 第十五轮：放宽声明兼容范围（三平台同步）

按需求把“声明范围”扩到整条小版本线（只改声明，不改逻辑、不改游戏内行为）：

- Forge：`minecraft_version_range=[1.20,1.21)`、`forge_version_range=[46,)`、`loaderVersion=[46,)`（覆盖 1.20–1.20.x 的 Forge）；
- NeoForge：`minecraft_version_range=[1.21,1.22)`、`neo_version_range=[21,)`（覆盖 1.21–1.21.x 的 NeoForge）；
- Fabric：`depends.minecraft=">=1.21 <1.22"`（其余 loader/API 保持宽松）。

⚠️ 注意：这是**声明层面**的放宽。真正跨版本运行仍需逐版本移植（1.20.2+/1.20.5+、1.21.2+ 的原版/加载器 API 变动较大）；
`pack.mcmeta` 的 pack_format 仍按构建版本（1.20.1 / 1.21.1）。

回归：Forge `runGameTestServer` 全部 12 个用例通过；Fabric / NeoForge 专服启动正常；三平台产物与源码包已刷新。

## 第十四轮：回溯写回方块改用“读档式”标志（三平台同步）

回溯写回方块原用 flag 3（含邻接更新），恢复两格方块（床/门）时可能因邻接更新让只剩一半的方块掉落。
改为 `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`（与区块加载一致：只更新客户端、不做邻接/形状更新），
与内存全量快照的回写标志统一，避免连锁反应与掉落。

Forge `runGameTestServer` 全部 12 个用例通过；Fabric / NeoForge 专服启动正常；三平台产物已刷新。

## 第十三轮：内存「全量方块快照」，真正覆盖存档（三平台同步）

### 背景
第十二轮虽把三平台方块改动都挂到了 `Level.setBlock`，但仍依赖“改动清单”（逐次记录），
跨会话/极端情况仍可能漏。本轮改为：**存档点创建时把已加载区块的方块状态整体拷一份到内存**，
回溯时逐块 diff 覆盖。

### 实现
- 新增 `BlockSnapshotStore`：存档点创建时，对每个维度**已加载区块**按 section 复制紧凑的方块调色板
  （`LevelChunkSection.getStates().copy()`）；回溯时逐块比较，只把与快照不同的方块写回
  （`UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`，不触发邻接连锁反应）。
- `BlockRollbackManager` 新增 `captureFullSnapshot / restoreFullSnapshot / clearFullSnapshot`；
  `RollbackManager` 在创建存档点时抓快照、回溯时先按快照覆盖、删除存档点时清空。
- 纯内存、不写盘、不动 save 文件；上限 8192 区块；“改动清单”保留作为非快照区块的兜底。

### 测试与验证 ✅
- Forge `runGameTestServer`：新增「改动完全不被记录、仅靠全量快照还原」用例，**All 12 required tests passed**；
- Fabric / NeoForge 专用服务器正常启动（Done 1.635s / 1.235s）；
- 三平台 `build` 全部 BUILD SUCCESSFUL，产物与源码包已刷新。

## 第十二轮：三平台彻底对齐「覆盖存档」（方块改动全量追踪）（三平台同步）

### 背景
方块此前靠事件/回调记录改动：Fabric 用 Mixin 钩住 `Level.setBlock`（覆盖全），
Forge / NeoForge 只用事件（BreakEvent/EntityPlaceEvent/…），活塞推动、沙砾下落、火焰蔓延、
树叶凋零、作物生长、流体流动等**不经过事件**的改动不会被记录。

### 变更
- Forge 1.20.1：接入 Mixin（MixinGradle 0.7.38 + mixin 0.8.5 AP，生成 refmap，`MixinConfigs` 写入 jar 清单），
  新增 `LevelMixin` 钩住 `setBlock / destroyBlock / removeBlock`；
- NeoForge 1.21.1：`neoforge.mods.toml` 声明 `[[mixins]]`，新增 `LevelMixin`（官方映射，无需 refmap）；
- Fabric 1.21.1：已有同等 `LevelMixin`，保持不变；
- 三平台现在都在 `Level.setBlock` 处记录“改动前的方块”，任何方块改动都会被回溯覆盖。
  世界生成走 ProtoChunk/WorldGenRegion，不经过 Level.setBlock，不会被误记录。

### 测试与验证 ✅
- Forge `runGameTestServer`：新增「非事件路径 setBlock 改动也能回溯」用例，**All 11 required tests passed**；
- Forge 产物含 `rollbackmod.refmap.json`，映射到 SRG（`m_7731_` 等），保证正式环境生效；
- NeoForge 专用服务器正常启动（Mixin JAVA_21，无报错，Done 1.194s）；
- 三平台 `build` 全部 BUILD SUCCESSFUL，产物与源码包已刷新。

## 第十一轮：回溯完整性修复（两格方块 + 单点失败不再中断整次回溯）（三平台同步）

### 现象（实测日志）
1. 回溯时床“掉在地上”（只恢复了一半）；
2. 有一部分改动没能回溯成功。

### 根因
1. **坏记录崩溃并中断整次回溯**：放置床等方块时事件给出的“旧方块”是空气，
   但记录里却附带了**新方块的方块实体**（床）；回溯时按空气状态创建床方块实体，
   抛 `ClassCastException: AirBlock cannot be cast to BedBlock`，异常从
   `restoreChangedBlocks` 一路抛出，**中止了整次回溯**，所以后面很多东西都没恢复。
2. **两格方块只记一半**：破坏床/门/高花高草只对其中一格触发事件，另一半被连带移除却无人记录，
   回溯后只剩半张床（无效方块 → 掉落成物品）。

### 修复
1. `BlockSnapshotRecord.capture`：仅当记录状态就是该位置当前方块时才附带方块实体，避免“新方块实体配旧状态”的坏记录。
2. `BlockSnapshotRecord.restore` / `RollbackRestoreQueue`：写回方块实体前校验 `state.hasBlockEntity()` 并 try/catch，
   **单个方块恢复失败不再中断整次回溯**。
3. `BlockRollbackManager.restoreChangedBlocks`：逐条 try/catch，跳过失败项继续。
4. 新增 `BlockRollbackManager.rememberBlockAndConnected`：破坏两格方块时把另一半一并记录（床 / 门 / 双格植物）；
   Forge / NeoForge 的 `BreakEvent` 改用它。

### 测试与验证 ✅
- Forge `runGameTestServer`：新增「床两半完整回溯」「坏记录不中断回溯」两个用例，**All 10 required tests passed**；
- Fabric / NeoForge 专用服务器正常启动（Done 2.023s / 1.500s）；
- 三平台 `build` 全部 BUILD SUCCESSFUL，产物与源码包已刷新。

## 第十轮：吸入器改为无限耐久（三平台同步）

### 变更
吸入器不再有耐久上限（移除 `durability(250)`，物品无最大耐久、永不损坏）；
蜕皮回溯不再消耗耐久，可无限次使用。药芯消耗等其余机制不变。

### 构建与验证 ✅
- 三平台 `build` 全部 **BUILD SUCCESSFUL**；
- Forge `runGameTestServer`：**All 8 required tests passed**（蜕皮用例改为断言“耐久不消耗”）；
- 产物与源码包已刷新到 `deliverables/`。

## 第九轮：NeoForge 专用服务器加载崩溃修复（三平台回归测试）

### 现象
在 NeoForge 1.21.1 上以**专用服务器（dedicated server，DEDICATED_SERVER dist）**启动时，
模组构造阶段即崩溃，无法进入世界：

```
java.lang.RuntimeException: Attempted to load class net/minecraft/client/gui/screens/Screen
  for invalid dist DEDICATED_SERVER
  at com.taobao.koi.rollbackmod.RollbackMod.<init>(RollbackMod.java:37)
```

### 根因
`RollbackMod` 构造函数中**无条件**注册了 Cloth Config 设置界面扩展点
`registerExtensionPoint(IConfigScreenFactory.class, ...)`。该函数式接口的方法签名包含
仅客户端存在的 `net.minecraft.client.gui.screens.Screen`；服务器端 `RuntimeDistCleaner`
在解析该 invokedynamic/接口时判定 dist 非法并抛错，导致模组加载失败。
Forge 端同类逻辑用 `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)` 隔离，未受影响；
Fabric 端没有这条扩展点路径，同样未受影响。

### 修复
把设置界面扩展点注册移入 `if (FMLLoader.getDist().isClient())` 分支，与 `ClientDayHud`
注册放在一起；服务器端不再解析任何客户端类。

### 测试与验证 ✅
- 三平台 `build` 全部 **BUILD SUCCESSFUL**；
- Forge 1.20.1 `runGameTestServer`：**All 2 required tests passed**；
- Fabric 1.21.1 专用服务器：模组正常加载，**Done (2.400s)**；
- NeoForge 1.21.1 专用服务器：修复前加载崩溃，修复后模组正常加载，**Done (1.598s)**；
- 产物与 NeoForge 源码包已刷新到 `deliverables/`。

### 补充：新增玩法级“游戏内”GameTest（Forge）
在真实服务端 GameTest 环境中新增 6 个玩法用例，覆盖：化茧存档、蜕皮回溯
（位置/血量/背包/耐久/消耗药芯）、致命伤害回溯、死亡事件回溯、方块改动回溯、天数换算。
`runGameTestServer` 现共 **8 个用例全部通过**（All 8 required tests passed）。
源码见 `Rollback-Mod-main/src/main/java/.../gameTest/RollbackGameplayTests.java`，
调试用的空结构模板 `run/gameteststructures/rollbackgameplaytests.empty.snbt` 已就位。

## 第八轮：滚动特效「上一个数字不消失」修复（三平台同步）

### 现象
回溯时画面上同时存在两个数字：新数字已经就位（如「第 2 天」），**上一个数字仍停在原位不消失**
（如旧数字「1」留在下方）。

### 根因（两点）
1. **动画被重复下发的同一转场不断重置**：服务端在一次回溯/跨天里可能多次下发
   `playAnimation = true` 的同步包，客户端每次都重新 `beginTransition`，
   把起始时间刷成“现在”，于是 `elapsed` 永远≈0 —— 画面被永久冻结在**起始帧**：
   旧数字还在基准位置、新数字还在旁边一格。这正是截图里的状态。
2. **旧数字的淡出安排太保守**：旧数字整段滚动（1.8s）里都在按 `alpha × (1 - scrollT)` 淡出，
   在滑动过程中长时间可见，视觉上像“没消失”。

### 修复
1. **忽略重复的同一转场**：记录最近一次真正播放过的 `(from, to)` 与时间，
   在整段黑屏时长内重复到达的**同一对数字**不再重启动画（换目标数字仍会正常播放新的转场）。
2. **旧数字提前退场**：只在前 **55%** 的滚动时间里滚走并淡出，之后**彻底不再绘制**；
   滚动时长 1.8s → 1.5s；旧数字淡出用独立进度 `oldT`，透明度归零即停止绘制（避免残留幽灵字）。
3. 新数字仍按「大数自上而下、小数自下而上」在剩余时间里滚入就位。

### 构建与验证 ✅
- Forge / NeoForge / Fabric 三平台 `build` 全部 **BUILD SUCCESSFUL**；
- Forge `runGameTestServer`：**All 2 required tests passed :)**；
- 产物、源码包与文档已刷新到 `deliverables/`。

## 第七轮：滚动方向、联动分类与死亡回溯修复（三平台同步）

### 1. 修复：死亡不触发回溯（回归）
- 根因：第六轮为「创造模式免疫」加的守卫范围过大——
  死亡回溯与致命伤害回溯被 `rollbackAllPlayersOnDeath` **二次拦截**，且
  `getAbilities().instabuild` 检查放在伤害/死亡路径最前面，导致正常生存玩家
  一旦该配置被改动（或状态读取时机不巧）就整条路径直接 return。
- 修复：死亡/致命伤害两条路径只以 `enable_death_rollback` + 存档点存在为条件；
  创造模式免疫改由「共享伤害不参与」承担（创造玩家本来就不吃伤害），
  不再阻断普通玩家的死亡回溯。（Fabric 的 `ALLOW_DEATH` 与 Mixin 路径同样修正。）

### 2. 数字滚动方向改为按数值大小判定
- 规则：**大数从上往下滚入，小数从下往上滚入**（旧数字反向滚出）。
  - 例：第 3 天 → 第 4 天：**4 自上而下**滚入；第 3 天 → 第 2 天：**2 自下而上**滚入。
  - 倒计时「还剩 X 天」同理：4 → 5 时 5 自上而下，4 → 3 时 3 自下而上。
- 实现：方向不再看「前进/回溯」，而是比较两端数字大小；
  且比较的是**屏幕上显示的数字**（倒计时模式下是剩余天数），
  `ClientDayHud` 因此改存 `transitionFromNumber/transitionToNumber`。

### 3. 网络包补充两端数字
- `DayInfoPacket` 新增 `fromNumber` / `toNumber`（仅在 `playAnimation` 时写入，
  向后兼容）：服务端在跨天/回溯时算好两端数字并下发，
  客户端不再自行推断，回溯到同一天时也能正确判定方向。

### 4. 配置「联动」分类标题简化
- 分类标题只写 **「联动」**（英文 `Integration`），选项名称与默认值保持不变
  （`dontgethurt_integration` 默认开）。

### 5. 构建与验证 ✅
- Forge / NeoForge / Fabric 三平台 `build` 全部 **BUILD SUCCESSFUL**；
- Forge `runGameTestServer`：**All 2 required tests passed :)**；
- 产物、源码包与文档已刷新到 `deliverables/`。

## 第六轮：体验与完整性修复（三平台同步）

### 1. 修复：创造模式也会摔死
- 根因：多人“共享伤害”把任一玩家的伤害以魔法伤害源重定向给**全体玩家**，
  创造模式玩家也会被这份共享伤害命中、累积到死亡。
- 修复：创造模式（`instabuild`）玩家**既不产生共享伤害、也不受共享伤害影响**，
  并且跳过致命伤害回溯与死亡回溯（三平台的伤害/死亡路径均已加创造豁免）。

### 2. 回溯更完整：存档世界数据分帧加载
- 根因：回溯时一次性同步恢复全部实体/方块实体；区块一旦卸载，
  `getBlockState` 返回空气、方块实体（箱子内容、模组机器等）被**静默丢弃**，导致回溯不完整。
- 修复：新增 `RollbackRestoreQueue`——回溯时先同步恢复维度时间/天气、清空非玩家实体、
  恢复玩家与方块改动，**实体与方块实体的恢复改为每 tick 处理一小批**，
  处理方块实体时强制加载其所在区块，确保完整写回；新存档点/删除存档点会清空待处理队列。

### 3. 配置新增 dontgethurt 联动（单独分类）
- Cloth Config 新增独立分类“dontgethurt 联动”，内含开关 `dontgethurt_integration`（默认开）：
  开启时多人共享伤害走魔法伤害源、交由 dontgethurt 的部位损伤系统接管；关闭时用普通伤害源。
- `sync_inventory_and_health` 的说明不再重复 dontgethurt 相关内容。

### 4. 清理只剩三种药芯后的冗余
- 移除已无引用的 `ModItems.isRollbackModItem`；网络包去掉不再需要的 `rollback` 标志
  （`DayInfoPacket` 回到 5 字段），`DayCounterManager` 相应简化。

### 5. 数字滚动特效放慢
- 黑屏总时长 2.6s → **5.0s**，数字滚动 0.7s → **1.8s**，淡入/淡出也同步放缓，
  配合服务端分帧加载存档世界数据，视觉上更从容。

### 6. 死亡与存档同一天时不触发滚动
- 客户端改为**只有天数发生变化**才播放滚动特效：回溯到同一天不再触发滚动
  （此前“回溯无条件播放”的行为已按反馈调整）。

### 7. 构建与验证 ✅
- Forge / NeoForge / Fabric 三平台 `build` 全部 **BUILD SUCCESSFUL**；
- Forge `runGameTestServer`：**All 2 required tests passed :)**；
- 产物、源码包与文档已刷新到 `deliverables/`。

## 第五轮：轻量化与命名规范（三平台同步）

### 1. 产物命名规范化
- 统一为 **模组名-模组版本-游戏版本-加载器**：
  `rollbackmod-1.1.0-1.20.1-forge.jar` / `rollbackmod-1.1.0-1.21.1-neoforge.jar` /
  `rollbackmod-1.1.0-1.21.1-fabric.jar`（`jar` / `remapJar` 归档名已设置，源码包同步改名）。

### 2. 断线 → 化茧
- 显示名（中/英）改为 **化茧药芯 / Cocoon Core**；内部标识一并规范：
  物品 ID `disconnection_core` → `cocoon_core`、枚举 `CoreType.COCOON`、
  效果类 `CocoonCoreEffect`、模型与贴图 `cocoon_core.*`、
  配置项 `disconnection_extends_countdown_day` → `cocoon_extends_countdown_day`。

### 3. 轻量化：只保留化茧、蜕皮、高塔
- 删除**柯罗诺斯 / 细沙 / 因果 / 万象 / 停滞**五种药芯及其全部系统：
  - 标记系统 `MarkManager`、标记存档 `RollbackSavedData.MarkRecord`、
    因果换位、万象随机拔刀、停滞失智、细沙拖拽与目标死亡处理；
  - 时之钟兼容层 `TimeClockCompat`、时缓键位 `ClientEvents` / `ChronosKeyPacket` / `ChronosController`、
    柯罗诺斯效果 `ModMobEffects` / `ChronosMobEffect`；
  - 相关配置项（chronos_* / stasis_* / mark_* / myriad_*）、创造栏条目、
    物品模型与贴图、语言文件条目、网络包字段（`DayInfoPacket.chronosToggle`）。
- **模组不再依赖时之钟 mod**：三种药芯与时间流速无关，模组描述与文档同步更新。

### 4. 修复：回溯黑屏未遮挡消息栏
- 根因：黑屏画在 `RenderGuiEvent.Post`，而原版聊天栏与动作栏消息属于 HUD 覆盖层，
  在该时机之后绘制 ⇒ 消息栏浮在黑屏之上。
- 修复：黑屏改为**最上层 HUD 覆盖层**——
  Forge `RegisterGuiOverlaysEvent.registerAboveAll`、
  NeoForge `RegisterGuiLayersEvent.registerAboveAll`、
  Fabric `HudRenderCallback`（在原版 HUD 渲染完成后触发）；
  左上角天数仍在普通 HUD 层（`registerAbove(HOTBAR)`）。

### 5. 吸入器交互补齐
- **单击右键（未长按完成）给出提示**“长按右键吸入药芯”（原先单击没有任何反馈）；
- **已装药芯可再右键取出**：背包/容器界面用**鼠标空手右键点击吸入器**即可取回药芯
  （收纳袋同款 `overrideOtherStackedOnMe` 空手分支，`SlotAccess#set` 放到鼠标上，带取出音效）；
- 悬浮提示改为“用鼠标空手右键点击可取出药芯”。

### 6. 构建与验证 ✅
- Forge / NeoForge / Fabric 三平台 `build` 全部 **BUILD SUCCESSFUL**；
- Forge `runGameTestServer`：**All 2 required tests passed :)**；
- 产物、源码包与文档已按新命名刷新到 `deliverables/`。

## 第四轮：实测反馈修复与交互重做（三平台同步）

### 1. 修复“回溯的黑屏特效没了” ✅
- 根因：客户端只在**天数发生变化**时播放黑屏转场。回溯到**同一天**的存档点
  （例如当天死亡）时天数没变，转场被跳过，看起来“特效消失了”。
- 修复：`DayInfoPacket` 新增 `rollback` 标志，回溯结束一律置 `true`，
  客户端**无条件**播放黑屏时间特效（即使天数相同，此时数字原地淡入）。
  自然跨天 / 睡觉跨天仍按天数变化播放。

### 2. 滚动特效：只有数字滚动，文字固定不动
- “第 X 天” / “还剩 X 天”拆分为 `*.prefix` + 数字 + `*.suffix` 三段渲染：
  前后文字位置固定，只有中间的**数字**按滚动规则上下滚动并淡入淡出；
- 数字列宽取新旧数字宽度最大值并居中，数字位数变化时文字也不会位移。

### 3. 吸入器重做：只装 1 枚、鼠标右键装入、装入后不可卸下
- **容量恒为 1 枚药芯**（`MAX_CORES = 1`，悬浮提示显示“已装药芯：X”与“装入后无法卸下”）；
- **鼠标拿起药芯右键点击吸入器**（背包/容器界面）即可装入
  —— 用与收纳袋一致的 `Item#overrideOtherStackedOnMe` 实现；
  主手持吸入器 + 副手持药芯右键仍可快捷装入；装入时播放收纳袋装入音效；
- **彻底移除卸下逻辑**：删除潜行右键卸下、药芯锁定等分支，
  `core_removed` / `core_locked` 文本随之移除；
- **长按右键 = 喝水动画吸入并触发**（`UseAnim.DRINK`，32 tick，`startUsingItem` 保证动画与进度条一致）；
- 已装药芯的吸入器右键生物仍按标记类药芯标记目标。

### 4. 版本号与产物命名统一
- 三平台版本号统一为 **1.1.0**，产物文件名带模组加载器：
  `rollbackmod-1.1.0-forge.jar` / `rollbackmod-1.1.0-neoforge.jar` / `rollbackmod-1.1.0-fabric.jar`
  （Forge 由 1.0.9 → 1.1.0；`jar` / `remapJar` 任务已设置归档名）。

### 5. 构建与验证 ✅
- Forge 1.20.1 / NeoForge 1.21.1 / Fabric 1.21.1 三平台 `build` 全部 **BUILD SUCCESSFUL**；
- Forge `runGameTestServer`：**All 2 required tests passed :)**（回溯位置/血量/背包回归测试）；
- 产物与源码包已更新到 `deliverables/`。

## 第三轮：按实测反馈的修复与玩法调整（三平台同步）

### 1. 修复“回溯不移动位置” ✅ GameTest 验证
- 根因：世界创建时的存档点里**还没有玩家**（玩家之后才加入），回溯时对不在
  存档点里的玩家用了“当前状态兜底”，等于原地恢复。
- 修复：玩家加入时**补拍快照进存档点**（`addPlayerToCheckpoint`），
  死亡回溯会把玩家送回加入时的位置/状态。
- 新增 GameTest `rollbackRestoresPlayerAddedAfterCheckpoint` 并全部通过
  （Forge `runGameTestServer`：2 个测试全过）。

### 2. 修复标记因区块卸载静默丢失（细沙/万象/因果失效）
- 根因：目标生物进入未加载区块时 `getEntity` 返回 null，被误判为死亡而移除标记。
- 修复：不再在 tick 里因“找不到目标”移除标记；改为监听**目标死亡事件**精确移除
  （Forge/NeoForge `LivingDeathEvent`、Fabric `ServerLivingEntityEvents.AFTER_DEATH`）。

### 3. 万象免死武器检测双保险
- 检测顺序：剑/斧标签 → 弓/弩/三叉戟类型 → 主手攻击伤害属性（覆盖模组武器）。

### 4. 吸入器重做：收纳袋式（⚠️ 已被第四轮第 3 条取代：容量改为 1 枚、鼠标右键装入、不可卸下）
- 取消材质分级（木/石/铁/金/钻/下界合金全部移除），只保留单个“吸入器”
  （耐久 250，蜕皮回溯消耗 1 点耐久）。
- 类似收纳袋：最多装入 6 枚药芯（NBT 存储，悬浮提示显示内容物）。
  - 主手持吸入器 + **副手持药芯右键** → 装入药芯；
  - 主手持吸入器**长按右键（喝水动作）** → 触发吸入器里的第一枚药芯；
  - 主手持吸入器**右键生物** → 用第一个标记类药芯标记目标；
  - **潜行+右键** → 卸下最后一枚药芯；已标记目标的标记类药芯**锁定无法卸下**。
- 蜕皮回溯：回溯覆盖背包后重新扣除耐久**并消耗触发药芯**（不受存档覆盖影响）。
- “回溯摧毁所有药芯”配置同时清空吸入器内的药芯。

### 5. 未安装时之钟时隐藏柯罗诺斯
- 创造栏物品列表在检测不到时之钟 mod 时不再显示柯罗诺丝药芯。

### 6. 跨天黑屏特效修正
- **任何跨天**（自然流逝 / 睡觉 / 死亡回溯）都播放全屏黑屏 + 居中滚动时间特效；
- 修复“两条数字”：黑屏期间不再渲染左上角小字，且旧数字随滚动完全淡出；
- 左上角天数仍可在配置中关闭（`show_day_hud`）。

### 7. 其他
- 作者统一为 **HanMoyun**（三平台元数据 + 文档）；
- 产物按版本号命名（第四轮起统一为 1.1.0 + 加载器后缀：
  rollbackmod-1.1.0-forge.jar / -neoforge.jar / -fabric.jar）。
- 说明：时之钟（timeclock）的 Mixin 为 SRG 名编译，正式安装环境正常，
  仅在开发者环境（userdev）无法加载；已验证其 `/timeclock tickrate` 命令与
  三平台 jar（4.8.0-forge / 4.7.0-neoforge / 4.7.0-fabric）完全兼容。


## 第一轮：核心玩法优化

### 1. 回溯 = “存档覆盖”式全量恢复
- 存档点保存整个世界的完整序列化状态：每个维度的时间/天气、所有非玩家实体
  （`saveAsPassenger` 全量 NBT）、所有已加载区块的方块实体（`saveWithFullMetadata`）、
  每个玩家的完整存档 NBT（含其他模组写入的能力数据）；回溯时整体覆盖。
- 已加载区块枚举（1.20.1 无公开 API）通过 `ChunkTracker`（ChunkEvent.Load/Unload）实现。
- 方块改动追踪保留作为兜底。

### 2. 吸入器材质分级 + 耐久
- 木/石/铁/金/钻/下界合金六档（耐久 59/131/250/32/1561/2031），旧 `inhaler` 等价铁制保留。
- 蜕皮回溯消耗 1 点耐久；回溯覆盖背包后**重新扣除**，耐久不受存档覆盖影响。

### 3. 万象：随机武器战斗
- 见第二轮“重新设计”。

### 4. 因果：先换位，再判伤害
- 致命伤害时先交换玩家与标记生物位置（含跨维度），交换完成后再对目标造成
  玩家最大生命值的魔法伤害，并治疗玩家至满血。

### 5. 天数滚动特效
- 左上角常驻天数（第 X 天 / 还剩 X 天）；前进时旧数字向下滚出、新数字从上方滚入，
  回溯时旧数字向上滚出、新数字从下方滚入。

### 6. 倒计时 / 高塔：极限模式死亡
- 摧毁存档点 → 清空背包 → 真正杀死玩家 → 复活强制旁观者且锁定游戏模式。
  （1.20.1 的 LevelSettings 无公开 hardcore setter，客户端界面为标准死亡界面，
  服务端行为与 hardcore 一致。）

### 7. 多人时间悖论防护
- 任一玩家受伤 → 伤害以魔法伤害源重定向给全体在线玩家（同时死亡、单次回溯）；
  每 1 秒把“主玩家”生命/饱食/背包同步给其他人；
  安装 dontgethurt 时共享伤害经其伤害事件自动转化为部位损伤。

### 8. 柯罗诺斯时缓
- 第一轮做过原生实现（1.20.1 Mixin 门控 / 1.21.1 tickRateManager）。
- 第二轮按需求改回**依赖时之钟 mod**（见下）。

### 9. NeoForge 1.21.1 移植 ✅（构建通过）
### 10. Fabric 1.21.1 移植 ✅（构建通过，评估见 FABRIC-ASSESSMENT.md）

## 第二轮：按新需求的调整（三平台同步）

### A. 修复回溯 bug（位置不变 / 背包不受影响）✅ GameTest 验证
- 玩家恢复不再依赖 `player.load()`：改为**显式恢复**——
  ①位置用 `teleportTo` 显式传送；②背包先 `clearContent()` 再 `load()`；
  ③生命/效果/食物/经验/火焰/氧气逐项显式恢复；④整体 `load()` 仅作模组数据兜底
  （try/catch，失败不影响核心恢复）。
- **Forge GameTest 回归测试通过**（`runGameTestServer`：All 1 required tests passed）：
  验证了位置、血量、背包三项在回溯后恢复为存档点状态。

### B. 接入 Cloth Config（双语配置）
- 配置改为 JSON 文件 `config/rollbackmod.json`（Gson），三平台同一套键名；
- Cloth Config 设置界面，全部文本走语言文件（zh_cn / en_us 双语）：
  - Forge 1.20.1：cloth-config-forge 11.1.136（可选依赖，ConfigScreenHandler 注册）
  - NeoForge 1.21.1：cloth-config-neoforge 15.0.140（IConfigScreenFactory）
  - Fabric 1.21.1：cloth-config-fabric 15.0.140 + Mod Menu 11.0.5（modmenu 入口）
- 配置项：死亡回溯、回溯摧毁药芯、全员回溯、统一生命背包、柯罗诺斯时长/键位模式/倍率、
  停滞时长、每人单标记、目标死亡失标、万象免死需武器、**万象换武器冷却**、
  **左上角天数显示开关**、倒计时模式/天数、**断线延长倒计时一天**。

### C. 存档策略：世界创建时存档一次，之后全靠道具
- 初始存档点改在**世界创建/首次加载时**创建（ServerStartedEvent /
  SERVER_STARTED），登录时不再自动存档；
- 柯罗诺斯效果结束**不再自动存档**（时缓结束只恢复 tickrate）；
- 存档仅由道具触发：断线 / 柯罗诺斯（使用时）。

### D. 时缓回归时之钟 mod
- 移除原生时缓（Mixin / tickRateManager），恢复 `TimeClockCompat`：
  通过时之钟的 `/timeclock tickrate <倍率>` 命令实现（已核对三个平台 jar 的命令名一致）；
- 未安装时之钟时提示 `chronos_no_time_clock`。
- 注意：时之钟 4.8.0（Forge 1.20.1）的 Mixin 是 SRG 名编译的，正式安装环境正常，
  仅在 dev（userdev）环境无法应用——不影响玩家实际使用。

### E. 断线延长倒计时
- 配置 `disconnection_extends_countdown_day`（默认开）：倒计时模式下使用断线存档，
  存活天数 +1，并同步客户端显示。

### F. 万象重新设计：智能快速拔刀
- 被标记生物受伤时：扫描**整个背包**（快捷栏+主背包）的武器，按**主手攻击伤害加权**
  随机选择（攻击力越高的武器越容易被掏出；弓/弩/三叉戟固定权重 3）；
- 武器直接换进当前手持槽位：主手 = 随机武器，副手 = 原主手物品，
  武器原槽位 = 原副手物品（一次三格交换，不打断快捷栏选择）；
- 冷却时间可配置（默认 10 tick）。

### G. 回溯黑屏特效 + 左上角可关闭
- 触发回溯时**全屏变黑**，居中大号显示天数并按滚动规则动画
  （6 回溯到 4：6 向上滚出、4 从下方滚入；淡入 300ms / 淡出 400ms，总时长约 2.6s）；
- 左上角天数 HUD 保留，新增配置 `show_day_hud` 可关闭；
- 键位模式（按住/切换）随 DayInfoPacket 从服务端同步给客户端。

## 构建与测试
- Forge 1.20.1：JDK 17，`gradle build`（dev 环境已切到 47.4.10，与用户环境一致）；
  GameTest：`gradle runGameTestServer`。
- NeoForge 1.21.1 / Fabric 1.21.1：JDK 21。
- 产物见 `deliverables/`。
