package fr.lordfinn.steveparty.blocks;

import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemActionResult;

/** Block "use with item" results: the shared item helpers (stamping, tile behaviours) answer an {@link ActionResult}. */
public final class ItemResults {
    private ItemResults() {
    }

    /** The block's item-use result for {@code result}: PASS lets the item act, without the block's empty-hand use. */
    public static ItemActionResult of(ActionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemActionResult.SUCCESS;
            case CONSUME -> ItemActionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemActionResult.CONSUME_PARTIAL;
            case PASS -> ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            case FAIL -> ItemActionResult.FAIL;
        };
    }
}
