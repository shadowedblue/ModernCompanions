package com.majorbonghits.moderncompanions.block;

import com.majorbonghits.moderncompanions.core.CompanionRespawnData;
import com.majorbonghits.moderncompanions.core.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

public class RespawnAnchorBlockEntity extends BlockEntity {

    private static final String BOUND_NAME_KEY = "BoundCompanionName";
    private static final String BOUND_UUID_KEY = "BoundCompanionUUID";

    private String boundCompanionName = "";
    @Nullable
    private UUID boundCompanionUUID;

    public RespawnAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RESPAWN_ANCHOR_ENTITY.get(), pos, state);
    }

    public String getBoundCompanionName() {
        return boundCompanionName;
    }

    @Nullable
    public UUID getBoundCompanionUUID() {
        return boundCompanionUUID;
    }

    public void setBoundCompanion(String name, @Nullable UUID uuid) {
        this.boundCompanionName = name == null ? "" : name;
        this.boundCompanionUUID = uuid;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(BOUND_NAME_KEY, boundCompanionName);
        if (boundCompanionUUID != null) {
            tag.putUUID(BOUND_UUID_KEY, boundCompanionUUID);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boundCompanionName = tag.getString(BOUND_NAME_KEY);
        boundCompanionUUID = tag.hasUUID(BOUND_UUID_KEY) ? tag.getUUID(BOUND_UUID_KEY) : null;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RespawnAnchorBlockEntity blockEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;
        if (!com.majorbonghits.moderncompanions.core.ModConfig.safeGet(com.majorbonghits.moderncompanions.core.ModConfig.RESPAWN_ANCHOR_ENABLED)) return;

        long time = level.getDayTime() % 24000L;
        if (time != 1) return;

        CompanionRespawnData.processOneRespawn(serverLevel, pos);
    }
}
