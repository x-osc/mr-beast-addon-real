package mister.breast.addon.utils;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SwingMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static meteordevelopment.meteorclient.MeteorClient.mc;


public class PlaceUtil {
    // basically so meteor client BlockUtils.place() is DUMB and STUPID and places the block in the wrong location
    // when there is a replaceable block (e.g grass) behind the targeted location, the block is placed on the
    // replaceable block instead
    // bruh
    public static boolean place(BlockPos pos, FindItemResult item, boolean rotate, boolean swing) {
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        Runnable place = () -> {
            InvUtils.swap(item.slot(), true);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            if (swing) SwingMode.Both.interactSwing(InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
        };

        if (rotate) Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), place);
        else place.run();
        return true;
    }
}
