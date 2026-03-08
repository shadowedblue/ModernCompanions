package com.majorbonghits.moderncompanions.core;

import com.majorbonghits.moderncompanions.Constants;
import com.majorbonghits.moderncompanions.block.RespawnAnchorBlock;
import com.majorbonghits.moderncompanions.block.RespawnAnchorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Constants.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Constants.MOD_ID);

    public static final DeferredHolder<Block, RespawnAnchorBlock> RESPAWN_ANCHOR = BLOCKS.register("companion_respawn_anchor",
            () -> new RespawnAnchorBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(1.5F, 1200.0F)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RespawnAnchorBlockEntity>> RESPAWN_ANCHOR_ENTITY =
            BLOCK_ENTITY_TYPES.register("companion_respawn_anchor",
                    () -> BlockEntityType.Builder.of(RespawnAnchorBlockEntity::new, RESPAWN_ANCHOR.get()).build(null));
}
