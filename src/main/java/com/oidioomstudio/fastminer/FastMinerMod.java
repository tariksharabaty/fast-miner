package com.oidioomstudio.fastminer;

import com.oidioomstudio.fastminer.config.FastMinerConfig;
import com.oidioomstudio.fastminer.net.KeyPressedPayload;
import com.oidioomstudio.fastminer.net.ModeChangedPayload;
import com.oidioomstudio.fastminer.net.SyncShapePayload;
import com.oidioomstudio.fastminer.rightclick.FastMinerRightClickDispatcher;
import com.oidioomstudio.fastminer.shape.FastMinerShape;
import com.oidioomstudio.fastminer.shape.ShapeRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.oidioomstudio.olib.util.item.OInventoryHelper;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FastMinerMod implements ModInitializer {
    public static final String MOD_ID = "fastminer";
    public static final String MOD_NAME = "Fast Miner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<UUID, Boolean> KEY_PRESSED_PLAYERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PLAYER_SHAPE_INDEX = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> IS_MINING = ThreadLocal.withInitial(() -> false);

    @Override
    public void onInitialize() {
        LOGGER.info("[Fast Miner] Initializing FTB-equivalent Fast Miner powered by O Lib...");
        FastMinerConfig.load();

        // Register Network Payloads using serverboundPlay / clientboundPlay
        PayloadTypeRegistry.serverboundPlay().register(KeyPressedPayload.TYPE, KeyPressedPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ModeChangedPayload.TYPE, ModeChangedPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncShapePayload.TYPE, SyncShapePayload.CODEC);

        // Server Packet Handlers
        ServerPlayNetworking.registerGlobalReceiver(KeyPressedPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                KEY_PRESSED_PLAYERS.put(player.getUUID(), payload.pressed());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ModeChangedPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                UUID uuid = player.getUUID();
                int current = PLAYER_SHAPE_INDEX.getOrDefault(uuid, 0);
                int count = ShapeRegistry.getCount();
                int next = payload.next() ? (current + 1) % count : (current - 1 + count) % count;
                PLAYER_SHAPE_INDEX.put(uuid, next);

                FastMinerShape shape = ShapeRegistry.getShape(next);
                if (shape != null) {
                    ServerPlayNetworking.send(player, new SyncShapePayload(next, Collections.emptyList()));
                }
            });
        });

        // Block Break Event
        PlayerBlockBreakEvents.BEFORE.register(this::onBlockBreak);

        // Right-Click Event (Axe stripping, Shovel flattening, Hoe till)
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            if (!isUltimining(serverPlayer)) {
                return InteractionResult.PASS;
            }

            BlockPos originPos = hitResult.getBlockPos();
            BlockState originState = level.getBlockState(originPos);
            Direction face = hitResult.getDirection();

            FastMinerShape shape = ShapeRegistry.getShape(getShapeIndex(serverPlayer));
            if (shape == null) return InteractionResult.PASS;

            int maxBlocks = FastMinerConfig.get().maxBlocks;
            List<BlockPos> blocks = shape.getBlocks(level, serverPlayer, originPos, face, originState, maxBlocks);

            if (blocks.size() > 1) {
                boolean handled = FastMinerRightClickDispatcher.dispatch(serverPlayer, hand, originPos, face, blocks);
                if (handled) {
                    return InteractionResult.SUCCESS;
                }
            }

            return InteractionResult.PASS;
        });

        LOGGER.info("[Fast Miner] Fast Miner initialized successfully.");
    }

    public static boolean isUltimining(ServerPlayer player) {
        return KEY_PRESSED_PLAYERS.getOrDefault(player.getUUID(), false);
    }

    public static int getShapeIndex(ServerPlayer player) {
        return PLAYER_SHAPE_INDEX.getOrDefault(player.getUUID(), 0);
    }

    private boolean onBlockBreak(Level level, net.minecraft.world.entity.player.Player player, BlockPos originPos, BlockState originState, BlockEntity originEntity) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }

        if (IS_MINING.get()) {
            return true;
        }

        if (!isUltimining(serverPlayer)) {
            return true;
        }

        FastMinerConfig.Data config = FastMinerConfig.get();

        boolean isCreative = serverPlayer.isCreative();

        // Player hunger check: require at least 6 food points (3 drumsticks, same as vanilla sprint threshold)
        if (!isCreative && config.exhaustionPerBlock > 0 && player.getFoodData().getFoodLevel() <= 6) {
            return true;
        }

        ItemStack tool = serverPlayer.getMainHandItem();

        // Require tool check (Creative mode never requires tool)
        if (!isCreative && config.requireTool && tool.isEmpty()) {
            return true;
        }

        // Require valid harvesting tool (Creative mode never requires tool)
        if (!isCreative && config.requireValidToolForBlock && !originState.isAir() && originState.requiresCorrectToolForDrops()) {
            if (!tool.isCorrectToolForDrops(originState)) {
                return true;
            }
        }

        FastMinerShape shape = ShapeRegistry.getShape(getShapeIndex(serverPlayer));
        if (shape == null) {
            return true;
        }

        int maxBlocks = config.maxBlocks;
        Direction face = Direction.UP; // Default direction for calculation
        List<BlockPos> blocks = shape.getBlocks(level, serverPlayer, originPos, face, originState, maxBlocks);

        if (blocks.isEmpty() || (blocks.size() == 1 && blocks.contains(originPos))) {
            return true;
        }

        IS_MINING.set(true);
        int minedCount = 0;
        try {
            ServerLevel serverLevel = (ServerLevel) level;
            for (BlockPos pos : blocks) {
                if (pos.equals(originPos)) continue; // The origin block will be broken naturally by Minecraft

                // Stop if player runs low on hunger in survival (exhausted threshold: 6)
                if (!isCreative && config.exhaustionPerBlock > 0 && serverPlayer.getFoodData().getFoodLevel() <= 6) {
                    break;
                }

                // Prevent tool breaking check (durability 1 protection)
                if (!isCreative && config.preventToolBreak && tool.isDamageableItem()) {
                    if (!OInventoryHelper.isSafeToDamage(tool)) {
                        break;
                    }
                }

                BlockState state = serverLevel.getBlockState(pos);
                if (state.isAir()) continue;

                BlockEntity blockEntity = serverLevel.getBlockEntity(pos);

                // Break block and drop items (ONLY IN SURVIVAL / NON-CREATIVE)
                if (!isCreative) {
                    if (config.teleportItemsToInventory) {
                        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, serverPlayer, tool);
                        for (ItemStack drop : drops) {
                            OInventoryHelper.giveOrDrop(serverPlayer, drop);
                        }
                        state.spawnAfterBreak(serverLevel, pos, tool, true);
                    } else {
                        Block.dropResources(state, serverLevel, pos, blockEntity, serverPlayer, tool);
                    }

                    if (config.teleportXpToPlayer) {
                        AABB box = new AABB(pos).inflate(2.0);
                        List<ExperienceOrb> orbs = serverLevel.getEntitiesOfClass(ExperienceOrb.class, box);
                        for (ExperienceOrb orb : orbs) {
                            orb.setPos(serverPlayer.getX(), serverPlayer.getY() + 0.2, serverPlayer.getZ());
                        }
                    }
                }
                serverLevel.destroyBlock(pos, false, serverPlayer);
                minedCount++;

                // Damage tool (not in creative)
                if (!isCreative && !tool.isEmpty() && tool.isDamageableItem()) {
                    tool.hurtAndBreak(1, serverPlayer, EquipmentSlot.MAINHAND);
                }

                // Stats
                serverPlayer.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
            }

            // Apply visible hunger drain after mining
            if (!isCreative && minedCount > 0) {
                applyHungerDrain(serverPlayer, minedCount);
            }
        } finally {
            IS_MINING.set(false);
        }

        return true;
    }

    private static final Map<UUID, Float> HUNGER_ACCUMULATOR = new ConcurrentHashMap<>();

    public static void applyHungerDrain(Player player, int actionCount) {
        if (actionCount <= 0 || player.isCreative()) return;
        if (player.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;
        FastMinerConfig.Data config = FastMinerConfig.get();
        if (config.exhaustionPerBlock <= 0) return;

        float multiplier = (float) config.exhaustionPerBlock;
        // ~4-5 blocks = 1 food point (half drumstick)
        float drain = actionCount * 0.25f * multiplier;

        float acc = HUNGER_ACCUMULATOR.getOrDefault(player.getUUID(), 0.0f) + drain;
        int pointsToDeduct = (int) acc;
        acc -= pointsToDeduct;
        HUNGER_ACCUMULATOR.put(player.getUUID(), acc);

        if (pointsToDeduct <= 0) return;

        net.minecraft.world.food.FoodData foodData = player.getFoodData();
        float currentSat = foodData.getSaturationLevel();
        int currentFood = foodData.getFoodLevel();

        // Drain saturation
        foodData.setSaturation(Math.max(0.0f, currentSat - (pointsToDeduct * 0.5f)));

        // Directly reduce food level so player visibly loses hunger drumsticks!
        int newFood = Math.max(0, currentFood - pointsToDeduct);
        foodData.setFoodLevel(newFood);

        // Vanilla exhaustion for internal game stats
        player.causeFoodExhaustion(actionCount * 0.05f * multiplier);

        // Immediately sync to client so the hunger bar updates on HUD in real-time
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetHealthPacket(
                    serverPlayer.getHealth(),
                    foodData.getFoodLevel(),
                    foodData.getSaturationLevel()
            ));
        }
    }
}

