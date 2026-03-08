package com.majorbonghits.moderncompanions.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores last known dimension and position for tamed companions.
 * Used by Summoning Torch to find companions in unloaded chunks (load chunk around last position).
 */
public class CompanionLastPositionData extends SavedData {

    private static final String ID = "modern_companions_companion_last_pos";
    private static final String MAP_KEY = "Positions";
    private static final String DIM_KEY = "dim";
    private static final String X_KEY = "x";
    private static final String Y_KEY = "y";
    private static final String Z_KEY = "z";

    private final Map<UUID, Entry> positions = new HashMap<>();

    public static final SavedData.Factory<CompanionLastPositionData> FACTORY = new SavedData.Factory<>(
            CompanionLastPositionData::new,
            CompanionLastPositionData::load,
            null
    );

    public CompanionLastPositionData() {}

    private static CompanionLastPositionData load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionLastPositionData data = new CompanionLastPositionData();
        CompoundTag map = tag.getCompound(MAP_KEY);
        for (String key : map.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                CompoundTag entry = map.getCompound(key);
                String dim = entry.getString(DIM_KEY);
                int x = entry.getInt(X_KEY);
                int y = entry.getInt(Y_KEY);
                int z = entry.getInt(Z_KEY);
                data.positions.put(uuid, new Entry(dim, new BlockPos(x, y, z)));
            } catch (Exception ignored) {}
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag map = new CompoundTag();
        for (Map.Entry<UUID, Entry> e : positions.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(DIM_KEY, e.getValue().dimension);
            entry.putInt(X_KEY, e.getValue().pos.getX());
            entry.putInt(Y_KEY, e.getValue().pos.getY());
            entry.putInt(Z_KEY, e.getValue().pos.getZ());
            map.put(e.getKey().toString(), entry);
        }
        tag.put(MAP_KEY, map);
        return tag;
    }

    public static CompanionLastPositionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    /** Update last known position for a companion (call when they are loaded and tamed). */
    public void setPosition(UUID companionUuid, ServerLevel level, BlockPos pos) {
        String dim = level.dimension().location().toString();
        Entry prev = positions.put(companionUuid, new Entry(dim, pos.immutable()));
        if (prev == null || !prev.dimension.equals(dim) || !prev.pos.equals(pos)) {
            setDirty();
        }
    }

    /** Get last known dimension key and position, or null. */
    @Nullable
    public Entry getPosition(UUID companionUuid) {
        return positions.get(companionUuid);
    }

    public record Entry(String dimension, BlockPos pos) {}
}
