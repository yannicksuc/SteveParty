package fr.lordfinn.steveparty.client.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The chat's lines on screen: the party HUDs keep clear of the chat while it shows some. */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> steveparty$getVisibleMessages();
}
