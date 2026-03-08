package com.majorbonghits.moderncompanions.core;

import com.majorbonghits.moderncompanions.ModernCompanions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

/**
 * Custom data components for Modern Companions.
 * BOUND_COMPANION: used by Summoning Torch to store which companion (UUID + name) the torch is bound to.
 */
public final class DataComponentInit {
    private DataComponentInit() {}

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ModernCompanions.MOD_ID);

    /** Stored on Summoning Torch: which companion (and name) this recall is bound to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BoundCompanionData>> BOUND_COMPANION =
            DATA_COMPONENTS.register("bound_companion", () -> DataComponentType.<BoundCompanionData>builder()
                    .persistent(BoundCompanionData.CODEC)
                    .networkSynchronized(BoundCompanionData.STREAM_CODEC)
                    .build());

    public record BoundCompanionData(UUID companionId, String companionName) {
        private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
                s -> {
                    try {
                        return com.mojang.serialization.DataResult.success(UUID.fromString(s));
                    } catch (IllegalArgumentException e) {
                        return com.mojang.serialization.DataResult.error(() -> "Invalid UUID: " + s);
                    }
                },
                UUID::toString
        );

        public static final Codec<BoundCompanionData> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        UUID_CODEC.fieldOf("id").forGetter(BoundCompanionData::companionId),
                        Codec.STRING.fieldOf("name").forGetter(BoundCompanionData::companionName)
                ).apply(instance, BoundCompanionData::new)
        );

        public static final StreamCodec<ByteBuf, BoundCompanionData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, d -> d.companionId().toString(),
                ByteBufCodecs.STRING_UTF8, BoundCompanionData::companionName,
                (uuidStr, name) -> new BoundCompanionData(UUID.fromString(uuidStr), name)
        );
    }
}
