package fr.lordfinn.steveparty.client;

import fr.lordfinn.steveparty.blocks.switchable.Switchables;
import fr.lordfinn.steveparty.payloads.custom.SwitchableBlocksPayload;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Objects;
import java.util.stream.Collectors;

/** "Switchable" line in the tooltip of every block the hop switch can switch (tag or server config). */
public final class SwitchableClient {
    private SwitchableClient() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(SwitchableBlocksPayload.ID, (payload, context) -> context.client().execute(() ->
                Switchables.setConfigBlocks(payload.blocks().stream()
                        .map(id -> Registries.BLOCK.getOptionalValue(id).orElse(null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.<Block>toSet()))));

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            if (stack.getItem() instanceof BlockItem blockItem && Switchables.isSwitchable(blockItem.getBlock().getDefaultState())) {
                lines.add(Text.translatable("tooltip.steveparty.switchable").formatted(Formatting.AQUA));
            }
        });
    }
}
