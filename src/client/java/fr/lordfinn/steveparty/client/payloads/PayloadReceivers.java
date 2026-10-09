package fr.lordfinn.steveparty.client.payloads;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.client.gui.party.DiceRevealHud;
import fr.lordfinn.steveparty.client.gui.party.PartyHud;
import fr.lordfinn.steveparty.client.renderer.FloatingTextRenderer;
import fr.lordfinn.steveparty.client.screens.DicePickScreen;
import fr.lordfinn.steveparty.client.screens.TokenSpellScreen;
import fr.lordfinn.steveparty.client.screens.TrapSetupScreen;
import fr.lordfinn.steveparty.client.squish.SquishAnimations;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.payloads.custom.*;
import fr.lordfinn.steveparty.client.entity.SixSevenClient;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.getBoardSpaceEntity;
import static fr.lordfinn.steveparty.particles.ModParticles.ARROW_PARTICLE;
import static fr.lordfinn.steveparty.particles.ModParticles.ENCHANTED_CIRCULAR_PARTICLE;

public class PayloadReceivers {
    private static final int ENCHANTED_DEFAULT_COLOR = 0xdec253;
    private static final double ENCHANTED_DEFAULT_ANGULAR_SPEED = 0.1;

    public static void initialize() {

        // Used to spawn arrow particles from the server
        ClientPayloads.receive(ArrowParticlesPayload.ID, (payload, context) ->
                context.player().getWorld().addImportantParticle(ARROW_PARTICLE,
                        payload.position().x, payload.position().y, payload.position().z,
                        payload.velocity().x, payload.velocity().y, payload.velocity().z));

        ClientPayloads.receive(EnchantedCircularParticlePayload.ID, PayloadReceivers::summonEnchanted);

        ClientPayloads.receive(UpdateColoredTilePayload.ID, (payload, context) -> {
            BlockPos pos = payload.position();
            World world = context.player().getWorld();
            BoardSpaceBlockEntity tileEntity = getBoardSpaceEntity(world, pos);
            if (tileEntity == null) return;
            ItemStack behaviorItemstack = tileEntity.getActiveCartridgeItemStack();
            if (behaviorItemstack == null || behaviorItemstack.isEmpty()) return;
            behaviorItemstack.set(ModComponents.COLOR, payload.color());
            MinecraftClient.getInstance().worldRenderer.updateBlock(world, pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        });

        ClientPayloads.receive(PartyDataPayload.ID, (payload, context) -> PartyHud.onPartyData(payload.partyData()));
        ClientPayloads.receive(PartyLivePayload.ID, (payload, context) -> PartyHud.onLiveData(payload.data()));
        ClientPayloads.receive(PartyDashboardPayload.ID, (payload, context) -> {
            if (context.player().currentScreenHandler instanceof PartyControllerScreenHandler handler
                    && handler.syncId == payload.syncId())
                handler.setData(payload.data());
        });

        ClientPayloads.receive(FloatingTextPayload.ID, (payload, context) -> {
            FloatingTextRenderer.spawn(payload.text(), payload.pos(), payload.velocity(), payload.duration(), payload.scale(), payload.color(), payload.fadeStart());
        });

        ClientPayloads.receive(SquishAnimationPayload.ID, (payload, context) -> SquishAnimations.start(context.client().world, payload));

        // The server accepted a Tokenizer Wand use: open the token spell (size slider), unless another screen is open
        ClientPayloads.receive(OpenTokenSpellPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            if (client.world == null || client.currentScreen != null) return;
            if (client.world.getEntityById(payload.entityId()) instanceof LivingEntity mob) {
                client.setScreen(new TokenSpellScreen(mob, payload.currentSize(), payload.resize(), payload.currentColor()));
            }
        });

        // An unsigned Trap's setup screen (sneak right-click), unless another screen is open
        ClientPayloads.receive(TrapSetupPayloads.Open.ID, (payload, context) -> {
            if (context.client().currentScreen == null) context.client().setScreen(new TrapSetupScreen(payload.hand()));
        });

        // A dice prompt (Choice / Lucky / Reroll / swap picker), or its end
        ClientPayloads.receive(DicePromptPayload.ID, (payload, context) -> DicePickScreen.onPayload(context.client(), payload));
        ClientPayloads.receive(DiceRevealPayload.ID, (payload, context) -> DiceRevealHud.onPayload(payload));
        ClientPayloads.receive(SixSevenPayload.ID, (payload, context) -> SixSevenClient.onPayload(payload));
        ClientTickEvents.END_CLIENT_TICK.register(
                DicePickScreen::showWaiting);
    }

    private static void summonEnchanted(EnchantedCircularParticlePayload payload, ClientPlayNetworking.Context context) {
        if (context.player() == null) return;
        World world = context.player().getWorld();
        double distance = payload.distance();
        for (int i = 0; i < payload.count(); i++) {
            // EnchantedCircularParticle: (x, y, z) = circle center, velocity = (radius, color, angular speed)
            world.addImportantParticle(ENCHANTED_CIRCULAR_PARTICLE,
                    payload.position().x, payload.position().y, payload.position().z,
                    distance, ENCHANTED_DEFAULT_COLOR, ENCHANTED_DEFAULT_ANGULAR_SPEED);
        }
    }
}
