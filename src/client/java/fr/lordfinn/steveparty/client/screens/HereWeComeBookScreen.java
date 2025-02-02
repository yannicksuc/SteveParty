package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.items.custom.teleportation_books.TeleportingTarget;
import fr.lordfinn.steveparty.payloads.HereWeComeBookPayload;
import fr.lordfinn.steveparty.screen_handlers.TeleportationBookScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.TP_TARGETS;

public class HereWeComeBookScreen extends TeleportationBookScreen {
    private final PlayerEntity player;
    public HereWeComeBookScreen(TeleportationBookScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.player = inventory.player;
        if (player.getMainHandStack().contains(TP_TARGETS))
            this.titleY = 200;
        inventory.player.getMainHandStack()
                .set(TP_TARGETS,
                List.of(new TeleportingTarget(TeleportingTarget.Group.EVERYONE, 0, 0)));
        ClientPlayNetworking.send(new HereWeComeBookPayload(this.player.getMainHandStack().get(TP_TARGETS)));
    }
}
