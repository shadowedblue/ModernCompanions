package com.majorbonghits.moderncompanions.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-only handler for pending respawn anchor selection.
 * When a player shift+right-clicks the anchor, we store it here; the next shift+right-click on a companion binds that companion to the anchor.
 */
public final class RespawnAnchorHandler {

    private static final Map<UUID, PendingAnchorSelection> PENDING = new ConcurrentHashMap<>();

    private RespawnAnchorHandler() {}

    public static void setPending(Player player, BlockPos pos, String dimension) {
        if (player.level().isClientSide()) return;
        PENDING.put(player.getUUID(), new PendingAnchorSelection(pos, dimension));
    }

    public static Optional<PendingAnchorSelection> getAndClearPending(Player player) {
        if (player.level().isClientSide()) return Optional.empty();
        return Optional.ofNullable(PENDING.remove(player.getUUID()));
    }

    public static void clearForPlayer(UUID playerId) {
        PENDING.remove(playerId);
    }

    public record PendingAnchorSelection(BlockPos pos, String dimension) {}
}
