package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.signs.StencilPaintBlock;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.screen_handlers.custom.StencilGunScreenHandler;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stencil gun: carries up to 9 stencils and 9 dye stacks and sprays the selected stencil in the selected colour,
 * on stencil signs like a stencil + dye, and on the face of any full block like a spray can.
 * <ul>
 *     <li>sneak + use: opens the gun to load / unload stencils and dyes;</li>
 *     <li>sneak + mouse wheel: picks the next / previous stencil, or colour (the "stencil gun mode" key switches
 *     which one the wheel picks, see the HUD);</li>
 *     <li>the colour list ends with "no paint": the stencil is then only engraved.</li>
 * </ul>
 */
public class StencilGunItem extends Item {
    public static final int STENCIL_SLOTS = 9;
    public static final int DYE_SLOTS = 9;
    public static final int SIZE = STENCIL_SLOTS + DYE_SLOTS;

    public StencilGunItem(Settings settings) {
        super(settings);
    }

    // ---------------------------------------------------------------- contents

    /** @return a copy of the loaded stacks: stencils in 0..8, dyes in 9..17. */
    public static List<ItemStack> contents(ItemStack gun) {
        InventoryComponent component = gun.get(ModComponents.STENCIL_GUN_CONTENTS);
        List<ItemStack> list = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) list.add(component == null ? ItemStack.EMPTY : component.getStack(i));
        return list;
    }

    public static void setContents(ItemStack gun, List<ItemStack> contents) {
        gun.set(ModComponents.STENCIL_GUN_CONTENTS, new InventoryComponent(contents));
    }

    public static StencilGunSelection selection(ItemStack gun) {
        return gun.getOrDefault(ModComponents.STENCIL_GUN_SELECTION, StencilGunSelection.DEFAULT);
    }

    /** What the gun sprays: the selected stencil's shape (null if none) and dye (null: engraved). */
    public record Load(@Nullable byte[] shape, @Nullable DyeColor color, int dyeSlot) {
    }

    public static Load selectedLoad(ItemStack gun) {
        List<ItemStack> contents = contents(gun);
        StencilGunSelection selection = validSelection(contents, selection(gun));
        ItemStack stencil = selection.stencil() >= 0 ? contents.get(selection.stencil()) : ItemStack.EMPTY;
        byte[] shape = stencil.getItem() instanceof StencilItem ? StencilItem.getShape(stencil) : null;
        DyeColor color = null;
        int dyeSlot = -1;
        if (selection.dye() != StencilGunSelection.ENGRAVE) {
            ItemStack dye = contents.get(STENCIL_SLOTS + selection.dye());
            if (dye.getItem() instanceof DyeItem dyeItem) {
                color = dyeItem.getColor();
                dyeSlot = STENCIL_SLOTS + selection.dye();
            }
        }
        return new Load(shape, color, dyeSlot);
    }

    /** Uses up one dye of {@code slot}. */
    public static void consumeDye(ItemStack gun, int slot) {
        if (slot < STENCIL_SLOTS || slot >= SIZE) return;
        List<ItemStack> contents = contents(gun);
        ItemStack dye = contents.get(slot);
        if (dye.isEmpty()) return;
        dye.decrement(1);
        contents.set(slot, dye.isEmpty() ? ItemStack.EMPTY : dye);
        setContents(gun, contents);
    }

    /**
     * Selection pointing at loaded stacks: an empty selected slot moves to the next loaded one (stencils), or to
     * "no paint" when no dye is loaded.
     */
    public static StencilGunSelection validSelection(List<ItemStack> contents, StencilGunSelection selection) {
        int stencil = selection.stencil();
        if (stencil < 0 || stencil >= STENCIL_SLOTS || contents.get(stencil).isEmpty()) stencil = step(contents, 0, STENCIL_SLOTS, stencil, 1, false);
        int dye = selection.dye();
        if (dye != StencilGunSelection.ENGRAVE && (dye < 0 || dye >= DYE_SLOTS || contents.get(STENCIL_SLOTS + dye).isEmpty())) {
            dye = step(contents, STENCIL_SLOTS, DYE_SLOTS, dye, 1, true);
        }
        return new StencilGunSelection(stencil, dye);
    }

    /**
     * Next loaded slot of a section after {@code current} in {@code direction}, wrapping around; with
     * {@code engraveEntry} the "no paint" entry ({@link StencilGunSelection#ENGRAVE}) sits between the last and the
     * first slot.
     */
    private static int step(List<ItemStack> contents, int offset, int count, int current, int direction, boolean engraveEntry) {
        int entries = count + (engraveEntry ? 1 : 0);
        // Entry index: 0..count-1 are slots, count is "no paint"
        int index = current == StencilGunSelection.ENGRAVE ? count : Math.floorMod(current, count);
        for (int i = 0; i < entries; i++) {
            index = Math.floorMod(index + direction, entries);
            if (index == count) return StencilGunSelection.ENGRAVE;
            if (!contents.get(offset + index).isEmpty()) return index;
        }
        return engraveEntry ? StencilGunSelection.ENGRAVE : 0;
    }

    /** Mouse wheel: moves the stencil (or colour) selection by one loaded slot. */
    public static void scroll(ItemStack gun, boolean colors, int direction) {
        if (direction == 0) return;
        List<ItemStack> contents = contents(gun);
        StencilGunSelection selection = validSelection(contents, selection(gun));
        StencilGunSelection next = colors
                ? new StencilGunSelection(selection.stencil(), step(contents, STENCIL_SLOTS, DYE_SLOTS, selection.dye(), direction, true))
                : new StencilGunSelection(step(contents, 0, STENCIL_SLOTS, selection.stencil(), direction, false), selection.dye());
        gun.set(ModComponents.STENCIL_GUN_SELECTION, next);
    }

    // ---------------------------------------------------------------- use

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (player.isSneaking()) {
            if (!world.isClient) openLoader(player, hand);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /** Sneaking always opens the gun (the block is skipped); otherwise sprays on the face of a full block. */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        World world = context.getWorld();
        if (player.isSneaking()) {
            if (!world.isClient) openLoader(player, context.getHand());
            return ActionResult.SUCCESS;
        }
        ItemStack gun = context.getStack();
        Load load = selectedLoad(gun);
        if (load.shape() == null || load.color() == null) return ActionResult.PASS;
        boolean sprayed = StencilPaintBlock.spray(world, context.getBlockPos(), context.getSide(), load.shape(), load.color(),
                player.getHorizontalFacing());
        if (!sprayed) return ActionResult.PASS;
        if (!world.isClient) {
            if (!player.isCreative()) consumeDye(gun, load.dyeSlot());
            playSpray(world, context.getBlockPos().offset(context.getSide()), load.color());
            world.emitGameEvent(GameEvent.BLOCK_CHANGE, context.getBlockPos(), GameEvent.Emitter.of(player));
        }
        return ActionResult.SUCCESS;
    }

    private static void openLoader(PlayerEntity player, Hand hand) {
        int slot = hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot : StencilGunScreenHandler.OFF_HAND_SLOT;
        player.openHandledScreen(new ExtendedScreenHandlerFactory<Integer>() {
            @Override
            public Integer getScreenOpeningData(ServerPlayerEntity serverPlayer) {
                return slot;
            }

            @Override
            public Text getDisplayName() {
                return Text.translatable("item.steveparty.stencil_gun");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity p) {
                return new StencilGunScreenHandler(syncId, inventory, slot);
            }
        });
    }

    /** Spray hiss and a puff of paint. */
    public static void playSpray(World world, BlockPos pos, @Nullable DyeColor color) {
        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.35F, 1.9F);
        if (color != null && world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(new DustParticleEffect(color.getEntityColor(), 1.0F),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.0);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        Load load = selectedLoad(stack);
        List<ItemStack> contents = contents(stack);
        StencilGunSelection selection = validSelection(contents, selection(stack));
        if (load.shape() != null) {
            StencilPatterns.Pattern pattern = StencilPatterns.byShape(load.shape());
            Text name = pattern != null ? pattern.name() : Text.translatable("tooltip.steveparty.stencil.custom");
            tooltip.add(Text.translatable("tooltip.steveparty.stencil_gun.stencil", name).formatted(Formatting.GRAY));
        } else {
            tooltip.add(Text.translatable("tooltip.steveparty.stencil_gun.no_stencil").formatted(Formatting.GRAY));
        }
        Text paint = load.color() != null
                ? Text.translatable("color.minecraft." + load.color().getName())
                : Text.translatable("tooltip.steveparty.stencil_sign.engraved");
        tooltip.add(Text.translatable("tooltip.steveparty.stencil_gun.color", paint).formatted(Formatting.GRAY));
        if (selection.dye() != StencilGunSelection.ENGRAVE && load.dyeSlot() >= 0) {
            tooltip.add(Text.translatable("tooltip.steveparty.stencil_gun.dye_left", contents.get(load.dyeSlot()).getCount()).formatted(Formatting.DARK_GRAY));
        }
        tooltip.add(Text.translatable("tooltip.steveparty.stencil_gun.usage").formatted(Formatting.DARK_GRAY));
    }
}
