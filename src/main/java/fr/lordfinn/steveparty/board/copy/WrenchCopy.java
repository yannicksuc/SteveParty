package fr.lordfinn.steveparty.board.copy;

import fr.lordfinn.steveparty.board.WrenchActions;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.WrenchCopyComponent;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The Wrench copies the settings of a block and pastes them on others, sneaking (a plain click keeps its meaning: the
 * block's screen or its own action). As the {@link fr.lordfinn.steveparty.board.Pipette} does for destinations: the
 * first block clicked is the source, every next one of its family a target.
 * <ul>
 *     <li>sneak + right click, the Wrench empty or holding the settings of another family: they are copied into it;</li>
 *     <li>sneak + right click on a block of the family it holds: pasted (only where its screen could be used);</li>
 *     <li>sneak + right click in the air: the Wrench is emptied ({@link WrenchItem#use}).</li>
 * </ul>
 * What each block copies: {@link BlockSettingsKinds}. Nothing is ever created in survival: settings only.
 */
public final class WrenchCopy {
    /** The key colour of the tooltips (the Wrench's hints): the block's name in the messages. */
    private static final int KEY_COLOR = 0xFFD24A;

    private WrenchCopy() {
    }

    public static void initialize() {
        // Before the block's own use (a podium's mode, a sign's plate...): both sides decide alike, from the block state
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || !player.isSneaking() || player.isSpectator()) return ActionResult.PASS;
            ItemStack wrench = player.getMainHandStack();
            if (!(wrench.getItem() instanceof WrenchItem)) return ActionResult.PASS;
            BlockSettings kind = BlockSettingsKinds.of(world.getBlockState(hit.getBlockPos()));
            if (kind == null) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            BlockPos pos = kind.settingsPos(world, hit.getBlockPos()).toImmutable();
            // Held down, the use repeats: the same block is not pasted on again and again
            if (!WrenchActions.isRepeat(serverPlayer, pos, world.getTime())) use(serverPlayer, wrench, (ServerWorld) world, pos, kind);
            return ActionResult.SUCCESS;
        });
    }

    /** Sneak + right click on the block at {@code pos} (where its settings live), of the kind {@code kind}. */
    public static void use(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos, BlockSettings kind) {
        WrenchCopyComponent held = wrench.get(ModComponents.WRENCH_COPY);
        if (held != null && held.family().equals(kind.family())) paste(player, world, pos, kind, held);
        else copy(player, wrench, world, pos, kind);
    }

    /** The settings of the block at {@code pos} copied into {@code wrench}. @return whether there were some */
    public static boolean copy(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos, BlockSettings kind) {
        Block block = world.getBlockState(pos).getBlock();
        NbtCompound settings = kind.write(world, pos, world.getRegistryManager(), player.getAbilities().creativeMode);
        if (settings == null) {
            WrenchActions.warn(player, Text.translatable("message.steveparty.wrench.copy.nothing"), true);
            sound(player, ModSounds.CANCEL_SOUND_EVENT, 0.8f);
            return false;
        }
        Text label = kind.label(block, settings, world.getRegistryManager());
        wrench.set(ModComponents.WRENCH_COPY, new WrenchCopyComponent(Registries.BLOCK.getId(block), kind.family(), settings, label));
        player.sendMessage(Text.translatable("message.steveparty.wrench.copy.copied", keyed(label)), true);
        sound(player, SoundEvents.ITEM_BUNDLE_INSERT, 1.0f);
        return true;
    }

    /** The settings {@code held} pasted on the block at {@code pos}. @return what the paste did */
    public static BlockSettings.@Nullable Pasted paste(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockSettings kind,
                                                       WrenchCopyComponent held) {
        if (!kind.mayEdit(player, world, pos)) {
            WrenchActions.warn(player, Text.translatable("message.steveparty.wrench.copy.locked"), true);
            sound(player, ModSounds.CANCEL_SOUND_EVENT, 0.8f);
            return null;
        }
        BlockSettings.Pasted pasted = kind.paste(new BlockSettings.Paste(player, world, pos, held.block(), held.settings(),
                player.getAbilities().creativeMode));
        if (pasted.refusal() != null) {
            WrenchActions.warn(player, pasted.refusal(), true);
            sound(player, ModSounds.CANCEL_SOUND_EVENT, 0.8f);
        } else if (!pasted.missing().isEmpty()) {
            Text missing = Texts.join(pasted.missing(), Text.literal(", "), item -> item.copy().formatted(Formatting.WHITE));
            player.sendMessage(Text.translatable(pasted.changed() ? "message.steveparty.wrench.copy.partly" : "message.steveparty.wrench.copy.missing",
                    missing).formatted(Formatting.GOLD), true);
            if (pasted.changed()) sound(player, ModSounds.SELECT_SOUND_EVENT, 1.7f);
            else sound(player, ModSounds.CANCEL_SOUND_EVENT, 0.8f);
        } else if (pasted.changed()) {
            player.sendMessage(Text.translatable("message.steveparty.wrench.copy.pasted", keyed(held.label())), true);
            sound(player, ModSounds.SELECT_SOUND_EVENT, 1.7f);
        } else {
            player.sendMessage(Text.translatable("message.steveparty.wrench.copy.same", keyed(held.label())), true);
        }
        return pasted;
    }

    /** Sneak + right click in the air: the Wrench forgets the settings it held. @return whether it held some */
    public static boolean clear(ServerPlayerEntity player, ItemStack wrench) {
        if (!wrench.contains(ModComponents.WRENCH_COPY)) return false;
        wrench.remove(ModComponents.WRENCH_COPY);
        player.sendMessage(Text.translatable("message.steveparty.wrench.copy.cleared"), true);
        sound(player, SoundEvents.ITEM_BUNDLE_REMOVE_ONE, 1.0f);
        return true;
    }

    /** What the settings held by {@code wrench} are (its tooltip), null if it holds none. */
    public static @Nullable MutableText heldName(ItemStack wrench) {
        WrenchCopyComponent held = wrench.get(ModComponents.WRENCH_COPY);
        return held == null ? null : keyed(held.label());
    }

    private static MutableText keyed(Text label) {
        return label.copy().withColor(KEY_COLOR);
    }

    private static void sound(ServerPlayerEntity player, SoundEvent sound, float pitch) {
        player.playSoundToPlayer(sound, SoundCategory.PLAYERS, 0.5f, pitch);
    }
}
