package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.render.geo.EmissiveLayer;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws the Mistigri (MistigriModel), his yellow-green eye glowing in the dark (but while it is shut) and his
 * see-through whiskers.
 */
public class MistigriRenderer extends GeoEntityRenderer<MistigriEntity> {
    private static final Identifier GLOW = Steveparty.id("textures/entity/mistigri_glow.png");

    public MistigriRenderer(EntityRendererFactory.Context context) {
        super(context, new MistigriModel());
        this.shadowRadius = 0.75f;
        addRenderLayer(new EmissiveLayer<>(this, mistigri -> GLOW,
                mistigri -> mistigri.deathTime <= 0 && !mistigri.isInvisible() && !MistigriModel.eyesClosed(mistigri)));
        addRenderLayer(new WhiskerLayer(this));
    }

    /**
     * His whiskers, see-through and a little greyer than painted, so they read as fine hairs, not wires: drawn on
     * their own with the translucent layer (one plane each, seen from both sides), every other bone hidden meanwhile.
     */
    private static final class WhiskerLayer extends GeoRenderLayer<MistigriEntity> {
        /** Their colour over the texture: 45 % opaque, greyed. */
        private static final int COLOR = 0x73C4C2CC;
        /** Each bone's hidden and children-hidden flags before, restored after. */
        private final Map<GeoBone, boolean[]> hidden = new IdentityHashMap<>();

        WhiskerLayer(GeoRenderer<MistigriEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack poseStack, MistigriEntity mistigri, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                           VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay) {
            if (mistigri.isInvisible()) return;
            hidden.clear();
            for (GeoBone bone : bakedModel.topLevelBones()) only(bone);
            RenderLayer layer = RenderLayer.getEntityTranslucent(MistigriModel.TEXTURE);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, mistigri, layer, bufferSource.getBuffer(layer),
                    partialTick, packedLight, packedOverlay, COLOR);
            hidden.forEach((bone, was) -> {
                bone.setHidden(was[0]);
                bone.setChildrenHidden(was[1]);
            });
        }

        /** Hides every bone but the whiskers (remembering how each was). */
        private void only(GeoBone bone) {
            hidden.put(bone, new boolean[]{bone.isHidden(), bone.isHidingChildren()});
            bone.setHidden(!bone.getName().startsWith("whisker_"));
            bone.setChildrenHidden(false); // (setHidden hides the children too)
            for (GeoBone child : bone.getChildBones()) only(child);
        }
    }
}
