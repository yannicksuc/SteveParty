package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlock;
import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlockEntity;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxColor;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws a candle holder as the Frousseux asleep in it: its own model (FrousseuxRenderer), its colour, its name and its
 * flame as they were, sitting on the floor of its block, facing whoever put it there. A client-only copy of it, never
 * added to the world (no light block, no AI), kept by the block entity; its age follows the world's time so its
 * flame flickers and it blinks. Its item ({@link #ITEM}) draws it the same way, from the data the item keeps.
 */
public class FrousseuxCandleHolderRenderer implements BlockEntityRenderer<FrousseuxCandleHolderBlockEntity> {
    public FrousseuxCandleHolderRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(FrousseuxCandleHolderBlockEntity holder, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (!(holder.getWorld() instanceof ClientWorld world)) return;
        FrousseuxEntity frousseux = holder.clientModel instanceof FrousseuxEntity copy ? copy : null;
        if (frousseux == null) {
            frousseux = sleeper(world, holder.getFrousseux());
            if (frousseux == null) return;
            holder.clientModel = frousseux;
        }
        float yaw = holder.getCachedState().get(FrousseuxCandleHolderBlock.FACING).asRotation();
        draw(frousseux, world, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /** A copy of the Frousseux kept in {@code data}: its colour, name and health (its flame). */
    static @Nullable FrousseuxEntity sleeper(ClientWorld world, NbtCompound data) {
        FrousseuxEntity frousseux = ModEntities.FROUSSEUX.create(world);
        if (frousseux == null) return null;
        if (!data.isEmpty()) frousseux.readNbt(data);
        else frousseux.setColor(FrousseuxColor.PLAIN);
        return frousseux;
    }

    static void draw(FrousseuxEntity frousseux, ClientWorld world, float yaw, float tickDelta, MatrixStack matrices,
                     VertexConsumerProvider vertexConsumers, int light) {
        frousseux.age = (int) world.getTime();
        frousseux.setYaw(yaw);
        frousseux.setBodyYaw(yaw);
        frousseux.prevBodyYaw = yaw;
        frousseux.setHeadYaw(yaw);
        frousseux.prevHeadYaw = yaw;
        EntityRenderer<? super FrousseuxEntity> renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(frousseux);
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        renderer.render(frousseux, yaw, tickDelta, matrices, vertexConsumers, light);
        matrices.pop();
    }

    /** Its item: the Frousseux it keeps, one shared copy per colour and flame (items in a grid draw many at once). */
    public static final BuiltinItemRendererRegistry.DynamicItemRenderer ITEM = new BuiltinItemRendererRegistry.DynamicItemRenderer() {
        private final Map<FrousseuxColor, FrousseuxEntity[]> copies = new EnumMap<>(FrousseuxColor.class);
        private @Nullable ClientWorld copiesWorld;

        @Override
        public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
            ClientWorld world = MinecraftClient.getInstance().world;
            if (world == null) return;
            if (world != copiesWorld) {
                copies.clear();
                copiesWorld = world;
            }
            NbtCompound kept = FrousseuxCandleHolderBlock.keptIn(stack);
            FrousseuxColor color = FrousseuxCandleHolderBlockEntity.colorOf(kept);
            FrousseuxEntity.Flame flame = FrousseuxCandleHolderBlockEntity.flameOf(kept);
            FrousseuxEntity[] byFlame = copies.computeIfAbsent(color, c -> new FrousseuxEntity[FrousseuxEntity.Flame.values().length]);
            FrousseuxEntity frousseux = byFlame[flame.ordinal()];
            if (frousseux == null) {
                frousseux = sleeper(world, kept);
                if (frousseux == null) return;
                frousseux.setColor(color);
                frousseux.setHealth((float) FrousseuxEntity.MAX_HEALTH * switch (flame) {
                    case FULL -> 1f;
                    case HIGH -> 0.7f;
                    case LOW -> 0.45f;
                    case EMBER -> 0.2f;
                });
                frousseux.setCustomName(null);
                byFlame[flame.ordinal()] = frousseux;
            }
            draw(frousseux, world, 180, MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false),
                    matrices, vertexConsumers, light);
        }
    };
}
