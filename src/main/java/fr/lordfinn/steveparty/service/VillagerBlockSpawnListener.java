package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.world.GameRules;

import java.util.List;

public class VillagerBlockSpawnListener {
    public static void registerChatListener() {
        ServerMessageEvents.CHAT_MESSAGE.register((signedMessage, serverPlayerEntity, parameters) -> {
            if (signedMessage.getSignedContent().contains("block")) {
                checkNearbyVillagers(serverPlayerEntity);
            }
        });
    }

    public static void checkNearbyVillagers(ServerPlayerEntity player) {
        World world = player.getEntityWorld();
        BlockPos playerPos = player.getBlockPos();
        Box searchBox = new Box(playerPos.toCenterPos().add(3), playerPos.toCenterPos().add(-3));

        // Find all Villagers within a radius of 2 blocks
        List<VillagerEntity> villagers = world.getEntitiesByClass(VillagerEntity.class, searchBox, villager -> true);

        if (villagers.size() >= 2) {


            BlockPos villagerPos = villagers.getFirst().getBlockPos(); // Use the first villager's position
            // Remove the two villagers
            for (VillagerEntity villager : villagers) {
                villager.remove(Entity.RemovalReason.DISCARDED);
            }
            // Place a VillagerBlock at the position of one of the villagers
            world.setBlockState(villagerPos, ModBlocks.VILLAGER_BLOCK.getDefaultState());
        }
    }
}
