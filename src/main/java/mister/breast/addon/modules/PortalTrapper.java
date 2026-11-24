package mister.breast.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import mister.breast.addon.MrBeastAddon;
import mister.breast.addon.utils.PlaceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class PortalTrapper extends Module {
    private record Portal(BlockPos min, BlockPos max, Direction.Axis axis) {}

    private static final int MAX_ATTEMPTS = 10;
    private static final int RETRY_TICKS = 5;
    private static final int COOLDOWN_TICKS = 100;
    private static final int SCAN_INTERVAL = 5;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks to trap with. Any of these found in your hotbar is used.")
            .defaultValue(List.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN))
            .build());
    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("place-range")
            .description("Maximum distance to a block being placed.")
            .defaultValue(4).min(1).sliderRange(1, 6)
            .build());
    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("Blocks placed per tick.")
            .defaultValue(1).min(1).sliderMax(4)
            .build());
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Ticks to wait between placement steps.")
            .defaultValue(0).min(0).sliderMax(10)
            .build());
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Look at blocks while placing.")
            .defaultValue(true)
            .build());

    private List<Portal> portals = List.of();
    private final Map<BlockPos, Integer> attempts = new HashMap<>();
    private final Map<BlockPos, Integer> nextTry = new HashMap<>();
    private int tick, timer;

    public PortalTrapper() {
        super(MrBeastAddon.CATEGORY, "portal-trapper", "Places blocks in front of and behind portals so players get stuck inside.");
    }

    @Override
    public void onActivate() { reset(); }

    @Override
    public void onDeactivate() { reset(); }

    private void reset() {
        portals = List.of();
        attempts.clear();
        nextTry.clear();
        tick = 0;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        tick++;

        if (tick % SCAN_INTERVAL == 0) portals = findPortals();
        if (timer-- > 0) return;

        double reachSq = placeRange.get() * placeRange.get();
        List<BlockPos> needed = new ArrayList<>();

        for (Portal portal : portals) {
            for (BlockPos pos : trapPositions(portal)) {
                if (!mc.level.getBlockState(pos).canBeReplaced()) { // is already solid block.. maybe add mining eventually?
                    attempts.remove(pos);
                    nextTry.remove(pos);
                    continue;
                }
                if (nextTry.getOrDefault(pos, 0) > tick) continue;
                if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reachSq) continue;
                needed.add(pos);
            }
        }
        if (needed.isEmpty()) return;

        FindItemResult item = InvUtils.findInHotbar(stack -> blocks.get().contains(Block.byItem(stack.getItem())));
        if (!item.found()) {
            error("No trap blocks in hotbar.");
            toggle();
            return;
        }

        int placed = 0;
        for (BlockPos pos : needed) {
            if (placed >= blocksPerTick.get()) break;
            if (!PlaceUtil.place(pos, item, rotate.get(), true)) continue;

            placed++;
            int n = attempts.merge(pos, 1, Integer::sum);
            if (n >= MAX_ATTEMPTS) {
                attempts.remove(pos);
                nextTry.put(pos, tick + COOLDOWN_TICKS);
                warning("Couldn't place at %s, retrying in a few seconds.", pos.toShortString());
            } else {
                nextTry.put(pos, tick + RETRY_TICKS);
            }
        }
        timer = delay.get();
    }

    private List<BlockPos> trapPositions(Portal p) {
        List<BlockPos> list = new ArrayList<>();
        int y = p.min().getY() + (p.max().getY() - p.min().getY()) / 2;

        if (p.axis() == Direction.Axis.X) {
            for (int x = p.min().getX(); x <= p.max().getX(); x++) {
                list.add(new BlockPos(x, y, p.min().getZ() - 1));
                list.add(new BlockPos(x, y, p.min().getZ() + 1));
            }
        } else {
            for (int z = p.min().getZ(); z <= p.max().getZ(); z++) {
                list.add(new BlockPos(p.min().getX() - 1, y, z));
                list.add(new BlockPos(p.min().getX() + 1, y, z));
            }
        }
        return list;
    }

    private Vec3 center(Portal p) {
        return new Vec3(
                (p.min().getX() + p.max().getX() + 1) / 2.0,
                (p.min().getY() + p.max().getY() + 1) / 2.0,
                (p.min().getZ() + p.max().getZ() + 1) / 2.0);
    }

    private List<Portal> findPortals() {
        BlockPos origin = mc.player.blockPosition();
        int range = 8;
        Set<BlockPos> seen = new HashSet<>();
        List<Portal> found = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-range, -range, -range), origin.offset(range, range, range))) {
            if (seen.contains(pos)) continue;
            var state = mc.level.getBlockState(pos);
            if (!state.is(Blocks.NETHER_PORTAL)) continue;

            found.add(floodFill(pos.immutable(), state.getValue(NetherPortalBlock.AXIS), seen));
        }

        Vec3 eye = mc.player.getEyePosition();
        found.sort(Comparator.comparingDouble(p -> eye.distanceToSqr(center(p))));
        return found;
    }

    private Portal floodFill(BlockPos start, Direction.Axis axis, Set<BlockPos> seen) {
        ArrayDeque<BlockPos> stack = new ArrayDeque<>();
        stack.push(start);
        seen.add(start);

        int minX = start.getX(), minY = start.getY(), minZ = start.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        int count = 0;

        while (!stack.isEmpty() && count++ < 1000) {
            BlockPos p = stack.pop();
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());

            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || !mc.level.getBlockState(n).is(Blocks.NETHER_PORTAL)) continue;
                seen.add(n);
                stack.push(n);
            }
        }
        return new Portal(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), axis);
    }
}
