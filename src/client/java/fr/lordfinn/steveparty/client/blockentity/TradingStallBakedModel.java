package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.TradingStallBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.List;

public class TradingStallBakedModel implements BakedModel {
    private final BakedModel baseModel;
    private final BakedModel modelAB;
    private final BakedModel modelBA;
    private final BakedModel modelBB;
    private final Sprite particleSprite;

    public TradingStallBakedModel(Sprite particleSprite, BakedModel baseModel, BakedModel modelAB, BakedModel modelBA, BakedModel modelBB) {
        this.baseModel = baseModel;
        this.modelAB = modelAB;
        this.modelBA = modelBA;
        this.modelBB = modelBB;
        this.particleSprite = particleSprite;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, Random random) {
        if (state == null) {
            return baseModel.getQuads(null, side, random);
        }
        int color1 = state.get(TradingStallBlock.COLOR1);
        int color2 = state.get(TradingStallBlock.COLOR2);

        // Choix de modèle selon couleurs
        BakedModel selectedModel = baseModel;

        if (isColorA(color1) && !isColorA(color2)) {
            selectedModel = modelAB;
        } else if (!isColorA(color1) && isColorA(color2)) {
            selectedModel = modelBA;
        } else if (!isColorA(color1) && !isColorA(color2)) {
            selectedModel = modelBB;
        }

        return selectedModel.getQuads(state, side, random);
    }

    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean hasDepth() { return true; }
    @Override public boolean isSideLit() { return true; }
    @Override public boolean isBuiltin() { return false; }
    @Override public Sprite getParticleSprite() { return particleSprite; }
    @Override public ModelTransformation getTransformation() { return ModelTransformation.NONE; }


    boolean isColorA(int color) {
        return !(color == 8 || (color >= 0 && color <=6));
    }
}
