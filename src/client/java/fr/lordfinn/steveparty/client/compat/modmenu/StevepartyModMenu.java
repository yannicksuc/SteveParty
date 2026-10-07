package fr.lordfinn.steveparty.client.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import fr.lordfinn.steveparty.client.config.ClientOptionsScreen;

/** Optional Mod Menu entry (entrypoint {@code modmenu}, only loaded by Mod Menu): the mod's client options. */
public class StevepartyModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClientOptionsScreen::new;
    }
}
