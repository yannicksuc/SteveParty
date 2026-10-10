package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.api.party.PartySteps;
import net.minecraft.nbt.NbtCompound;

public class PartyStepFactory {

    /**
     * Rebuilds a step from NBT, with the loader registered for its kind ({@link PartySteps}). Never returns null: an unknown
     * or broken step is replaced by a generic placeholder so that the indexes of the following steps (and the saved
     * step index) stay aligned.
     */
    public static PartyStep get(NbtCompound nbt){
        String type = nbt.getString("Type");
        PartySteps.Loader loader = PartySteps.get(PartySteps.parse(type));
        if (loader == null) {
            Steveparty.LOGGER.warn("Unknown party step type '{}', replaced by a placeholder step", type);
            return new PartyStep(nbt);
        }
        try {
            return loader.fromNbt(nbt);
        } catch (RuntimeException e) {
            Steveparty.LOGGER.error("Could not rebuild party step of type {}, replaced by a placeholder step", type, e);
        }
        return new PartyStep(nbt);
    }
}
