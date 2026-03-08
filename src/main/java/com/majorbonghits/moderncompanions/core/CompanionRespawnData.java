package com.majorbonghits.moderncompanions.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * SavedData on the overworld holding pending companion respawn requests.
 * Processed by the respawn anchor block entity (next day) or when the player uses a golden apple on the anchor.
 */
public class CompanionRespawnData extends SavedData {

    private static final String ID = "modern_companions_companion_respawns";
    private static final String REQUESTS_KEY = "Requests";

    private final List<CompanionRespawnRequest> requests = new ArrayList<>();

    public static final SavedData.Factory<CompanionRespawnData> FACTORY = new SavedData.Factory<>(
            CompanionRespawnData::new,
            CompanionRespawnData::load,
            null
    );

    public CompanionRespawnData() {}

    private static CompanionRespawnData load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionRespawnData data = new CompanionRespawnData();
        ListTag list = tag.getList(REQUESTS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompanionRespawnRequest.CODEC.parse(NbtOps.INSTANCE, list.get(i))
                    .result()
                    .ifPresent(data.requests::add);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (CompanionRespawnRequest request : requests) {
            CompanionRespawnRequest.CODEC.encodeStart(NbtOps.INSTANCE, request)
                    .result()
                    .ifPresent(list::add);
        }
        tag.put(REQUESTS_KEY, list);
        return tag;
    }

    public static CompanionRespawnData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public static CompanionRespawnData get(MinecraftServer server) {
        return get(server.overworld());
    }

    public void addRequest(CompanionRespawnRequest request) {
        requests.add(request);
        setDirty();
    }

    public void removeRequestsFor(ServerLevel level, BlockPos bedPos) {
        String dim = level.dimension().location().toString();
        boolean removed = requests.removeIf(r -> dim.equals(r.dimension()) && bedPos.equals(r.bedPos()));
        if (removed) setDirty();
    }

    private void migrateOrphanedRequestsForBed(ServerLevel level, BlockPos bedPos) {
        String correctDim = level.dimension().location().toString();
        List<CompanionRespawnRequest> toReadd = new ArrayList<>();
        for (int i = requests.size() - 1; i >= 0; i--) {
            CompanionRespawnRequest r = requests.get(i);
            if (bedPos.equals(r.bedPos()) && !correctDim.equals(r.dimension())) {
                requests.remove(i);
                toReadd.add(new CompanionRespawnRequest(r.entityTypeId(), correctDim, r.entityData(), r.bedPos(), r.companionName()));
            }
        }
        toReadd.forEach(requests::add);
        if (!toReadd.isEmpty()) setDirty();
    }

    public List<CompanionRespawnRequest> getRequestsFor(ServerLevel level, BlockPos bedPos) {
        migrateOrphanedRequestsForBed(level, bedPos);
        String dim = level.dimension().location().toString();
        return requests.stream()
                .filter(r -> dim.equals(r.dimension()) && bedPos.equals(r.bedPos()))
                .toList();
    }

    public CompanionRespawnRequest takeOneRequestFor(ServerLevel level, BlockPos bedPos) {
        migrateOrphanedRequestsForBed(level, bedPos);
        String dim = level.dimension().location().toString();
        for (int i = 0; i < requests.size(); i++) {
            CompanionRespawnRequest r = requests.get(i);
            if (dim.equals(r.dimension()) && bedPos.equals(r.bedPos())) {
                requests.remove(i);
                setDirty();
                return r;
            }
        }
        return null;
    }

    public static boolean processOneRespawn(ServerLevel level, BlockPos bedPos) {
        CompanionRespawnData data = get(level.getServer());
        CompanionRespawnRequest request = data.takeOneRequestFor(level, bedPos);
        if (request == null) return false;

        Entity entity = EntityType.loadEntityRecursive(request.entityData(), level, e -> {
            e.setPos(Vec3.upFromBottomCenterOf(bedPos, 0.8));
            if (e instanceof net.minecraft.world.entity.LivingEntity living) {
                living.setHealth(living.getMaxHealth());
            }
            if (!request.companionName().isEmpty()) {
                e.setCustomName(Component.literal(request.companionName()));
            }
            return e;
        });
        if (entity == null) {
            data.addRequest(request);
            return false;
        }
        level.addFreshEntity(entity);
        return true;
    }
}
