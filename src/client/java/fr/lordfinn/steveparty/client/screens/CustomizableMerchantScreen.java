package fr.lordfinn.steveparty.client.screens;

import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.Text;

public class CustomizableMerchantScreen extends MerchantScreen {
    public CustomizableMerchantScreen(MerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }
}
