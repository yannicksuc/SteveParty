package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.particles.StarFlareEffect;
import net.minecraft.block.BlockState;
import net.minecraft.block.TransparentBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Translucent star crystal, built like glass: full-cube collision, light passes through, faces between two blocks of
 * the same colour are hidden (TranslucentBlock#isSideInvisible), no ambient-occlusion darkening and no camera
 * collision. The glass-like settings (no suffocation, no vision blocking, no spawning) are in ModBlocks.
 * <p>
 * Now and then a solar eruption bursts out of an exposed face and arcs around the block (display only).
 */
public class StarFragmentsBlock extends TransparentBlock {
    public static final MapCodec<StarFragmentsBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("colour").forGetter(StarFragmentsBlock::getColour),
            createSettingsCodec()
    ).apply(instance, StarFragmentsBlock::new));

    public static final int BLUE = 0, GREEN = 1, PURPLE = 2, RED = 3, YELLOW = 4, BLACK = 5;
    /** Eruption chance per display tick for a lone block (6 exposed faces); about one every 9 s near the player. */
    private static final float ERUPTION_CHANCE = 0.25f;
    /** Each neighbouring star block (3x3x3) lowers the chance: walls don't turn into fireworks. */
    private static final float CROWD_DAMPING = 0.15f;

    private final int colour;

    public StarFragmentsBlock(int colour, Settings settings) {
        super(settings);
        this.colour = colour;
    }

    public int getColour() {
        return colour;
    }

    @Override
    protected MapCodec<? extends StarFragmentsBlock> getCodec() {
        return CODEC;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        Direction[] exposed = new Direction[6];
        int count = 0;
        for (Direction dir : Direction.values()) {
            BlockState next = world.getBlockState(pos.offset(dir));
            if (!(next.getBlock() instanceof StarFragmentsBlock) && !next.isOpaqueFullCube()) exposed[count++] = dir;
        }
        if (count == 0) return;
        int crowd = 0;
        for (BlockPos p : BlockPos.iterate(pos.add(-1, -1, -1), pos.add(1, 1, 1))) {
            if (!p.equals(pos) && world.getBlockState(p).getBlock() instanceof StarFragmentsBlock) crowd++;
        }
        float chance = ERUPTION_CHANCE * count / 6f / (1f + CROWD_DAMPING * crowd);
        if (random.nextFloat() >= chance) return;

        Direction face = exposed[random.nextInt(count)];
        Direction tangent = perpendicular(face, random);
        float radius = MathHelper.nextFloat(random, 0.62f, 0.8f);
        float sweep = MathHelper.nextFloat(random, 1.0f, 2.0f);        // 60..115 degrees around the block
        int life = MathHelper.nextInt(random, 22, 32);                  // 1.1..1.6 s
        world.addParticle(new StarFlareEffect(colour, face.getId(), tangent.getId(), radius, sweep, life),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0, 0);
    }

    private static Direction perpendicular(Direction face, Random random) {
        Direction[] options = new Direction[4];
        int n = 0;
        for (Direction d : Direction.values()) if (d.getAxis() != face.getAxis()) options[n++] = d;
        return options[random.nextInt(n)];
    }
}
