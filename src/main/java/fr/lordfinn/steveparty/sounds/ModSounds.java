package fr.lordfinn.steveparty.sounds;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final SoundEvent CLOSE_TILE_GUI_SOUND_EVENT = register("close-tile-gui");
    public static final SoundEvent OPEN_TILE_GUI_SOUND_EVENT = register("open-tile-gui");
    public static final SoundEvent SELECT_SOUND_EVENT = register("select");
    public static final SoundEvent CANCEL_SOUND_EVENT = register("cancel");
    public static final SoundEvent PLUNGER_SUCK_IN = register("plunger-suck-in");
    public static final SoundEvent PLUNGER_SUCK_OUT = register("plunger-suck-out");
    public static void initialize() {
    }

    public static SoundEvent register(String name) {
        Identifier identifier = Identifier.of(Steveparty.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, identifier, SoundEvent.of(identifier));
    }
}
