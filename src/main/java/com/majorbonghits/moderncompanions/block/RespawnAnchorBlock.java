package com.majorbonghits.moderncompanions.block;

import com.majorbonghits.moderncompanions.core.CompanionRespawnData;
import com.majorbonghits.moderncompanions.core.ModBlocks;
import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.core.RespawnAnchorHandler;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * (Beta) Companion Respawn Anchor: bind a companion (shift+right-click anchor then shift+right-click companion).
 * Dead companions respawn after one in-game day or when the player uses a golden apple on the anchor.
 * Right-click to see who is bound; if dead, message suggests using a golden apple to respawn.
 */
public class RespawnAnchorBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    private static final Map<UUID, BlockPos> LAST_BREAK_WARN = new HashMap<>();

    public RespawnAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return (com.mojang.serialization.MapCodec<? extends BaseEntityBlock>) (com.mojang.serialization.MapCodec<?>) Block.CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RespawnAnchorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlocks.RESPAWN_ANCHOR_ENTITY.get(), RespawnAnchorBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        float progress = super.getDestroyProgress(state, player, level, pos);
        if (level instanceof ServerLevel serverLevel && ModConfig.safeGet(ModConfig.RESPAWN_ANCHOR_ENABLED)) {
            boolean hasPending = !CompanionRespawnData.get(serverLevel.getServer()).getRequestsFor(serverLevel, pos).isEmpty();
            if (hasPending) {
                if (player instanceof ServerPlayer serverPlayer && !pos.equals(LAST_BREAK_WARN.get(serverPlayer.getUUID()))) {
                    LAST_BREAK_WARN.put(serverPlayer.getUUID(), pos);
                    serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.respawn_anchor_break_warning"));
                }
                return progress * 0.2F;
            }
        }
        return progress;
    }

    public static void clearBreakWarningFor(BlockPos pos) {
        LAST_BREAK_WARN.entrySet().removeIf(e -> e.getValue().equals(pos));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

        if (!ModConfig.safeGet(ModConfig.RESPAWN_ANCHOR_ENABLED)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.respawn_anchor_disabled"));
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            RespawnAnchorHandler.setPending(player, pos, level.dimension().location().toString());
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.respawn_anchor_set_pending"));
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (stack.is(Items.GOLDEN_APPLE)) {
            if (level instanceof ServerLevel serverLevel && CompanionRespawnData.processOneRespawn(serverLevel, pos)) {
                stack.shrink(1);
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.companion_respawned"));
                }
                return ItemInteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.no_companion_awaiting_respawn"));
            }
            return ItemInteractionResult.FAIL;
        }

        if (stack.isEmpty()) {
            sendOwnerMessage(level, pos, player);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    private static void sendOwnerMessage(Level level, BlockPos pos, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RespawnAnchorBlockEntity anchorEntity)) return;
        String name = anchorEntity.getBoundCompanionName();
        if (!name.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.respawn_anchor_belongs_to", name));
            if (level instanceof ServerLevel serverLevel) {
                if (!CompanionRespawnData.get(serverLevel.getServer()).getRequestsFor(serverLevel, pos).isEmpty()) {
                    serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.respawn_anchor_awaiting_respawn_hint"));
                }
            }
        } else {
            serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.respawn_anchor_no_bound"));
        }
    }
}
