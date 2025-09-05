package fr.lordfinn.steveparty.sounds;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final SoundEvent CLOSE_TILE_GUI_SOUND_EVENT = register("close_tile_gui");
    public static final SoundEvent OPEN_TILE_GUI_SOUND_EVENT = register("open_tile_gui");
    public static final SoundEvent SELECT_SOUND_EVENT = register("select");
    public static final SoundEvent CANCEL_SOUND_EVENT = register("cancel");
    public static final SoundEvent POP_SOUND_EVENT = register("pop");
    public static final SoundEvent PLUNGER_SUCK_IN_SOUND_EVENT = register("plunger_suck_in");
    public static final SoundEvent PLUNGER_SUCK_OUT_SOUND_EVENT = register("plunger_suck_out");
    @SuppressWarnings("EmptyMethod")
    public static void initialize() {
    }

    public static SoundEvent register(String name) {
        Identifier identifier = Steveparty.id(name);
        return Registry.register(Registries.SOUND_EVENT, identifier, SoundEvent.of(identifier));
    }
}
