package com.majorbonghits.moderncompanions.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * One pending companion respawn: stored in overworld SavedData and processed
 * at next day tick or when the player uses a golden apple on the respawn anchor.
 */
public record CompanionRespawnRequest(
        String entityTypeId,
        String dimension,
        CompoundTag entityData,
        BlockPos bedPos,
        String companionName
) {
    public static final Codec<CompanionRespawnRequest> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("entityTypeId").forGetter(CompanionRespawnRequest::entityTypeId),
                    Codec.STRING.fieldOf("dimension").forGetter(CompanionRespawnRequest::dimension),
                    CompoundTag.CODEC.fieldOf("entityData").forGetter(CompanionRespawnRequest::entityData),
                    BlockPos.CODEC.fieldOf("bedPos").forGetter(CompanionRespawnRequest::bedPos),
                    Codec.STRING.fieldOf("companionName").forGetter(CompanionRespawnRequest::companionName)
            ).apply(instance, CompanionRespawnRequest::new)
    );
}
