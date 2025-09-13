package fr.lordfinn.steveparty.utils;

import fr.lordfinn.steveparty.components.TripleJumpComponent;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.TripleJumpPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;

public class TripleJumpHandler {

    private static final float FIRST_JUMP_VELOCITY = 0.42f;
    private static final float SECOND_JUMP_VELOCITY = 0.6f;
    private static final float THIRD_JUMP_VELOCITY = 0.8f;
    private static final int JUMP_BUFFER_TICKS = 20;

    // On utilise un NBT pour stocker le nombre de sauts par joueur
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(TripleJumpPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> {
                handleTripleJump(player);
            });
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            for (ServerPlayerEntity player : world.getPlayers()) {
                onPlayerTick(player);
            }
        });
    }
    public static void onPlayerTick(ServerPlayerEntity player) {
        if (!player.isOnGround() || !hasTripleJumpBoots(player)) return;
        TripleJumpComponent comp = getTripleJumpComponent(player);
        if (comp.getJumpCount() > 0 && comp.getJumpBufferExpire() == 0) {
            comp.setJumpBufferExpire(player.getWorld().getTime() + JUMP_BUFFER_TICKS);
        }
    }
    private static void handleTripleJump(ServerPlayerEntity player) {
     /*   TripleJumpComponent comp = getTripleJumpComponent(player);
        if (!hasTripleJumpBoots(player) || !player.isOnGround()) {
            comp.setJumpCount(0);
            comp.setJumpBufferExpire(0);
            return;
        }

        long currentTime = player.getWorld().getTime();
        int jumpCount = comp.getJumpCount();

        if (currentTime > comp.getJumpBufferExpire())
            jumpCount = 0;

        if (comp.getJumpBufferExpire() == 0 || jumpCount > 0) {
            double jumpVelocity = switch (jumpCount) {
                case 0 -> FIRST_JUMP_VELOCITY;
                case 1 -> SECOND_JUMP_VELOCITY;
                case 2 -> THIRD_JUMP_VELOCITY;
                default -> 0.42;
            };

            player.setVelocity(player.getVelocity().x, jumpVelocity, player.getVelocity().z);
            player.velocityModified = true;
            jumpCount++;
            player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), player.getSoundCategory(), 1.0f, 1.0f);
        }
        comp.setJumpCount(jumpCount);
        comp.setJumpBufferExpire(0);*/
    }

    public static TripleJumpComponent getTripleJumpComponent(ServerPlayerEntity player) {
        return TripleJumpComponent.get(player);
    }

    private static boolean hasTripleJumpBoots(ServerPlayerEntity player) {
        return player.getInventory().contains(new ItemStack(ModItems.TRIPLE_JUMP_SHOES));
    }
}

