package fr.lordfinn.steveparty.api.party;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.api.StevePartyRegistries;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The kinds of party steps, and how a saved step of each kind is rebuilt. Registry:
 * {@link StevePartyRegistries#PARTY_STEPS}.
 * <p>
 * A party is a list of steps ({@link PartyStep}) played one after the other by its Party Controller: the start rolls,
 * the turns of the tokens, the mini-games, the events, the end... Steve Party's kinds are {@code steveparty:<name>}
 * ({@link PartyStepType}). A new kind, for an addon:
 * <ol>
 *     <li>a class extending {@link PartyStep}: {@link PartyStep#start} (call {@code super.start}), {@link PartyStep#end},
 *     {@link PartyStep#tick}, {@link PartyStep#resume} after a reload, {@link PartyStep#onDiceRoll},
 *     {@link PartyStep#onTileReached}... It moves the party on with {@code controller.nextStep()} when it is done;</li>
 *     <li>it overrides {@link PartyStep#getTypeId()} with its id and {@link PartyStep#getName()} with its translation
 *     key, saves its own fields in {@link PartyStep#toNbt()} (on top of {@code super.toNbt()}) and reads them back in
 *     a constructor taking an {@link NbtCompound};</li>
 *     <li>that constructor is registered here under the id ({@code PartySteps.register(id, MyStep::new)}).</li>
 * </ol>
 * The steps of a party are put in it by its party cards ({@link PartyCards}) or by a listener of
 * {@link fr.lordfinn.steveparty.api.event.PartyEvents}.
 */
public final class PartySteps {
    private PartySteps() {
    }

    /** Rebuilds a step of one kind from its save. */
    @FunctionalInterface
    public interface Loader {
        PartyStep fromNbt(NbtCompound nbt);
    }

    public static Loader register(Identifier id, Loader loader) {
        return StevePartyRegistries.PARTY_STEPS.register(id, loader);
    }

    /** The id of a built-in kind: {@code steveparty:token_turn}... */
    public static Identifier idOf(PartyStepType type) {
        return Steveparty.id(type.name().toLowerCase(Locale.ROOT));
    }

    /**
     * The kind a saved step names: an id ({@code myaddon:my_step}), or the name of a built-in kind
     * ({@code TOKEN_TURN}, as Steve Party saves its own steps). Null if it names nothing.
     */
    public static @Nullable Identifier parse(String saved) {
        if (saved.indexOf(':') >= 0) return Identifier.tryParse(saved);
        return Identifier.tryParse(Steveparty.MOD_ID + ":" + saved.toLowerCase(Locale.ROOT));
    }

    /** What a step saves as its kind: the name of a built-in kind, the id of any other. */
    public static String saveName(Identifier id) {
        return id.getNamespace().equals(Steveparty.MOD_ID) ? id.getPath().toUpperCase(Locale.ROOT) : id.toString();
    }

    public static @Nullable Loader get(@Nullable Identifier id) {
        return StevePartyRegistries.PARTY_STEPS.get(id);
    }

    /** Registers the built-in kinds, as an addon registers its own. */
    public static void registerBuiltIns() {
        for (PartyStepType type : PartyStepType.values()) register(idOf(type), type::fromNbt);
    }
}
