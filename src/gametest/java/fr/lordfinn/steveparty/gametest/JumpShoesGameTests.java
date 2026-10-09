package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.jumpshoes.JumpShoes;
import fr.lordfinn.steveparty.items.custom.jumpshoes.JumpShoesState;
import fr.lordfinn.steveparty.items.custom.jumpshoes.TripleJumpChain;
import fr.lordfinn.steveparty.items.tooltip.ItemTips;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The Triple Jump Shoes: the jump chain, the Double Jump enchantment, the wall kick direction, no unfair fall damage. */
public class JumpShoesGameTests implements FabricGameTest {

    private static RegistryEntry<Enchantment> doubleJump(TestContext context) {
        return context.getWorld().getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(JumpShoes.DOUBLE_JUMP);
    }

    private static PlayerEntity shod(TestContext context, boolean enchanted) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        ItemStack shoes = new ItemStack(ModItems.TRIPLE_JUMP_SHOES);
        if (enchanted) shoes.addEnchantment(doubleJump(context), 1);
        player.equipStack(EquipmentSlot.FEET, shoes);
        return player;
    }

    /** « Double Jump »: only on the shoes, level 1, offered by the enchanting table (and so on books). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void doubleJumpEnchantsOnlyTheShoes(TestContext context) {
        RegistryEntry<Enchantment> enchantment = doubleJump(context);
        ItemStack shoes = new ItemStack(ModItems.TRIPLE_JUMP_SHOES);
        context.assertTrue(enchantment.value().isSupportedItem(shoes), "applies to the shoes");
        context.assertTrue(enchantment.value().isPrimaryItem(shoes), "offered for the shoes at the table");
        for (ItemStack other : List.of(new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.STICK))) {
            context.assertTrue(!enchantment.value().isSupportedItem(other), "not on " + other);
        }
        context.assertTrue(enchantment.value().getMaxLevel() == 1, "max level 1");
        context.assertTrue(enchantment.isIn(EnchantmentTags.IN_ENCHANTING_TABLE), "in the enchanting table");
        var table = context.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT)
                .getEntryList(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow();
        List<EnchantmentLevelEntry> offered = EnchantmentHelper.getPossibleEntries(20, shoes, table.stream());
        context.assertTrue(offered.stream().anyMatch(e -> e.enchantment.matchesKey(JumpShoes.DOUBLE_JUMP)), "possible on the shoes at level 20");
        List<EnchantmentLevelEntry> onBoots = EnchantmentHelper.getPossibleEntries(20, new ItemStack(Items.DIAMOND_BOOTS), table.stream());
        context.assertTrue(onBoots.stream().noneMatch(e -> e.enchantment.matchesKey(JumpShoes.DOUBLE_JUMP)), "never on other boots");
        context.complete();
    }

    /** 1, 2, 3 in rhythm, then 1 again; a late jump, a jump on the spot or a reset starts over. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tripleJumpChainNeedsRhythmAndSpeed(TestContext context) {
        TripleJumpChain chain = new TripleJumpChain();
        double run = 0.2;
        context.assertTrue(chain.jump(0, run) == 1, "first jump");
        chain.onGround(15);
        context.assertTrue(chain.jump(16, run) == 2, "second, right after landing");
        chain.onGround(35);
        context.assertTrue(chain.jump(35 + TripleJumpChain.WINDOW_TICKS, run) == 3, "third, at the end of the window");
        chain.onGround(60);
        context.assertTrue(chain.jump(61, run) == 1, "after the third, the chain starts over");

        chain.onGround(80);
        context.assertTrue(chain.jump(80 + TripleJumpChain.WINDOW_TICKS + 1, run) == 1, "too late: first again");
        chain.onGround(100);
        context.assertTrue(chain.jump(101, 0.0) == 1, "on the spot: first again");
        chain.onGround(120);
        chain.reset();
        context.assertTrue(chain.jump(121, run) == 1, "after a reset (hit, water, sneak): first again");
        context.assertTrue(TripleJumpChain.boost(1) == 1.0 && TripleJumpChain.boost(2) > 1.0 && TripleJumpChain.boost(3) > TripleJumpChain.boost(2),
                "each jump of the chain higher");
        context.complete();
    }

    /** The server accepts one double jump per air time, only with the enchanted shoes. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void doubleJumpOnlyWithTheEnchantment(TestContext context) {
        PlayerEntity plain = shod(context, false);
        context.assertTrue(!new JumpShoesState().accept(plain, JumpShoes.Action.DOUBLE_JUMP, null), "plain shoes: no double jump");
        context.assertTrue(!JumpShoes.hasDoubleJump(plain), "plain shoes");

        PlayerEntity enchanted = shod(context, true);
        JumpShoesState state = new JumpShoesState();
        context.assertTrue(JumpShoes.hasDoubleJump(enchanted), "enchanted shoes");
        context.assertTrue(state.accept(enchanted, JumpShoes.Action.DOUBLE_JUMP, null), "enchanted: a double jump");
        context.assertTrue(!state.accept(enchanted, JumpShoes.Action.DOUBLE_JUMP, null), "only one per air time");

        PlayerEntity barefoot = context.createMockPlayer(GameMode.SURVIVAL);
        context.assertTrue(!new JumpShoesState().accept(barefoot, JumpShoes.Action.JUMP_3, null), "no shoes: nothing");
        context.complete();
    }

    /** The kick goes where the player looks, but always away from the wall. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wallKickGoesAwayTowardTheLook(TestContext context) {
        // wall on the east: away is west (-1, 0)
        Vec3d into = JumpShoes.kickDirection(1, 0, -1, 0);
        context.assertTrue(into.distanceTo(new Vec3d(-1, 0, 0)) < 1.0E-6, "looking into the wall: straight away, got " + into);
        Vec3d up = JumpShoes.kickDirection(0, 0, -1, 0);
        context.assertTrue(up.distanceTo(new Vec3d(-1, 0, 0)) < 1.0E-6, "looking straight up: straight away, got " + up);
        Vec3d back = JumpShoes.kickDirection(-0.6, 0.8, -1, 0);
        context.assertTrue(back.distanceTo(new Vec3d(-0.6, 0, 0.8)) < 1.0E-6, "looking away: where he looks, got " + back);
        Vec3d along = JumpShoes.kickDirection(0, 1, -1, 0);
        context.assertTrue(Math.abs(-along.x - JumpShoes.MIN_AWAY) < 1.0E-6 && along.z > 0, "along the wall: away and sideways, got " + along);
        Vec3d diagonal = JumpShoes.kickDirection(0.7, -0.7, -1, 0);
        context.assertTrue(-diagonal.x >= JumpShoes.MIN_AWAY - 1.0E-6 && diagonal.z < 0, "half into the wall: away, keeping its side, got " + diagonal);
        for (Vec3d v : List.of(into, back, along, diagonal)) {
            context.assertTrue(Math.abs(v.length() - 1) < 1.0E-6, "a unit direction, got " + v);
        }
        context.complete();
    }

    /** Sliding down a wall cancels the fall; once off it, the fall counts from where he left it. Ladders are no walls. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void noFallDamageAfterAWallSlide(TestContext context) {
        for (int y = 1; y <= 6; y++) {
            for (int z = 1; z <= 3; z++) context.setBlockState(new BlockPos(4, y, z), Blocks.STONE);
            context.setBlockState(new BlockPos(1, y, 2), Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.EAST));
        }
        PlayerEntity player = shod(context, false);
        Vec3d at = context.getAbsolute(new Vec3d(3.69, 4, 2.5));
        player.setPosition(at);
        player.setOnGround(false);
        context.assertTrue(JumpShoes.wallSide(player, JumpShoes.CLIENT_REACH, new Vec3d(1, 0, 0)) == Direction.EAST, "the wall is on the east");

        JumpShoesState state = new JumpShoesState();
        context.assertTrue(state.accept(player, JumpShoes.Action.SLIDE_START, Direction.EAST), "slide accepted");
        context.assertTrue(!new JumpShoesState().accept(player, JumpShoes.Action.SLIDE_START, Direction.WEST), "no wall on the west");
        player.fallDistance = 12;
        state.tick(player);
        context.assertTrue(player.fallDistance == 0, "sliding: no fall, got " + player.fallDistance);

        for (int i = 0; i < 5; i++) { // slides slowly down
            player.setPosition(player.getPos().add(0, -JumpShoes.SLIDE_SPEED, 0));
            player.fallDistance += (float) JumpShoes.SLIDE_SPEED;
            state.tick(player);
        }
        context.assertTrue(player.fallDistance == 0, "still sliding: no fall, got " + player.fallDistance);

        double leftAt = player.getY();
        context.assertTrue(state.accept(player, JumpShoes.Action.SLIDE_STOP, null), "slide stopped");
        player.setPosition(player.getPos().add(0, -2, 0));
        player.fallDistance = 20;
        for (int i = 0; i < 5; i++) state.tick(player);
        context.assertTrue(Math.abs(player.fallDistance - (leftAt - player.getY())) < 1.0E-4,
                "the fall counts from the wall: 2 blocks, got " + player.fallDistance);
        float health = player.getHealth();
        player.handleFallDamage(player.fallDistance, 1.0f, player.getDamageSources().fall());
        context.assertTrue(player.getHealth() == health, "a 2-block fall: no damage");

        PlayerEntity climber = shod(context, false);
        climber.setPosition(context.getAbsolute(new Vec3d(1.54, 4, 2.5)));
        context.assertTrue(!JumpShoes.touchesWall(climber, Direction.WEST, JumpShoes.SERVER_REACH), "a ladder is no wall");
        context.complete();
    }
    /** Behind Shift: the triple jump and the wall jump; the double jump only on enchanted shoes. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tooltipTellsTheDoubleJumpOnlyWhenEnchanted(TestContext context) {
        String key = "tooltip.steveparty.triple_jump_shoes.use.";
        Set<String> plain = tooltipKeys(new ItemStack(ModItems.TRIPLE_JUMP_SHOES));
        context.assertTrue(plain.contains(key + "triple") && plain.contains(key + "wall"), "triple and wall jump told, got " + plain);
        context.assertTrue(!plain.contains(key + "double"), "no double jump on plain shoes");
        ItemStack enchanted = new ItemStack(ModItems.TRIPLE_JUMP_SHOES);
        enchanted.addEnchantment(doubleJump(context), 1);
        context.assertTrue(tooltipKeys(enchanted).contains(key + "double"), "double jump told on enchanted shoes");
        context.complete();
    }

    private static Set<String> tooltipKeys(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        Tooltips.forTests(true);
        try {
            ItemTips.append(stack, lines);
        } finally {
            Tooltips.forTests(null);
        }
        Set<String> keys = new HashSet<>();
        for (Text line : lines) collect(line, keys);
        return keys;
    }

    private static void collect(Text text, Set<String> keys) {
        if (text.getContent() instanceof TranslatableTextContent t) {
            keys.add(t.getKey());
            for (Object arg : t.getArgs()) if (arg instanceof Text a) collect(a, keys);
        }
        for (Text sibling : text.getSiblings()) collect(sibling, keys);
    }
}
