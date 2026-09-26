package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.TilePartBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The item of the Tile and the Advanced Tile: its size ({@link TileSize}) shows in its name, and a large tile is put
 * on the 2x2 blocks around the aimed point (the aimed block and its neighbours on the aimed side), all of them free.
 */
public class TileBlockItem extends BlockItem {
    public TileBlockItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public @Nullable ItemPlacementContext getPlacementContext(ItemPlacementContext context) {
        if (TileSize.of(context.getStack()) != TileSize.LARGE) return super.getPlacementContext(context);
        BlockPos pos = context.getBlockPos();
        Vec3d hit = context.getHitPos();
        // The 2x2 whose centre is closest to the aimed point; the tile itself is its north-west block
        BlockPos master = pos.add(hit.x - pos.getX() < 0.5 ? -1 : 0, 0, hit.z - pos.getZ() < 0.5 ? -1 : 0);
        World world = context.getWorld();
        if (!world.getBlockState(master).isReplaceable() && !master.equals(pos)) return null;
        for (TilePartBlock.Part part : TilePartBlock.Part.values()) {
            BlockPos partPos = part.fromMaster(master);
            if (!partPos.equals(pos) && !world.getBlockState(partPos).isReplaceable()) return null;
        }
        if (master.equals(pos)) return context.canPlace() ? context : null;
        return ItemPlacementContext.offset(context, master, context.getSide());
    }

    @Override
    public Text getName(ItemStack stack) {
        TileSize size = TileSize.of(stack);
        Text name = super.getName(stack);
        if (size == TileSize.STANDARD) return name;
        return Text.translatable("block.steveparty.tile.sized", name, Text.translatable("tooltip.steveparty.tile.size." + size.asString()));
    }
}
