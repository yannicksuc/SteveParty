package fr.lordfinn.steveparty.gametest.addon;

import fr.lordfinn.steveparty.api.StevePartyAddon;
import fr.lordfinn.steveparty.api.board.BoardSpaceRoles;
import fr.lordfinn.steveparty.api.party.PartyCards;
import fr.lordfinn.steveparty.api.party.PartySteps;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.powerups.PowerUp;
import fr.lordfinn.steveparty.powerups.PowerUps;
import net.minecraft.util.Formatting;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * A Steve Party addon as an addon author writes one, loaded through the {@code steveparty} entrypoint of the GameTest
 * mod: a board space role with its cartridge, and a kind of party step. The API GameTests check it works.
 */
public class TestAddon implements StevePartyAddon {
    public static final String NAMESPACE = "steveparty-gametest";
    public static final Identifier PROBE_ROLE = Identifier.of(NAMESPACE, "probe");
    public static final Identifier PROBE_STEP = Identifier.of(NAMESPACE, "probe_step");
    /** How many times the entrypoint ran (once). */
    public static int initialized = 0;
    public static Item PROBE_CARTRIDGE;
    public static final Identifier PROBE_CARD = Identifier.of(NAMESPACE, "probe_card");
    /** A party card of the addon: one probe step, of the stack's size as value. */
    public static Item PROBE_CARD_ITEM;

    @Override
    public void onStevePartyInitialize() {
        initialized++;
        BoardSpaceRoles.register(PROBE_ROLE, new ProbeBehavior());
        PROBE_CARTRIDGE = Registry.register(Registries.ITEM, Identifier.of(NAMESPACE, "probe_cartridge"),
                new ProbeCartridge(new Item.Settings()));
        PartySteps.register(PROBE_STEP, ProbeStep::new);
        PartyCards.register(PROBE_CARD, (context, count) -> context.addStep(new ProbeStep(count)));
        PROBE_CARD_ITEM = Registry.register(Registries.ITEM, Identifier.of(NAMESPACE, "party_card_probe"),
                new PartyCardItem(PROBE_CARD, new Item.Settings()));
        DiceModules.register(STICKY);
        PowerUps.register(BANANA);
    }

    /** A dice module of the addon: the token ignores the Stop spaces. */
    public static final DiceModule STICKY = new DiceModule(Identifier.of(NAMESPACE, "sticky"), 1, false) {
        @Override
        public boolean ignoresStops() {
            return true;
        }
    };

    /** A power-up of the addon, doing nothing. */
    public static final PowerUp BANANA = new PowerUp(Identifier.of(NAMESPACE, "banana"), 3, Formatting.YELLOW) {
    };

    /** A board space role: it notes the tokens stopping on it, and drives its Routers at level 13. */
    public static class ProbeBehavior extends ABoardSpaceBehavior {
        public final List<MobEntity> landed = new ArrayList<>();

        public ProbeBehavior() {
            super(BoardSpaceType.DEFAULT);
        }

        @Override
        public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity space, PartyControllerEntity controller) {
            landed.add(token);
            super.onDestinationReached(world, pos, token, space, controller);
        }

        @Override
        public int comparatorLevel(BoardSpaceBlockEntity space, ItemStack stack) {
            return 13;
        }
    }

    /** Its cartridge: the look of a Simple tile, the probe role. */
    public static class ProbeCartridge extends CartridgeItem {
        public ProbeCartridge(Settings settings) {
            super(settings);
        }

        @Override
        public Identifier getBoardSpaceRole() {
            return PROBE_ROLE;
        }
    }

    /** A kind of party step with a field of its own: it ends right away. */
    public static class ProbeStep extends PartyStep {
        public int value;

        public ProbeStep(int value) {
            this.value = value;
        }

        public ProbeStep(NbtCompound nbt) {
            super(nbt);
            this.value = nbt.getInt("Value");
        }

        @Override
        public Identifier getTypeId() {
            return PROBE_STEP;
        }

        @Override
        public NbtCompound toNbt() {
            NbtCompound nbt = super.toNbt();
            nbt.putInt("Value", value);
            return nbt;
        }

        @Override
        public void start(PartyControllerEntity controller) {
            super.start(controller);
            controller.nextStep();
        }
    }
}
