package fr.lordfinn.steveparty.client.token;

import fr.lordfinn.steveparty.entities.TokenPoses;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.event.GeoRenderEvent;

/**
 * Client side of the mob pawn poses: the player who poses a pawn reads which pose it takes ("Pose 3/7"); the poses
 * found are forgotten when the resources reload (models and animations may have changed).
 */
public final class PawnPoseFeedback {
    private PawnPoseFeedback() {
    }

    public static void initialize() {
        TokenPoses.setClientFeedback(PawnPoseFeedback::show);
        // A posed GeckoLib pawn drawn: the shared bones back as they were (see GeoPoses)
        GeoRenderEvent.Entity.Post.EVENT.register(event -> GeoPoses.restore());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Steveparty.id("pawn_poses");
            }

            @Override
            public void reload(ResourceManager manager) {
                MobPoses.clear();
                GeoPoses.clear();
            }
        });
    }

    private static void show(MobEntity mob, int number) {
        int count = Math.max(MobPoses.count(mob.getType()), GeoPoses.count(mob.getType()));
        if (count <= 1) return;
        MinecraftClient client = MinecraftClient.getInstance();
        client.inGameHud.setOverlayMessage(Text.translatable("message.steveparty.token_pose", number % count + 1, count), false);
    }
}
