package fr.lordfinn.steveparty.client.model;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesBlock;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.function.Supplier;

/**
 * The polished tiles in the hand and the inventory: the item draws the block model of the two colours it carries.
 * Items only: in the world the block uses its plain baked models, untouched.
 */
public class PolishedTilesItemModel extends ForwardingBakedModel {
    private final PolishedTilesBlock tiles;

    public PolishedTilesItemModel(BakedModel base, PolishedTilesBlock tiles) {
        this.wrapped = base;
        this.tiles = tiles;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
        BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(tiles.state(stack));
        model.emitItemQuads(stack, randomSupplier, context);
    }

    public static class Plugin implements ModelLoadingPlugin {
        @Override
        public void initialize(Context context) {
            context.modifyModelAfterBake().register((model, ctx) -> {
                Identifier id = ctx.resourceId();
                if (id == null || model == null || !id.getNamespace().equals(Steveparty.MOD_ID)) return model;
                for (PolishedTilesBlock tiles : ModBlocks.POLISHED_TILES) {
                    if (id.getPath().equals("item/" + Registries.BLOCK.getId(tiles).getPath())) return new PolishedTilesItemModel(model, tiles);
                }
                return model;
            });
        }
    }
}
