package com.majorbonghits.moderncompanions.core;

import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Shared recall logic for Summoning Torch: find companion by UUID (any dimension or unloaded chunk)
 * and teleport them to a target position, including cross-dimension.
 */
public final class CompanionRecallUtil {
    private CompanionRecallUtil() {}

    /**
     * Find companion by UUID in any loaded level, or by loading their last-known chunk if in unloaded chunks.
     */
    @Nullable
    public static AbstractHumanCompanionEntity findCompanionForRecall(MinecraftServer server, UUID uuid) {
        AbstractHumanCompanionEntity c = findCompanionInAnyLevel(server, uuid);
        if (c == null) {
            c = tryFindCompanionByLoadingChunk(server, uuid);
        }
        return c;
    }

    @Nullable
    private static AbstractHumanCompanionEntity findCompanionInLevel(ServerLevel level, UUID uuid) {
        Entity e = level.getEntity(uuid);
        return e instanceof AbstractHumanCompanionEntity comp ? comp : null;
    }

    @Nullable
    private static AbstractHumanCompanionEntity findCompanionInAnyLevel(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            AbstractHumanCompanionEntity found = findCompanionInLevel(level, uuid);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Load a 3x3 chunk area around the companion's last-known position, then look up by UUID.
     */
    @Nullable
    private static AbstractHumanCompanionEntity tryFindCompanionByLoadingChunk(MinecraftServer server, UUID uuid) {
        CompanionLastPositionData.Entry last = CompanionLastPositionData.get(server).getPosition(uuid);
        if (last == null) return null;
        ServerLevel targetLevel = null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(last.dimension())) {
                targetLevel = level;
                break;
            }
        }
        if (targetLevel == null) return null;
        ChunkPos center = new ChunkPos(last.pos());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                targetLevel.getChunkSource().getChunk(center.x + dx, center.z + dz, true);
            }
        }
        return findCompanionInLevel(targetLevel, uuid);
    }

    /**
     * Teleport a companion to (x,y,z) in the given level. If they are in another dimension, change dimension then teleport.
     * Updates last-known position after teleport so recall keeps working.
     */
    public static void teleportCompanionTo(AbstractHumanCompanionEntity companion, ServerLevel targetLevel, double x, double y, double z) {
        if (companion.level() == targetLevel) {
            companion.teleportTo(x, y, z);
        } else {
            Vec3 targetPos = new Vec3(x, y, z);
            DimensionTransition transition = new DimensionTransition(
                    targetLevel, targetPos, Vec3.ZERO,
                    companion.getYRot(), companion.getXRot(),
                    false, DimensionTransition.DO_NOTHING);
            Entity newEntity = companion.changeDimension(transition);
            if (newEntity != null) {
                newEntity.teleportTo(x, y, z);
            }
        }
        MinecraftServer server = targetLevel.getServer();
        if (server != null) {
            CompanionLastPositionData.get(server).setPosition(companion.getUUID(), targetLevel, BlockPos.containing(x, y, z));
        }
    }
}
