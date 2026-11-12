package com.oidioomstudio.fastminer.rightclick;

import com.oidioomstudio.fastminer.config.FastMinerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

public class FastMinerRightClickDispatcher {
    public static boolean dispatch(ServerPlayer player, InteractionHand hand, BlockPos originPos, Direction face, Collection<BlockPos> positions) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return false;
        }

        FastMinerConfig.Data config = FastMinerConfig.get();
        boolean isAxe = stack.is(ItemTags.AXES);
        boolean isShovel = stack.is(ItemTags.SHOVELS);
        boolean isHoe = stack.is(ItemTags.HOES);

        if (isAxe && !config.rightClickAxe) return false;
        if (isShovel && !config.rightClickShovel) return false;
        if (isHoe && !config.rightClickHoe) return false;

        if (!isAxe && !isShovel && !isHoe) {
            return false;
        }

        int successCount = 0;
        Direction clickFace = (face != null) ? face : Direction.UP;

        boolean isCreative = player.isCreative();

        for (BlockPos pos : positions) {
            // Tool break prevention (durability 1 protection)
            if (!isCreative && config.preventToolBreak && stack.isDamageableItem()) {
                if (!com.oidioomstudio.olib.util.item.OInventoryHelper.isSafeToDamage(stack)) {
                    break;
                }
            }

            BlockHitResult hit = new BlockHitResult(
                    Vec3.atBottomCenterOf(pos),
                    clickFace,
                    pos,
                    false
            );

            UseOnContext context = new UseOnContext(player, hand, hit);
            InteractionResult result = stack.useOn(context);

            if (result.consumesAction()) {
                successCount++;
            }
        }

        if (successCount > 0 && !isCreative) {
            com.oidioomstudio.fastminer.FastMinerMod.applyHungerDrain(player, successCount);
        }

        return successCount > 0;
    }
}
