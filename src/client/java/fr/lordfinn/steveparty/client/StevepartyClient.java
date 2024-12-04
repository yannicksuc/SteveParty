package fr.lordfinn.steveparty.client;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.particle.ArrowParticle;
import fr.lordfinn.steveparty.client.particle.HereParticle;
import fr.lordfinn.steveparty.client.screens.TileScreen;
import fr.lordfinn.steveparty.particles.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

import static fr.lordfinn.steveparty.screens.ModScreens.TILE_SCREEN_HANDLER;

public class StevepartyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(TILE_SCREEN_HANDLER, TileScreen::new);
        Steveparty.LOGGER.info("Registering {} screens", Steveparty.MOD_ID);

        ParticleFactoryRegistry.getInstance().register(ModParticles.HERE_PARTICLE, HereParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ARROW_PARTICLE, ArrowParticle.Factory::new);
    }
}
