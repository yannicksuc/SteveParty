package fr.lordfinn.steveparty.blocks.custom.signs;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.TrafficSignBlock;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.WoodType;

/** Traffic sign made of any planks (vanilla or modded): the wood is kept in its block entity and item. */
public class MaterialTrafficSignBlock extends TrafficSignBlock {
    public static final MapCodec<MaterialTrafficSignBlock> CODEC = createCodec(MaterialTrafficSignBlock::new);

    public MaterialTrafficSignBlock(Settings settings) {
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
