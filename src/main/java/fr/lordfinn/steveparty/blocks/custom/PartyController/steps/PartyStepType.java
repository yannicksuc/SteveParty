package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.function.Function;

public enum PartyStepType {
    DEFAULT("party_step_type.default", PartyStep::new),
    TOKEN_TURN("party_step_type.token_turn", TokenTurnPartyStep::new),
    MINI_GAME("party_step_type.mini_game", MiniGamePartyStep::new),
    START_ROLLS("party_step_type.start_rolls", StartRollsStep::new),
    BASIC_GAME_GENERATOR("party_step_type.basic_game_generator", BasicGameGeneratorStep::new),
    END("party_step_type.end", EndPartyStep::new),
    EVENT("party_step_type.event", EventPartyStep::new);

    private final String translationKey;
    /** Rebuilds a saved step of this type (see {@link PartyStepFactory#get}). */
    private final Function<NbtCompound, PartyStep> fromNbt;

    public static final Codec<PartyStepType> CODEC = Codec.STRING.xmap(PartyStepType::valueOf, PartyStepType::name);

    PartyStepType(String translationKey, Function<NbtCompound, PartyStep> fromNbt) {
        this.translationKey = translationKey;
        this.fromNbt = fromNbt;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Text getTranslatedText() {
        return Text.translatable(translationKey);
    }

    /** A step of this type rebuilt from its save. */
    public PartyStep fromNbt(NbtCompound nbt) {
        return fromNbt.apply(nbt);
    }
}
