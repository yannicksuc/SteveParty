package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllers;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.payloads.custom.DiceRevealPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.utils.EntitiesUtils.getPlayerNameByUuid;

/**
 * What the players see and hear of the reveal of a throw ({@link DiceRollSequence}): each die landing (a pop, a puff),
 * a double or a triple as soon as the dice make it (a chime, the matching dice glow), then the total (a sting); and the
 * reveal shown at the action bar's place ({@link DiceRevealPayload}) to the players near the dice and to the whole
 * party of the roller: « LordFinn : 3 + 5 + 3 = 11 ».
 */
public final class DiceReveal {
    /** Players this close to the dice see the reveal (as the result used to be told). */
    public static final double NEARBY = 20;

    private DiceReveal() {
    }

    /** Die {@code index} of the throw ({@code dice} in all) lands on {@code face}. */
    public static void dieLands(DiceEntity die, int index, int dice) {
        if (!(die.getWorld() instanceof ServerWorld world)) return;
        // A thud, and a note going up die after die
        world.playSound(null, die.getX(), die.getY(), die.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(),
                SoundCategory.PLAYERS, 0.8f, 0.9f);
        if (dice > 1) world.playSound(null, die.getX(), die.getY(), die.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                SoundCategory.PLAYERS, 0.7f, (float) Math.pow(2, (index * 4) / 12.0) * 0.9f);
        world.spawnParticles(ParticleTypes.CRIT, die.getX(), die.getY() + die.getHeight() / 2, die.getZ(), 10, 0.25, 0.25, 0.25, 0.2);
        world.spawnParticles(ParticleTypes.POOF, die.getX(), die.getY() + die.getHeight() / 2, die.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
    }

    /** The dice just made a double ({@code same} 2) or a triple (3): {@code matching} show the same number. */
    public static void combo(List<DiceEntity> matching, int same) {
        for (DiceEntity die : matching) {
            die.setGlowing(true);
            if (die.getWorld() instanceof ServerWorld world)
                world.spawnParticles(same >= 3 ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.HAPPY_VILLAGER,
                        die.getX(), die.getY() + die.getHeight() / 2, die.getZ(), same >= 3 ? 24 : 12, 0.3, 0.3, 0.3, 0.25);
        }
        if (matching.isEmpty() || !(matching.getFirst().getWorld() instanceof ServerWorld world)) return;
        DiceEntity at = matching.getLast();
        world.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2f, 1.2f);
        world.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS,
                0.9f, same >= 3 ? 2.0f : 1.5f);
        if (same >= 3) world.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE,
                SoundCategory.PLAYERS, 1.0f, 1.1f);
    }

    /** The whole throw is revealed: the total comes (louder than a die). */
    public static void total(DiceEntity lead, int dice) {
        if (dice <= 1 || !(lead.getWorld() instanceof ServerWorld world)) return;
        world.playSound(null, lead.getX(), lead.getY(), lead.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.3f);
        world.playSound(null, lead.getX(), lead.getY(), lead.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    /** Shows where the reveal is to who follows the throw: {@code total} null while it goes on. */
    public static void send(DiceEntity lead, int dice, List<DiceFace> faces, @Nullable Text total) {
        if (!(lead.getWorld() instanceof ServerWorld world)) return;
        List<Integer> numbers = DiceThrow.numbers(faces);
        int same = DiceThrow.sameCount(numbers);
        UUID roller = lead.getOwner().orElse(null);
        String name = roller == null ? null : getPlayerNameByUuid(world.getServer(), roller);
        if (name == null) name = Text.translatable("message.steveparty.unknown_player").getString();
        DiceRevealPayload payload = DiceRevealPayload.of(lead.getId(), roller, name, dice, faces, same >= 2 ? Math.min(3, same) : 0,
                same >= 2 ? DiceThrow.sameNumber(numbers) : 0, total);
        for (ServerPlayerEntity player : recipients(lead)) ServerPlayNetworking.send(player, payload);
    }

    /** The players near the dice and the audience of the running party the roller plays in. */
    public static List<ServerPlayerEntity> recipients(DiceEntity lead) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        if (!(lead.getWorld() instanceof ServerWorld world)) return players;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getPos().isInRange(lead.getPos(), NEARBY)) players.add(player);
        }
        UUID roller = lead.getOwner().orElse(null);
        if (roller == null) return players;
        for (PartyControllerEntity party : PartyControllers.all()) {
            if (party.isRemoved() || !party.getPartyData().isStarted() || !party.getPlayersInOrder().contains(roller)) continue;
            for (ServerPlayerEntity player : party.getPartyAudience()) {
                if (!players.contains(player)) players.add(player);
            }
        }
        return players;
    }
}
