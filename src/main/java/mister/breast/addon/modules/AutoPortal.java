package mister.breast.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import mister.breast.addon.MrBeastAddon;
import mister.breast.addon.utils.PlaceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;

public class AutoPortal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum BuildFrom {
        Direction,
        TargetedBlock
    }

    private final Setting<BuildFrom> buildFrom = sgGeneral.add(new EnumSetting.Builder<BuildFrom>()
            .name("build-from")
            .description("Direction: build in front of you. TargetedBlock: build on top of the block you're looking at.")
            .defaultValue(BuildFrom.TargetedBlock)
            .build());
    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
            .name("distance")
            .description("How many blocks in front of you to build.")
            .defaultValue(2).min(1).sliderRange(1, 5)
            .build());
    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("Blocks placed per tick")
            .defaultValue(1).min(1).sliderMax(4)
            .build());
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Ticks to wait between placement steps")
            .defaultValue(0).min(0).sliderMax(10)
            .build());
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Look at blocks while placing and lighting")
            .defaultValue(true)
            .build());
    private final Setting<Boolean> light = sgGeneral.add(new BoolSetting.Builder()
            .name("light")
            .description("Light the portal with flint and steel when finished")
            .defaultValue(true)
            .build());

    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private BlockPos lightPos;
    private int timer, stuck;

    public AutoPortal() {
        super(MrBeastAddon.CATEGORY, "auto-portal", "Builds and lights a nether portal in front of you.");
    }

    @Override
    public void onActivate() {
        queue.clear();
        timer = 0;
        stuck = 0;
        if (mc.player == null || mc.level == null) { toggle(); return; }

        Direction facing = mc.player.getDirection();
        Direction right = facing.getClockWise();
        BlockPos origin = mc.player.blockPosition().relative(facing, distance.get());;

        if (buildFrom.get() == BuildFrom.TargetedBlock) {
            HitResult hit = mc.player.pick(5, 1f, false);
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = blockHit.getBlockPos();
                origin = mc.level.getBlockState(hitPos).canBeReplaced() ? hitPos : hitPos.above();
            } else {
                error("Look at a block to build on.");
                toggle();
                return;
            }
        }

        // frame is 4 wide (w = -1..2) by 5 tall (h = 0..4)
        for (int h = 0; h <= 4; h++) {
            for (int w = -1; w <= 2; w++) {
                boolean frame = w == -1 || w == 2 || h == 0 || h == 4;
                BlockPos pos = origin.relative(right, w).above(h);
                var state = mc.level.getBlockState(pos);

                if (frame) {
                    if (state.is(Blocks.OBSIDIAN)) continue;
                    if (!state.canBeReplaced()) {
                        error("Frame blocked at %s.", pos.toShortString());
                        toggle();
                        return;
                    }
                    queue.add(pos);
                } else if (!state.isAir()) {
                    error("Portal interior blocked at %s.", pos.toShortString());
                    toggle();
                    return;
                }
            }
        }
        lightPos = origin; // bottom frame block next to the interior
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (timer-- > 0) return;

        if (!queue.isEmpty()) {
            FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
            if (!obsidian.found()) {
                error("No obsidian in hotbar.");
                toggle();
                return;
            }

            for (int i = 0; i < blocksPerTick.get() && !queue.isEmpty(); i++) {
                BlockPos pos = queue.peek();
                if (mc.level.getBlockState(pos).is(Blocks.OBSIDIAN) || PlaceUtil.place(pos, obsidian, rotate.get(), true)) {
                    queue.poll();
                    stuck = 0;
                } else {
                    if (++stuck > 40) {
                        error("Couldn't place a block, giving up.");
                        toggle();
                        return;
                    }
                    break;
                }
            }
            timer = delay.get();
            return;
        }

        if (light.get()) {
            FindItemResult fns = InvUtils.findInHotbar(Items.FLINT_AND_STEEL);
            if (!fns.found()) {
                error("No flint and steel in hotbar.");
                toggle();
                return;
            }

            Runnable ignite = () -> {
                InvUtils.swap(fns.slot(), true);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(lightPos).add(0, 0.5, 0), Direction.UP, lightPos, false));
                InvUtils.swapBack();
            };
            if (rotate.get()) Rotations.rotate(Rotations.getYaw(lightPos), Rotations.getPitch(lightPos), ignite);
            else ignite.run();
        }
        toggle();
    }
}
