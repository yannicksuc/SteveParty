package fr.lordfinn.steveparty.blocks.custom.signs;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.EaselSignBlock;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.WoodType;

/** Easel sign made of any planks (vanilla or modded): the wood is kept in its block entity and item. */
public class MaterialEaselSignBlock extends EaselSignBlock {
    public static final MapCodec<MaterialEaselSignBlock> CODEC = createCodec(MaterialEaselSignBlock::new);

    public MaterialEaselSignBlock(Settings settings) {
        super(WoodType.OAK, settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public SignMaterial getMaterialKind() {
        return SignMaterial.WOOD;
    }
}
