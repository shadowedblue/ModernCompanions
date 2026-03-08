package com.majorbonghits.moderncompanions.entity;

import com.majorbonghits.moderncompanions.ModernCompanions;
import com.majorbonghits.moderncompanions.block.RespawnAnchorBlock;
import com.majorbonghits.moderncompanions.block.RespawnAnchorBlockEntity;
import com.majorbonghits.moderncompanions.core.CompanionLastPositionData;
import com.majorbonghits.moderncompanions.core.CompanionRespawnData;
import com.majorbonghits.moderncompanions.core.CompanionRespawnRequest;
import com.majorbonghits.moderncompanions.core.ModBlocks;
import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.core.RespawnAnchorHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = ModernCompanions.MOD_ID)
public final class CompanionEvents {
    private CompanionEvents() {}

    @SubscribeEvent
    public static void giveExperience(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof AbstractHumanCompanionEntity companion && event.getEntity().level() instanceof ServerLevel serverLevel) {
            companion.recordKill(event.getEntity());
            companion.giveExperiencePoints(event.getEntity().getExperienceReward(serverLevel, companion));
        }
    }

    @SubscribeEvent
    public static void friendlyFire(LivingIncomingDamageEvent event) {
        var source = event.getSource();
        var direct = source.getDirectEntity();
        var attacker = source.getEntity();

        AbstractHumanCompanionEntity companion = null;
        if (attacker instanceof AbstractHumanCompanionEntity comp) {
            companion = comp;
        } else if (direct instanceof net.minecraft.world.entity.projectile.Projectile proj && proj.getOwner() instanceof AbstractHumanCompanionEntity comp) {
            companion = comp;
        }

        if (companion == null || !companion.isTame()) return;

        // Prevent harming owner
        if (!ModConfig.safeGet(ModConfig.FRIENDLY_FIRE_PLAYER) && event.getEntity() instanceof Player player) {
            if (companion.getOwner() == player) {
                event.setCanceled(true);
                return;
            }
        }

        // Prevent harming other tamed companions/pets of same owner
        if (!ModConfig.safeGet(ModConfig.FRIENDLY_FIRE_COMPANIONS)) {
            if (event.getEntity() instanceof TamableAnimal other && other.isTame() && other.getOwner() == companion.getOwner()) {
                event.setCanceled(true);
                return;
            }
            if (event.getEntity() instanceof AbstractHumanCompanionEntity otherComp && otherComp.getOwner() == companion.getOwner()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof AbstractHumanCompanionEntity companion)) return;
        if (!companion.isTame()) return;
        if (!companion.hasTrait("trait_lucky")) return;
        double chance = ModConfig.safeGet(ModConfig.LUCKY_EXTRA_DROP_CHANCE);
        if (companion.getRandom().nextDouble() >= chance) return;
        var drops = event.getDrops();
        if (drops.isEmpty()) return;
        var list = drops.stream().toList();
        var pick = list.get(companion.getRandom().nextInt(list.size()));
        if (pick.getItem().isEmpty()) return;
        var copy = pick.getItem().copy();
        copy.setCount(Math.max(1, copy.getCount()));
        var extra = new net.minecraft.world.entity.item.ItemEntity(event.getEntity().level(), pick.getX(), pick.getY(), pick.getZ(), copy);
        drops.add(extra);
    }

    /** When the owner dies, save all their companions' positions so Summoning Torch recall works after respawn (chunks may unload). */
    @SubscribeEvent
    public static void onOwnerDeathSaveCompanionPositions(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        var server = serverLevel.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            for (AbstractHumanCompanionEntity companion : level.getEntitiesOfClass(AbstractHumanCompanionEntity.class, AABB.INFINITE)) {
                if (companion.isTame() && companion.getOwner() != null && companion.getOwner().getUUID().equals(player.getUUID())) {
                    CompanionLastPositionData.get(server).setPosition(companion.getUUID(), level, companion.blockPosition());
                }
            }
        }
    }

    /** When the owner changes dimension, save their companions' positions in the level they are leaving. */
    @SubscribeEvent
    public static void onOwnerChangeDimensionSaveCompanionPositions(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level() instanceof ServerLevel fromLevel) {
            var server = fromLevel.getServer();
            for (AbstractHumanCompanionEntity companion : fromLevel.getEntitiesOfClass(AbstractHumanCompanionEntity.class, AABB.INFINITE)) {
                if (companion.isTame() && companion.getOwner() != null && companion.getOwner().getUUID().equals(player.getUUID())) {
                    CompanionLastPositionData.get(server).setPosition(companion.getUUID(), fromLevel, companion.blockPosition());
                }
            }
        }
    }

    /** (Beta) On companion death, if they have a bound respawn anchor and feature is enabled, enqueue a respawn request. */
    @SubscribeEvent
    public static void onCompanionDeathEnqueueRespawn(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof AbstractHumanCompanionEntity companion) || !companion.hasCompanionRespawnAnchor()) return;
        if (!ModConfig.safeGet(ModConfig.RESPAWN_ANCHOR_ENABLED)) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        String entityTypeId = serverLevel.registryAccess().registryOrThrow(Registries.ENTITY_TYPE)
                .getKey(companion.getType()).toString();
        String dimension = companion.getCompanionRespawnAnchorDimension();
        CompoundTag entityData = new CompoundTag();
        entityData.putString("id", entityTypeId);
        companion.saveWithoutId(entityData);
        String companionName = companion.getCustomName() != null ? companion.getCustomName().getString() : "";

        CompanionRespawnRequest request = new CompanionRespawnRequest(
                entityTypeId,
                dimension,
                entityData,
                companion.getCompanionRespawnAnchorPos(),
                companionName
        );
        CompanionRespawnData.get(serverLevel.getServer()).addRequest(request);
    }

    /** When a respawn anchor is broken, clear the bound companion's anchor and remove respawn requests for that pos. */
    @SubscribeEvent
    public static void onRespawnAnchorBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getState().getBlock() != ModBlocks.RESPAWN_ANCHOR.get()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        var blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity instanceof RespawnAnchorBlockEntity anchorEntity) {
            var boundUuid = anchorEntity.getBoundCompanionUUID();
            if (boundUuid != null) {
                var server = serverLevel.getServer();
                for (ServerLevel level : server.getAllLevels()) {
                    var e = level.getEntity(boundUuid);
                    if (e instanceof AbstractHumanCompanionEntity companion) {
                        companion.clearCompanionRespawnAnchor();
                        break;
                    }
                }
            }
        }
        CompanionRespawnData.get(serverLevel.getServer()).removeRequestsFor(serverLevel, event.getPos());
        RespawnAnchorBlock.clearBreakWarningFor(event.getPos());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        RespawnAnchorHandler.clearForPlayer(event.getEntity().getUUID());
    }
}
