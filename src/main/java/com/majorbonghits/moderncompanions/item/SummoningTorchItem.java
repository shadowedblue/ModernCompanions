package com.majorbonghits.moderncompanions.item;

import com.majorbonghits.moderncompanions.core.CompanionRecallUtil;
import com.majorbonghits.moderncompanions.core.DataComponentInit;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Single-target recall item: bind to one companion (Shift + right-click), then right-click to recall
 * that companion to the player. Works across dimensions and can load unloaded chunks via last-known position.
 */
public class SummoningTorchItem extends Item {

    private static final int RECALL_COOLDOWN_TICKS = 10;

    public SummoningTorchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);

        var bound = stack.get(DataComponentInit.BOUND_COMPANION.get());
        if (bound == null) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.summoning_torch_not_bound"));
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!(level instanceof ServerLevel serverLevel)) return InteractionResultHolder.fail(stack);

        AbstractHumanCompanionEntity companion = CompanionRecallUtil.findCompanionForRecall(serverLevel.getServer(), bound.companionId());
        if (companion == null) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.summoning_torch_failed"));
            }
            return InteractionResultHolder.fail(stack);
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        CompanionRecallUtil.teleportCompanionTo(companion, serverLevel, x, y, z);
        companion.getNavigation().stop();
        player.getCooldowns().addCooldown(this, RECALL_COOLDOWN_TICKS);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.summoning_torch_recalled", bound.companionName()));
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag flag) {
        var bound = stack.get(DataComponentInit.BOUND_COMPANION.get());
        if (bound != null) {
            tooltipComponents.add(Component.translatable("tooltip.modern_companions.summoning_torch.bound", bound.companionName()));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.modern_companions.summoning_torch.hint"));
        }
    }
}
