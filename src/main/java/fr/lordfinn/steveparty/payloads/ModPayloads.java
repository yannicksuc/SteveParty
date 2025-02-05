package fr.lordfinn.steveparty.payloads;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import static fr.lordfinn.steveparty.items.custom.teleportation_books.HereWeComeBookItem.handleHereWeComeBookPayload;
import static fr.lordfinn.steveparty.items.custom.teleportation_books.HereWeGoBookItem.handleHereWeGoBookPayload;

public class ModPayloads {
    public static final Identifier ARROW_PARTICLES_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "arrow-particles");
    public static final Identifier TOKENS_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "tokens-payload");
    public static final Identifier ENCHANTED_CIRCULAR_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "enchanted-circular-particles-payload");
    public static final Identifier UPDATE_COLORED_TILE_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "update-colored-tile-payload");
    public static final Identifier PARTY_DATA_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "party-data");
    public static final Identifier SELECTION_STATE_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "selection-state-payload");
    public static final Identifier BLOCK_POSES_MAP_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "block-poses-map-payload");
    public static final Identifier HERE_WE_GO_BOOK_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "here-we-go-book-payload");
    public static final Identifier HERE_WE_COME_BOOK_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "here-we-come-book-payload");
    public static final Identifier TRAFFIC_SIGN_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "traffic-sign-payload");

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(ArrowParticlesPayload.ID, ArrowParticlesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TokenPayload.ID, TokenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EnchantedCircularParticlePayload.ID, EnchantedCircularParticlePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UpdateColoredTilePayload.ID, UpdateColoredTilePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PartyDataPayload.ID, PartyDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SelectionStatePayload.ID, SelectionStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectionStatePayload.ID, SelectionStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BlockPosesMapPayload.ID, BlockPosesMapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HereWeGoBookPayload.ID, HereWeGoBookPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HereWeComeBookPayload.ID, HereWeComeBookPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(HereWeGoBookPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> handleHereWeGoBookPayload(player, payload.state()));
        });

        ServerPlayNetworking.registerGlobalReceiver(HereWeComeBookPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> handleHereWeComeBookPayload(player, payload.teleportingTargets()));
        });
    }

}
