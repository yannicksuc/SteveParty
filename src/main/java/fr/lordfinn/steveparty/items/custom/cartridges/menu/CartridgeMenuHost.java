package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A screen handler showing a cartridge's menu: the cartridge's own screen, or a tile's interface (its selected slot).
 * Changes sent with {@link fr.lordfinn.steveparty.payloads.custom.CartridgeSettingPayload} go to the cartridge it
 * edits.
 */
public interface CartridgeMenuHost {
    /** The cartridge edited now, or null (none: an empty slot). */
    @Nullable CartridgeRef editedCartridge(PlayerEntity player);
}
