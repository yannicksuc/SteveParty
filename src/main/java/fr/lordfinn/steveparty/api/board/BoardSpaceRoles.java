package fr.lordfinn.steveparty.api.board;

import fr.lordfinn.steveparty.api.StevePartyRegistries;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The roles a cartridge gives its board space (tile or check point), and what a board space with that role does: its
 * {@link ABoardSpaceBehavior}. Registry: {@link StevePartyRegistries#BOARD_SPACE_ROLES}.
 * <p>
 * Steve Party's roles are {@code steveparty:<name>} ({@link BoardSpaceType}). A new role, for an addon:
 * <ol>
 *     <li>a behaviour, extending {@link ABoardSpaceBehavior} (built with the {@link BoardSpaceType} whose look it
 *     borrows, most often {@link BoardSpaceType#DEFAULT}), registered here under the addon's id;</li>
 *     <li>a cartridge item, extending {@link CartridgeItem}, whose {@link CartridgeItem#getBoardSpaceRole()} returns
 *     that id and {@link CartridgeItem#getBoardSpaceType()} the built-in role whose look it borrows (the model of the
 *     tile and the block state); its settings are its menu modules ({@link CartridgeItem#modules()}).</li>
 * </ol>
 * A board space reads its role from its active cartridge: the behaviour of the addon's role then answers the
 * landings, the passings, the clicks, the comparator level and the landing feedback of the space.
 * <p>
 * Limit: {@link ABoardSpaceBehavior#tick} is only called for the roles whose base look ticks
 * ({@link ABoardSpaceBehavior#ticks()} of the built-in role, see {@link ABoardSpaceBlock#getTicker}).
 */
public final class BoardSpaceRoles {
    private BoardSpaceRoles() {
    }

    /** Registers what a board space with the role {@code id} does. */
    public static <B extends ABoardSpaceBehavior> B register(Identifier id, B behavior) {
        return StevePartyRegistries.BOARD_SPACE_ROLES.register(id, behavior);
    }

    public static @Nullable ABoardSpaceBehavior get(@Nullable Identifier id) {
        return StevePartyRegistries.BOARD_SPACE_ROLES.get(id);
    }

    /** The role a cartridge stack gives its board space; {@code steveparty:default} for anything else. */
    public static Identifier roleOf(@Nullable ItemStack cartridge) {
        if (cartridge != null && cartridge.getItem() instanceof CartridgeItem item) return item.getBoardSpaceRole();
        return BoardSpaceType.DEFAULT.id();
    }

    /**
     * What a board space whose active cartridge is {@code cartridge} does. A cartridge whose role is not registered
     * gets the behaviour of its base look ({@link CartridgeItem#getBoardSpaceType()}).
     */
    public static ABoardSpaceBehavior behaviorOf(@Nullable ItemStack cartridge) {
        if (cartridge != null && cartridge.getItem() instanceof CartridgeItem item) {
            ABoardSpaceBehavior behavior = get(item.getBoardSpaceRole());
            return behavior != null ? behavior : item.getBoardSpaceType().behavior();
        }
        return BoardSpaceType.DEFAULT.behavior();
    }

    /**
     * What the board space at {@code pos} does: the role of its active cartridge, as long as the block state shows
     * that cartridge (else, while the space changes role, the role of its block state).
     */
    public static ABoardSpaceBehavior behaviorAt(World world, BlockPos pos, BlockState state) {
        BoardSpaceType type = state.get(ABoardSpaceBlock.TILE_TYPE);
        BoardSpaceBlockEntity space = ABoardSpaceBlock.getBoardSpaceEntity(world, pos);
        if (space != null) {
            ItemStack cartridge = space.getActiveCartridgeItemStack();
            if (cartridge.getItem() instanceof CartridgeItem item && item.getBoardSpaceType() == type)
                return behaviorOf(cartridge);
        }
        return type.behavior();
    }
}
