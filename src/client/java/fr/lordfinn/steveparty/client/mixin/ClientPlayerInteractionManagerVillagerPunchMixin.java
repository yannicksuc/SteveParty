package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.blocks.custom.VillagerBlock;
import fr.lordfinn.steveparty.payloads.custom.VillagerBlockPunchPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Punching a villager block where blocks can't be broken (adventure mode): vanilla stops the attack on the client and
 * sends nothing, so tell the server (it checks and makes the block react: ouch, grumpy, faint). Holding the button
 * attacks every tick: at most one punch every few ticks.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerVillagerPunchMixin {
    @Unique
    private static final int PUNCH_RATE = 5;
    @Unique
    private long steveparty$lastVillagerPunch = Long.MIN_VALUE / 2;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private GameMode gameMode;

    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void steveparty$punchVillagerBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (client.player == null || client.world == null || client.player.isSpectator()) return;
        if (!client.player.isBlockBreakingRestricted(client.world, pos, gameMode)) return;
        if (!(client.world.getBlockState(pos).getBlock() instanceof VillagerBlock)) return;
        long now = client.world.getTime();
        if (now - steveparty$lastVillagerPunch < PUNCH_RATE) return;
        steveparty$lastVillagerPunch = now;
        ClientPlayNetworking.send(new VillagerBlockPunchPayload(pos.toImmutable()));
    }
}
