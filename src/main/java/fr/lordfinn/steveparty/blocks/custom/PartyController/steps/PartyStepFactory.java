package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.DefaultBoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StartTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StopBoardSpaceBehavior;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PartyStepFactory {

    private static final Map<PartyStepType, Class<? extends PartyStep>> STEPS_TYPES = new HashMap<>();

    static {
        STEPS_TYPES.put(PartyStepType.DEFAULT, PartyStep.class);
        STEPS_TYPES.put(PartyStepType.TOKEN_TURN, TokenTurnPartyStep.class);
        STEPS_TYPES.put(PartyStepType.MINI_GAME, MiniGamePartyStep.class);
        STEPS_TYPES.put(PartyStepType.END, EndPartyStep.class);
        STEPS_TYPES.put(PartyStepType.START_ROLLS, StartRollsStep.class);
        STEPS_TYPES.put(PartyStepType.BASIC_GAME_GENERATOR, BasicGameGeneratorStep.class);
    }

    public static PartyStep get(NbtCompound nbt){
        String type = nbt.getString("Type");
        Class<? extends PartyStep> stepClass = STEPS_TYPES.get(PartyStepType.valueOf(type.toUpperCase()));
        try {
            return stepClass.getConstructor(NbtCompound.class).newInstance(nbt);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            Steveparty.LOGGER.error(e.getMessage());
            Steveparty.LOGGER.error(Arrays.toString(e.getStackTrace()));
        }
        return null;
    }
}
