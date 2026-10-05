package fr.lordfinn.steveparty.minigame.zone;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * What a player may touch while sessions are going on: in a zone, only the participants of its session; out of
 * every zone, only those who are of no session. So a participant reaches nothing out of its zone, nobody else
 * reaches into it, and a spectator (or a participant the mod took out of its zone) touches nothing at all.
 * Game mode and operator rights change nothing to it: a zone in session is not built in.
 * <p>
 * These are the polite refusals, on the click itself; the border also holds underneath, whatever the item used
 * ({@link ZoneBorder}: a player's action changes no block and spawns nothing on the other side).
 */
final class ZonePlayerRules {
    private ZonePlayerRules() {
    }

    static void initialize() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !ZoneBorder.ACTIVE || allowed(player, world, pos) && !keptLive(player, world, pos));
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                !ZoneBorder.ACTIVE || allowed(player, world, pos) && !keptLive(player, world, pos) ? ActionResult.PASS : ActionResult.FAIL);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!ZoneBorder.ACTIVE || world.isClient) return ActionResult.PASS;
            if (forbidden(player, player.getStackInHand(hand))) return ActionResult.FAIL;
            if (ZoneBubbles.isUsable(world.getBlockState(hit.getBlockPos()).getBlock())) return ActionResult.PASS;
            if (keptLive(player, world, hit.getBlockPos())) return ActionResult.FAIL;
            // the block clicked, and the place a block put against it would take
            return allowed(player, world, hit.getBlockPos()) && allowed(player, world, hit.getBlockPos().offset(hit.getSide()))
                    ? ActionResult.PASS : ActionResult.FAIL;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                !ZoneBorder.ACTIVE || allowed(player, world, entity.getBlockPos()) && !keptLive(player, world, entity) ? ActionResult.PASS : ActionResult.FAIL);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                !ZoneBorder.ACTIVE || allowed(player, world, entity.getBlockPos()) && !keptLive(player, world, entity) ? ActionResult.PASS : ActionResult.FAIL);
        // an item used in the air acts where its user stands
        UseItemCallback.EVENT.register((player, world, hand) ->
                !ZoneBorder.ACTIVE || world.isClient || !forbidden(player, player.getStackInHand(hand)) && allowed(player, world, player.getBlockPos())
                        ? TypedActionResult.pass(player.getStackInHand(hand)) : TypedActionResult.fail(player.getStackInHand(hand)));
    }

    /** @return true if the player is of a session and holds an item the server forbids to sessions: it uses it on nothing */
    private static boolean forbidden(PlayerEntity player, ItemStack stack) {
        if (ZoneBorder.bypass > 0 || !(player instanceof ServerPlayerEntity server) || !ZoneBubbles.blocksItem(server, stack)) return false;
        ZoneBubbles.warn(server, "forbidden_item");
        return true;
    }

    /**
     * @return true if the entity is of what goes on beyond its zone (a token of a running party) and stands in a zone
     * in session: nobody uses or hits it, it is not put back at the end (an item given to it would leave the session
     * with it, a token killed would stay dead)
     */
    private static boolean keptLive(PlayerEntity player, World world, Entity entity) {
        if (world.isClient || ZoneBorder.bypass > 0 || !ZoneBubbles.isInZone(world, entity.getBlockPos()) || !ZoneBubbles.isKeptLive(entity)) return false;
        if (player instanceof ServerPlayerEntity server) ZoneBubbles.warn(server, "hands_tied");
        return true;
    }

    /**
     * @return true if the block holds what goes on beyond its zone (a party controller) and stands in a zone in
     * session: the players of the session don't use it, it is not put back at the end (a stack taken out of it would
     * be destroyed with the session inventory, one put in would leave the session)
     */
    private static boolean keptLive(PlayerEntity player, World world, BlockPos pos) {
        if (ZoneBorder.bypass > 0 || !ZoneBubbles.isInZone(world, pos)) return false;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity == null || !ZoneBubbles.isKeptLive(blockEntity.getType())) return false;
        if (player instanceof ServerPlayerEntity server) ZoneBubbles.warn(server, "hands_tied");
        return true;
    }

    private static boolean allowed(PlayerEntity player, World world, BlockPos pos) {
        if (world.isClient || ZoneBorder.bypass > 0 || !(player instanceof ServerPlayerEntity server)) return true;
        if (ZoneBubbles.canTouch(server, world, pos)) return true;
        ZoneBubbles.warn(server, "hands_tied");
        return false;
    }
}
