package fr.lordfinn.steveparty.client.payloads;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.client.PartyService;
import fr.lordfinn.steveparty.client.gui.PartyStepsHud;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.payloads.*;
import fr.lordfinn.steveparty.service.TokenData;
import fr.lordfinn.steveparty.utils.BoardSpaceRoutersPersistentState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector2d;

import java.util.Map;
import java.util.UUID;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpace.getBoardSpaceEntity;
import static fr.lordfinn.steveparty.particles.ModParticles.ARROW_PARTICLE;
import static fr.lordfinn.steveparty.particles.ModParticles.ENCHANTED_CIRCULAR_PARTICLE;

public class PayloadReceivers {
    public static void initialize() {

        // Used to spawn arrow particles from the server
        ClientPlayNetworking.registerGlobalReceiver(ArrowParticlesPayload.ID,
                (payload, context) -> context.client().execute(() ->
                context.player().getWorld().addImportantParticle(ARROW_PARTICLE,
                payload.position().x, payload.position().y, payload.position().z,
                payload.velocity().x, payload.velocity().y, payload.velocity().z)));

        ClientPlayNetworking.registerGlobalReceiver(BlockPosesMapPayload.ID,
                (payload, context) -> context.client().execute(()
                        -> BoardSpaceRoutersPersistentState.set(payload.blockPoses())));

        ClientPlayNetworking.registerGlobalReceiver(EnchantedCircularParticlePayload.ID,
                (payload, context) -> context.client().execute(summonEnchanted(context, payload)));

        ClientPlayNetworking.registerGlobalReceiver(TokenPayload.ID, (payload, context)  -> context.client().execute(() ->
        {
            Map<UUID, TokenData> tokens = payload.tokens();
            PartyService.tokens.putAll(tokens);
        }));

        ClientPlayNetworking.registerGlobalReceiver(UpdateColoredTilePayload.ID, (payload, context)  -> context.client().execute(() -> {
            BlockPos pos = payload.position();
            World world = context.player().getWorld();
            BoardSpaceBlockEntity tileEntity = getBoardSpaceEntity(world, pos);
            if (tileEntity == null) return;
            ItemStack behaviorItemstack = tileEntity.getActiveTileBehaviorItemStack();
            behaviorItemstack.set(ModComponents.TB_START_COLOR, payload.color());
            MinecraftClient.getInstance().worldRenderer.updateBlock(world, pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }));

        ClientPlayNetworking.registerGlobalReceiver(PartyDataPayload.ID, (payload, context) -> context.client().execute(() -> PartyStepsHud.updateSteps(payload.partyData())));
    }

    private static Runnable summonEnchanted(ClientPlayNetworking.Context context, EnchantedCircularParticlePayload payload) {
        for (int i = 0; i < payload.count(); i++) {
            double randomRadian = Math.random() * 2 * Math.PI;
            Vector2d vec = new Vector2d(payload.distance() * Math.cos(randomRadian) - payload.distance() * Math.sin(randomRadian),
                    payload.distance() * Math.sin(randomRadian) + payload.distance() * Math.cos(randomRadian));

            context.player().getWorld().addImportantParticle(ENCHANTED_CIRCULAR_PARTICLE,
                    payload.position().x + vec.x, payload.position().y, payload.position().z + vec.y,
                    payload.position().x, payload.position().y, payload.position().z);
        }
        return null;
    }
}
