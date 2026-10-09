package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.components.MiniGamePageRef;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGameIntroShot;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageImage;
import fr.lordfinn.steveparty.minigame.MiniGamePageImages;
import fr.lordfinn.steveparty.minigame.MiniGamePageNetworking;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePagesState;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGameText;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.io.IOException;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The mini-game pages: their content lives on the server under the page's id (saved, shared by the linked copies,
 * copied when a page is unlinked), only builders write on them, their pictures are checked and stored, and a page
 * says which teams its mini-game accepts.
 */
public class MiniGamePageGameTests implements FabricGameTest {
    /** Counts the changes told to {@link MiniGamePages#CHANGED} (registered once for the whole run). */
    private static final AtomicInteger CHANGES = new AtomicInteger();
    private static volatile UUID lastChanged;

    static {
        MiniGamePages.CHANGED.register((server, page) -> {
            CHANGES.incrementAndGet();
            lastChanged = page.id();
        });
    }

    /** A page in the player's main hand, opened once: it has its id. */
    private static ItemStack openedPage(ServerPlayerEntity player) {
        ItemStack page = new ItemStack(ModItems.MINI_GAME_PAGE);
        player.setStackInHand(Hand.MAIN_HAND, page);
        MiniGamePageNetworking.open(player, Hand.MAIN_HAND);
        return player.getMainHandStack();
    }

    private static MiniGamePagePayloads.Edit edit(UUID page, String title, String description, List<MiniGameFormat> formats) {
        return new MiniGamePagePayloads.Edit(Hand.MAIN_HAND, page, title, description, formats);
    }

    /** A picture with details everywhere (gradient and noise), as a photo would be. */
    private static BufferedImage photo(int width, int height, boolean transparent) {
        BufferedImage image = new BufferedImage(width, height, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setPaint(new GradientPaint(0, 0, Color.ORANGE, width, height, Color.BLUE));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        Random random = new Random(42);
        for (int i = 0; i < width * height / (transparent ? 400 : 8); i++) {
            image.setRGB(random.nextInt(width), random.nextInt(height), (transparent ? 0x80000000 : 0xFF000000) | random.nextInt(0xFFFFFF));
        }
        return image;
    }

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------------------------ saved state

    /** Everything a page says goes through the saved state and comes back the same. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pageDataRoundTripsThroughTheSavedState(TestContext context) {
        UUID id = UUID.randomUUID(), uploader = UUID.randomUUID();
        MiniGamePageData page = new MiniGamePageData(id, "Course de cochons", "Premier arrivé gagne.\nAttention à la lave !",
                new MiniGamePageImage("0123456789abcdef0123456789abcdef", 640, 360, 45678, "LordFinn", uploader),
                List.of(MiniGameFormat.freeForAll(2, 6), MiniGameFormat.teams(false, MiniGameFormat.Side.exactly(1), MiniGameFormat.Side.exactly(1),
                        MiniGameFormat.Side.atLeast(2))),
                List.of(new MiniGamePipeLink(GlobalPos.create(World.NETHER, new BlockPos(4, 70, -12)), Direction.EAST,
                        MiniGamePipeRole.TEAM_B)));
        MiniGamePageData blank = MiniGamePageData.empty(UUID.randomUUID());

        MiniGamePagesState state = new MiniGamePagesState();
        state.put(page);
        state.put(blank.withTexts("Sans image", ""));
        NbtCompound saved = state.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
        MiniGamePagesState loaded = MiniGamePagesState.fromNbt(saved);

        context.assertEquals(loaded.get(id), page, "the page comes back the same");
        context.assertEquals(loaded.get(id).pipeLinks().get(0).mouth().dimension(), World.NETHER, "pipe mouths keep their dimension");
        context.assertEquals(loaded.get(blank.id()).title(), "Sans image", "the second page too");
        context.assertTrue(loaded.get(blank.id()).image() == null, "no picture stays no picture");
        context.assertEquals(loaded.all().size(), 2, "two pages");
        context.assertTrue(loaded.isImageUsed("0123456789abcdef0123456789abcdef", null), "the picture is known as used");
        context.assertTrue(!loaded.isImageUsed("0123456789abcdef0123456789abcdef", id), "but by no other page");

        // The introduction's shots, the roles sent at random and the text markup come back too
        MiniGamePageData advanced = page.withTexts("Titre", "&lRègles&r : &cattention&r && bonne chance")
                .withRandom(MiniGamePipeRole.PLAYERS, true)
                .withIntro(List.of(new MiniGameIntroShot(World.OVERWORLD, new Vec3d(1.5, 80, -3.25), 45, 30, 60, "Vue d'&6ensemble"),
                        new MiniGameIntroShot(World.NETHER, new Vec3d(0, 64, 0), -400, 120, 100000, "")));
        MiniGamePageData reloaded = MiniGamePageData.fromNbt(advanced.toNbt());
        context.assertEquals(reloaded, advanced, "the advanced page comes back the same");
        context.assertEquals(advanced.toNbt().getInt("Format"), MiniGamePageData.FORMAT, "the saved form says its version");
        context.assertEquals(reloaded.intro().size(), 2, "two shots");
        context.assertTrue(reloaded.intro().get(1).pitch() == 90 && reloaded.intro().get(1).ticks() == MiniGameIntroShot.MAX_TICKS,
                "a shot keeps sane values");
        context.assertTrue(page.intro().isEmpty() && page.randomRoles().isEmpty(), "none by default");
        NbtCompound old = page.toNbt();
        old.remove("Format");
        context.assertEquals(MiniGamePageData.fromNbt(old), page, "a page saved before these were added loads as it was");
        Text shown = MiniGameText.parse(advanced.description());
        context.assertEquals(shown.getString(), "Règles : attention & bonne chance", "the markup is not shown");
        context.assertTrue(shown.getSiblings().get(0).getStyle().isBold() && !shown.getSiblings().get(1).getStyle().isBold(), "&l bold until &r");
        context.assertEquals(shown.getSiblings().get(2).getStyle().getColor(), TextColor.fromFormatting(Formatting.RED), "&c red");
        context.assertEquals(MiniGameText.wrap("un deux trois quatre cinq six", 10), List.of("un deux", "trois", "quatre", "cinq six"),
                "long lines are cut at the spaces");
        PacketByteBuf advancedBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        MiniGamePageData.PACKET_CODEC.encode(advancedBuf, advanced);
        context.assertEquals(MiniGamePageData.PACKET_CODEC.decode(advancedBuf), advanced, "and crosses the network the same");

        // The network form too
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        MiniGamePageData.PACKET_CODEC.encode(buf, page);
        context.assertEquals(MiniGamePageData.PACKET_CODEC.decode(buf), page, "the page crosses the network the same");
        context.complete();
    }

    /** Whatever is given, a page keeps sane values: short texts, at least one mode, min ≤ max. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pageDataIsCleaned(TestContext context) {
        MiniGamePageData page = new MiniGamePageData(UUID.randomUUID(), "  Titre\n§cpiégé " + "x".repeat(100), "a\n".repeat(30),
                null, List.of(), null);
        context.assertTrue(page.title().length() <= MiniGamePageData.MAX_TITLE_LENGTH, "title cut");
        context.assertTrue(!page.title().contains("\n") && !page.title().contains("§"), "title on one line, no formatting code");
        context.assertTrue(page.description().split("\n").length <= MiniGamePageData.MAX_DESCRIPTION_LINES, "description lines cut");
        context.assertEquals(page.formats(), List.of(MiniGameFormat.freeForAll(1, MiniGameFormat.Side.INFINITE)), "no format: free for all, any number");
        context.assertEquals(new MiniGameFormat.Side(9, 3), new MiniGameFormat.Side(9, 9), "a side's max raised to its min");
        context.assertTrue(page.pipeLinks().isEmpty(), "no pipe link yet");
        context.complete();
    }

    // ------------------------------------------------------------------ linked copies

    /** A copy carries the same id: what is written through one shows on the other, and on their names. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void linkedCopiesShareEdits(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.CREATIVE);
        try {
            ItemStack page = openedPage(player);
            UUID id = MiniGamePages.idOf(page);
            context.assertTrue(id != null, "opening a page gives it its id");
            context.assertTrue(MiniGamePages.find(server, id).isEmpty(), "nothing is stored for a page nothing was written on");

            ItemStack copy = MiniGamePages.linkedCopy(page, 1);
            context.assertEquals(MiniGamePages.idOf(copy), id, "the copy is the same page");
            context.assertTrue(page.get(ModComponents.MINI_GAME_PAGE).linked() && copy.get(ModComponents.MINI_GAME_PAGE).linked(),
                    "both are marked as linked");

            int changes = CHANGES.get();
            context.assertTrue(MiniGamePageNetworking.edit(player, edit(id, "Course", "Vite !", List.of(TWO_V_TWO))),
                    "the edit is accepted");
            context.assertEquals(CHANGES.get(), changes + 1, "the change event fired once");
            context.assertEquals(lastChanged, id, "for this page");
            MiniGamePageData seenByCopy = MiniGamePages.of(server, copy);
            context.assertEquals(seenByCopy.title(), "Course", "the copy shows the title");
            context.assertEquals(seenByCopy.description(), "Vite !", "and the description");
            context.assertEquals(seenByCopy.formats(), List.of(TWO_V_TWO), "and the formats");

            context.assertEquals(page.getName().getString(), "Course", "the edited item is named after its title");
            context.assertTrue(MiniGamePages.refresh(server, copy), "the copy's title is brought up to date");
            context.assertEquals(copy.getName().getString(), "Course", "the copy is named after the title too");
            context.assertEquals(MiniGamePages.displayName(server, copy).getString(), "Course", "the roulette shows the title");

            // The same edit again changes nothing
            MiniGamePageNetworking.edit(player, edit(id, "Course", "Vite !", List.of(TWO_V_TWO)));
            context.assertEquals(CHANGES.get(), changes + 1, "no event for no change");

            // The « Copy » button: a linked copy in the inventory
            player.getInventory().clear();
            player.setStackInHand(Hand.MAIN_HAND, page);
            context.assertTrue(MiniGamePageNetworking.action(player, new MiniGamePagePayloads.Action(Hand.MAIN_HAND, id,
                    MiniGamePagePayloads.Action.Kind.COPY)), "copy accepted");
            int pages = 0;
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (id.equals(MiniGamePages.idOf(stack))) pages += stack.getCount();
            }
            context.assertEquals(pages, 2, "the page and its copy");
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    /** Page + paper in a grid: as many more linked copies as sheets, with an id even for a page never opened. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void craftingWithPaperGivesLinkedCopies(TestContext context) {
        ItemStack fresh = new ItemStack(ModItems.MINI_GAME_PAGE);
        ItemStack paper = new ItemStack(Items.PAPER);
        CraftingRecipeInput input = CraftingRecipeInput.create(3, 1, List.of(paper, fresh, paper));
        Optional<RecipeEntry<CraftingRecipe>> recipe = context.getWorld().getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
        context.assertTrue(recipe.isPresent(), "page + paper is a recipe");
        ItemStack result = recipe.get().value().craft(input, context.getWorld().getRegistryManager());
        context.assertTrue(result.isOf(ModItems.MINI_GAME_PAGE) && result.getCount() == 3, "the page and two copies, got " + result);
        MiniGamePageRef ref = result.get(ModComponents.MINI_GAME_PAGE);
        context.assertTrue(ref != null && ref.linked(), "linked copies with an id");
        context.assertTrue(fresh.get(ModComponents.MINI_GAME_PAGE) == null, "the page in the grid is left alone");

        ItemStack written = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID id = MiniGamePages.ensureId(written);
        CraftingRecipeInput second = CraftingRecipeInput.create(2, 1, List.of(written, paper));
        ItemStack copies = context.getWorld().getServer().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, second, context.getWorld())
                .map(entry -> entry.value().craft(second, context.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
        context.assertEquals(MiniGamePages.idOf(copies), id, "copies keep the page's id");
        context.assertEquals(copies.getCount(), 2, "one sheet: the page and one copy");

        // Two pages in the grid: not this recipe
        CraftingRecipeInput two = CraftingRecipeInput.create(3, 1, List.of(written, fresh, paper));
        context.assertTrue(context.getWorld().getServer().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, two, context.getWorld())
                .isEmpty(), "two pages are not copied at once");
        context.complete();
    }

    /** Unlinking gives the item an id of its own with the same content: from then on the two pages live apart. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unlinkedCopyLivesItsOwnLife(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        try {
            ItemStack page = openedPage(player);
            UUID id = MiniGamePages.idOf(page);
            MiniGamePageNetworking.edit(player, edit(id, "Bataille", "Dernier debout.", List.of(MiniGameFormat.freeForAll(2, 8))));
            context.assertEquals(MiniGamePages.setImage(server, id, MiniGamePageImages.prepare(photo(320, 180, false)), "LordFinn", player.getUuid()),
                    MiniGamePages.ImageResult.SAVED, "picture saved");
            ItemStack original = MiniGamePages.linkedCopy(page, 1);

            context.assertTrue(MiniGamePageNetworking.action(player, new MiniGamePagePayloads.Action(Hand.MAIN_HAND, id,
                    MiniGamePagePayloads.Action.Kind.UNLINK)), "unlink accepted");
            ItemStack unlinked = player.getMainHandStack();
            UUID newId = MiniGamePages.idOf(unlinked);
            context.assertTrue(newId != null && !newId.equals(id), "the unlinked page has another id");
            context.assertTrue(!unlinked.get(ModComponents.MINI_GAME_PAGE).linked(), "and is no longer marked as linked");
            MiniGamePageData copy = MiniGamePages.get(server, newId);
            context.assertEquals(copy.withId(id), MiniGamePages.get(server, id), "same content: texts, picture, settings");
            context.assertTrue(MiniGamePages.imageBytes(server, copy.image().hash()) != null, "the picture is still there");

            MiniGamePageNetworking.edit(player, edit(newId, "Bataille navale", "", List.of(DUEL)));
            context.assertEquals(MiniGamePages.of(server, original).title(), "Bataille", "the other page did not change");
            context.assertEquals(MiniGamePages.of(server, unlinked).title(), "Bataille navale", "the unlinked one did");

            // Removing the picture of one leaves the other's (the file is shared while a page shows it)
            String hash = copy.image().hash();
            context.assertTrue(MiniGamePages.clearImage(server, newId), "picture removed");
            context.assertTrue(MiniGamePages.imageBytes(server, hash) != null, "still shown by the first page: kept");
            context.assertTrue(MiniGamePages.clearImage(server, id), "picture removed there too");
            context.assertTrue(MiniGamePages.imageBytes(server, hash) == null, "no page shows it any more: gone");
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    /** Of a stack of new pages, only the one opened becomes a page: the others stay blank. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void openingAStackWritesOnePage(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        try {
            player.getInventory().clear();
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.MINI_GAME_PAGE, 5));
            MiniGamePageNetworking.open(player, Hand.MAIN_HAND);
            ItemStack held = player.getMainHandStack();
            context.assertTrue(held.getCount() == 1 && MiniGamePages.idOf(held) != null, "one page with an id in hand, got " + held);
            int blanks = 0;
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (MiniGamePages.isPage(stack) && MiniGamePages.idOf(stack) == null) blanks += stack.getCount();
            }
            context.assertEquals(blanks, 4, "the four others stay blank, in the inventory");

            // In survival the « Copy » button takes a sheet of paper
            UUID id = MiniGamePages.idOf(held);
            MiniGamePagePayloads.Action copy = new MiniGamePagePayloads.Action(Hand.MAIN_HAND, id, MiniGamePagePayloads.Action.Kind.COPY);
            context.assertTrue(!MiniGamePageNetworking.action(player, copy), "no paper: no copy");
            player.getInventory().insertStack(new ItemStack(Items.PAPER, 2));
            context.assertTrue(MiniGamePageNetworking.action(player, copy), "with paper: copied");
            context.assertEquals(player.getInventory().count(Items.PAPER), 1, "one sheet used");
            int linked = 0;
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (id.equals(MiniGamePages.idOf(stack))) linked += stack.getCount();
            }
            context.assertEquals(linked, 2, "the page and its linked copy");
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    // ------------------------------------------------------------------ rights

    /** Writing takes the right to build, and the page in hand. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void onlyBuildersHoldingThePageCanEdit(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        try {
            ItemStack page = openedPage(player);
            UUID id = MiniGamePages.idOf(page);
            context.assertTrue(MiniGamePages.canEdit(player), "survival: can edit");
            context.assertTrue(MiniGamePageNetworking.edit(player, edit(id, "Écrit", "", MiniGameFormat.GALLERY)), "survival writes");
            byte[] picture = MiniGamePageImages.prepare(photo(200, 100, false));

            for (GameMode mode : new GameMode[]{GameMode.ADVENTURE, GameMode.SPECTATOR}) {
                player.changeGameMode(mode);
                context.assertTrue(!MiniGamePages.canEdit(player), mode + ": can't edit");
                context.assertTrue(!MiniGamePageNetworking.edit(player, edit(id, "Piraté", "", MiniGameFormat.GALLERY)),
                        mode + ": edit refused");
                for (MiniGamePagePayloads.Action.Kind kind : MiniGamePagePayloads.Action.Kind.values()) {
                    context.assertTrue(!MiniGamePageNetworking.action(player, new MiniGamePagePayloads.Action(Hand.MAIN_HAND, id, kind)),
                            mode + ": " + kind + " refused");
                }
                MiniGamePageNetworking.upload(player, new MiniGamePagePayloads.Upload(Hand.MAIN_HAND, id, 0, 1, picture));
                context.assertEquals(MiniGamePages.get(server, id).title(), "Écrit", mode + ": the page did not change");
                context.assertTrue(MiniGamePages.get(server, id).image() == null, mode + ": no picture got in");
                context.assertEquals(MiniGamePages.idOf(player.getMainHandStack()), id, mode + ": the item did not change");
            }

            // A new page opened by someone who can't write gets no id
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.MINI_GAME_PAGE));
            MiniGamePageNetworking.open(player, Hand.MAIN_HAND);
            context.assertTrue(MiniGamePages.idOf(player.getMainHandStack()) == null, "reading a blank page leaves it blank");

            // A builder, but not holding that page: refused
            player.changeGameMode(GameMode.CREATIVE);
            context.assertTrue(!MiniGamePageNetworking.edit(player, edit(id, "Piraté", "", MiniGameFormat.GALLERY)),
                    "a page that is not in hand can't be written on");
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            context.assertTrue(!MiniGamePageNetworking.edit(player, edit(id, "Piraté", "", MiniGameFormat.GALLERY)),
                    "nor with an empty hand");
            context.assertEquals(MiniGamePages.get(server, id).title(), "Écrit", "still as written");
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    // ------------------------------------------------------------------ pictures

    /** A big photo is shrunk and compressed by the sender, within the limits, proportions kept. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void picturesAreShrunkAndCompressed(TestContext context) {
        byte[] wide = MiniGamePageImages.prepare(photo(1920, 1080, false));
        context.assertTrue(wide != null && MiniGamePageImages.isAcceptable(wide), "a full HD photo is made acceptable");
        MiniGamePageImages.Info info = MiniGamePageImages.inspect(wide);
        context.assertTrue(info.width() == MiniGamePageImages.MAX_WIDTH && info.height() == MiniGamePageImages.MAX_HEIGHT,
                "16:9 fills the frame, got " + info);
        context.assertEquals(info.format(), MiniGamePageImages.Format.JPEG, "an opaque photo is a JPEG");

        MiniGamePageImages.Info tall = MiniGamePageImages.inspect(MiniGamePageImages.prepare(photo(1000, 2000, false)));
        context.assertTrue(tall.height() == MiniGamePageImages.MAX_HEIGHT && tall.width() == MiniGamePageImages.MAX_HEIGHT / 2,
                "a tall picture keeps its proportions, got " + tall);

        MiniGamePageImages.Info small = MiniGamePageImages.inspect(MiniGamePageImages.prepare(photo(64, 48, false)));
        context.assertTrue(small.width() == 64 && small.height() == 48, "a small picture is not enlarged, got " + small);

        byte[] transparent = MiniGamePageImages.prepare(photo(800, 450, true));
        context.assertTrue(MiniGamePageImages.isAcceptable(transparent), "a transparent picture is acceptable too");
        context.assertEquals(MiniGamePageImages.inspect(transparent).format(), MiniGamePageImages.Format.PNG, "and stays a PNG");
        context.assertTrue(MiniGamePageImages.decode(transparent) != null, "what was written can be read back");
        context.complete();
    }

    /** The server checks what it is sent (weight, size, format), stores it with the sender's name, and serves it back. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void picturesAreCheckedAndStored(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        UUID id = UUID.randomUUID();
        byte[] heavy = new byte[MiniGamePageImages.MAX_BYTES + 1];
        context.assertEquals(MiniGamePages.setImage(server, id, heavy, "x", null), MiniGamePages.ImageResult.TOO_HEAVY, "too many bytes");
        context.assertEquals(MiniGamePages.setImage(server, id, "not a picture at all".getBytes(), "x", null),
                MiniGamePages.ImageResult.UNREADABLE, "not a picture");
        byte[] fakePng = new byte[64];
        fakePng[0] = (byte) 0x89;
        fakePng[1] = 'P';
        fakePng[2] = 'N';
        fakePng[3] = 'G';
        context.assertEquals(MiniGamePages.setImage(server, id, fakePng, "x", null), MiniGamePages.ImageResult.UNREADABLE, "a PNG in name only");
        context.assertEquals(MiniGamePages.setImage(server, id, png(photo(800, 200, false)), "x", null),
                MiniGamePages.ImageResult.TOO_LARGE, "wider than the frame");
        context.assertTrue(MiniGamePages.find(server, id).isEmpty(), "nothing got in");

        // Sent in pieces by a player, as the editor does
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        try {
            ItemStack page = openedPage(player);
            UUID held = MiniGamePages.idOf(page);
            byte[] picture = MiniGamePageImages.prepare(photo(1280, 720, false));
            int chunk = MiniGamePagePayloads.CHUNK_SIZE;
            int total = (picture.length + chunk - 1) / chunk;
            context.assertTrue(total > 1, "the picture needs several pieces (" + picture.length + " bytes)");
            for (int index = 0; index < total; index++) {
                context.assertTrue(MiniGamePages.get(server, held).image() == null, "no picture before the last piece");
                MiniGamePageNetworking.upload(player, new MiniGamePagePayloads.Upload(Hand.MAIN_HAND, held, index, total,
                        Arrays.copyOfRange(picture, index * chunk, Math.min(picture.length, (index + 1) * chunk))));
            }
            MiniGamePageImage stored = MiniGamePages.get(server, held).image();
            context.assertTrue(stored != null, "the picture is on the page");
            context.assertEquals(stored.uploader(), player.getGameProfile().getName(), "with the name of who sent it");
            context.assertEquals(stored.uploaderId(), player.getUuid(), "and their id");
            context.assertEquals(stored.hash(), MiniGamePageImages.hash(picture), "under the hash of its bytes");
            context.assertTrue(stored.width() <= MiniGamePageImages.MAX_WIDTH && stored.height() <= MiniGamePageImages.MAX_HEIGHT, "within the frame");
            context.assertTrue(Arrays.equals(MiniGamePages.imageBytes(server, stored.hash()), picture), "served back byte for byte");

            // Pieces out of order, or more than announced: refused
            MiniGamePageNetworking.upload(player, new MiniGamePagePayloads.Upload(Hand.MAIN_HAND, held, 1, 2, new byte[10]));
            MiniGamePageNetworking.upload(player, new MiniGamePagePayloads.Upload(Hand.MAIN_HAND, held, 0, MiniGamePagePayloads.MAX_CHUNKS + 1, new byte[10]));
            context.assertEquals(MiniGamePages.get(server, held).image(), stored, "a broken upload changes nothing");

            // The operator removes it
            context.assertTrue(MiniGamePages.clearImage(server, held), "cleared");
            context.assertTrue(MiniGamePages.get(server, held).image() == null, "the page has no picture");
            context.assertTrue(MiniGamePages.imageBytes(server, stored.hash()) == null, "and its bytes are gone");
            context.assertTrue(!MiniGamePages.clearImage(server, held), "nothing left to clear");
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    private static final MiniGameFormat TWO_V_TWO = MiniGameFormat.teams(false, MiniGameFormat.Side.exactly(2), MiniGameFormat.Side.exactly(2));
    private static final MiniGameFormat DUEL = MiniGameFormat.teams(false, MiniGameFormat.Side.exactly(1), MiniGameFormat.Side.exactly(1));
}
