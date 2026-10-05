package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faces of a die forged in the Dice Forge. A die carrying this component rolls one of these faces instead of the
 * default 1..10 range, each face being as likely as its weight (the number of that face placed in the forge).
 * <p>
 * Besides the numbers (normal, premium, cursed, 0 and the blank side), a face may be a coin face (the roller gains
 * coins), a debt face (the roller loses coins) or the swap face (the token swaps places with another one): see
 * {@link fr.lordfinn.steveparty.dice.DiceOutcome} for what a roll does.
 */
public record DiceFacesComponent(List<DiceFace> faces) {
    public static final int MAX_FACES = 12;
    public static final Codec<DiceFacesComponent> CODEC = Codec.list(DiceFace.CODEC, 1, MAX_FACES)
            .xmap(DiceFacesComponent::new, DiceFacesComponent::faces);

    /**
     * Registered here rather than in {@link ModComponents} so the Dice Forge feature stays self-contained;
     * {@link #initialize()} is called from the dice forge block class initialisation (mod init time).
     */
    public static final ComponentType<DiceFacesComponent> TYPE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Steveparty.id("dice-faces"),
            ComponentType.<DiceFacesComponent>builder().codec(CODEC).build());

    public DiceFacesComponent {
        faces = List.copyOf(faces);
    }

    /** Forces the class (and so the component type) to be registered. */
    public static void initialize() {
        // Class loading registers TYPE
    }

    // ------------------------------------------------------------------ dice API

    /** @return true if the stack is a forged die carrying its own faces. */
    public static boolean hasFaces(ItemStack stack) {
        DiceFacesComponent component = stack == null ? null : stack.get(TYPE);
        return component != null && !component.faces().isEmpty();
    }

    /**
     * Rolls the die: one of the forged faces when the stack carries this component, otherwise the classic
     * uniform {@link DiceEntity#MIN}..{@link DiceEntity#MAX} roll (plain dice keep working as before).
     */
    public static int rollFace(@Nullable ItemStack stack, Random random) {
        DiceFace face = rollDiceFace(stack, random);
        return face == null ? random.nextBetween(DiceEntity.MIN, DiceEntity.MAX) : face.value();
    }

    /** Same as {@link #rollFace(ItemStack, Random)} but also tells which face was rolled (null for a plain die). */
    public static @Nullable DiceFace rollDiceFace(@Nullable ItemStack stack, Random random) {
        DiceFacesComponent component = stack == null ? null : stack.get(TYPE);
        if (component == null || component.faces().isEmpty()) return null;
        int roll = random.nextInt(component.totalWeight());
        for (DiceFace face : component.faces()) {
            roll -= face.weight();
            if (roll < 0) return face;
        }
        return component.faces().getLast();
    }

    /** The faces a die rolls: its forged faces, or {@link DiceEntity#MIN}..{@link DiceEntity#MAX} for a plain die. */
    public static List<DiceFace> facesOf(@Nullable ItemStack stack) {
        DiceFacesComponent component = stack == null ? null : stack.get(TYPE);
        if (component != null && !component.faces().isEmpty()) return component.faces();
        List<DiceFace> faces = new ArrayList<>();
        for (int value = DiceEntity.MIN; value <= DiceEntity.MAX; value++) faces.add(new DiceFace(Kind.NORMAL, value));
        return faces;
    }

    /** Rolls the die: one of its faces by weight (a plain die: a number of {@link #facesOf}). Never null. */
    public static DiceFace roll(@Nullable ItemStack stack, Random random) {
        DiceFace face = rollDiceFace(stack, random);
        return face != null ? face : new DiceFace(Kind.NORMAL, random.nextBetween(DiceEntity.MIN, DiceEntity.MAX));
    }

    /** Sum of the face weights (at least 1). */
    public int totalWeight() {
        int total = 0;
        for (DiceFace face : faces) total += face.weight();
        return Math.max(1, total);
    }

    /**
     * Builds the die produced by the forge for these face stacks (empty stacks are ignored): each stack count is the
     * weight of its face, and the same face given several times adds up its weights. Faces are sorted so the same
     * faces and weights always give stackable dice.
     *
     * @return the die, or {@link ItemStack#EMPTY} if no valid face was given
     */
    public static ItemStack createDie(List<ItemStack> faceStacks) {
        java.util.Map<DiceFace, Integer> weights = new java.util.LinkedHashMap<>();
        for (ItemStack stack : faceStacks) {
            if (stack == null || stack.isEmpty()) continue;
            DiceFace.fromItem(stack.getItem()).ifPresent(face -> weights.merge(face, stack.getCount(), Integer::sum));
        }
        if (weights.isEmpty()) return ItemStack.EMPTY;
        List<DiceFace> faces = new ArrayList<>();
        weights.forEach((face, weight) -> faces.add(face.withWeight(weight)));
        faces.sort(DiceFace.ORDER);
        if (faces.size() > MAX_FACES) faces.subList(MAX_FACES, faces.size()).clear();

        DiceFacesComponent component = new DiceFacesComponent(faces);
        ItemStack die = new ItemStack(ModItems.DEFAULT_DICE);
        die.set(TYPE, component);
        die.set(DataComponentTypes.ITEM_NAME,
                Text.translatableWithFallback("item.steveparty.forged_dice", "Forged Die"));
        return die;
    }

    /** One tooltip line listing the faces and their weights, e.g. "Faces: 1, 3 ×10, 5★, 2☠, –". */
    public Text describe() {
        MutableText list = Text.empty();
        for (int i = 0; i < faces.size(); i++) {
            if (i > 0) list.append(Text.literal(", ").formatted(Formatting.GRAY));
            list.append(faces.get(i).asText());
            if (faces.get(i).weight() > 1) {
                list.append(Text.literal(" ×" + faces.get(i).weight()).formatted(Formatting.DARK_GRAY));
            }
        }
        return Text.translatableWithFallback("tooltip.steveparty.dice_faces", "Faces: %s", list)
                .styled(style -> style.withItalic(false).withColor(Formatting.GRAY));
    }

    // ------------------------------------------------------------------ face

    /**
     * @param weight how likely this face is compared to the others (the number of that face placed in the forge)
     */
    public record DiceFace(Kind kind, int value, int weight) {
        public static final int MAX_WEIGHT = 64 * 12;
        public static final Codec<DiceFace> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Kind.CODEC.fieldOf("kind").forGetter(DiceFace::kind),
                Codec.INT.optionalFieldOf("value", 0).forGetter(DiceFace::value),
                // Dice forged before weights existed: every face counts once
                Codec.intRange(1, MAX_WEIGHT).optionalFieldOf("weight", 1).forGetter(DiceFace::weight)
        ).apply(instance, DiceFace::new));

        public DiceFace(Kind kind, int value) {
            this(kind, value, 1);
        }

        public DiceFace withWeight(int weight) {
            return new DiceFace(kind, value, Math.clamp(weight, 1, MAX_WEIGHT));
        }
        public static final Comparator<DiceFace> ORDER =
                Comparator.comparing(DiceFace::kind).thenComparingInt(DiceFace::value);
        private static final Pattern FACE_PATTERN = Pattern.compile("^(premium_|cursed_|coin_|debt_)?dice_face_(\\d+)$");
        /** The highest number of coins a coin / debt face gives or takes. */
        public static final int MAX_COINS = 10;

        /**
         * @return the face represented by this item (dice_face_N, premium_dice_face_N, cursed_dice_face_N,
         * coin_dice_face_N, debt_dice_face_N, swap_dice_face, blank_dice_face).
         */
        public static Optional<DiceFace> fromItem(Item item) {
            if (item == null || item == Items.AIR) return Optional.empty();
            Identifier id = Registries.ITEM.getId(item);
            if (!Steveparty.MOD_ID.equals(id.getNamespace())) return Optional.empty();
            String path = id.getPath();
            if (path.equals("blank_dice_face")) return Optional.of(new DiceFace(Kind.BLANK, 0));
            if (path.equals("swap_dice_face")) return Optional.of(new DiceFace(Kind.SWAP, 0));
            Matcher matcher = FACE_PATTERN.matcher(path);
            if (!matcher.matches()) return Optional.empty();
            Kind kind = Kind.NORMAL;
            for (Kind candidate : Kind.values()) {
                if (candidate.prefix.equals(matcher.group(1))) kind = candidate;
            }
            try {
                return Optional.of(new DiceFace(kind, Integer.parseInt(matcher.group(2))));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        public static boolean isFace(ItemStack stack) {
            return !stack.isEmpty() && fromItem(stack.getItem()).isPresent();
        }

        /** @return the face item this face was made from (AIR if it no longer exists). */
        public Item toItem() {
            String path = kind == Kind.BLANK ? "blank_dice_face" : kind == Kind.SWAP ? "swap_dice_face"
                    : kind.prefix + "dice_face_" + value;
            return Registries.ITEM.get(Steveparty.id(path));
        }

        /** @return the steps this face moves the token (0 for the coin, debt and swap faces). */
        public int steps() {
            return kind.numeric ? value : 0;
        }

        /** @return the coins this face gives (negative: takes), 0 for the other faces. */
        public int coins() {
            return kind == Kind.COIN ? value : kind == Kind.DEBT ? -value : 0;
        }

        /** @return true for the face 0: the token stays where it is and its tile plays its landing again. */
        public boolean isZero() {
            return kind == Kind.NORMAL && value == 0;
        }

        public Text asText() {
            return switch (kind) {
                case NORMAL -> Text.literal(Integer.toString(value)).formatted(Formatting.WHITE);
                case PREMIUM -> Text.literal(value + "★").formatted(Formatting.GOLD);
                case CURSED -> Text.literal(value + "☠").formatted(Formatting.DARK_PURPLE);
                case BLANK -> Text.literal("–").formatted(Formatting.DARK_GRAY);
                case COIN -> Text.literal("+" + value + "¢").formatted(Formatting.YELLOW);
                case DEBT -> Text.literal("−" + value + "¢").formatted(Formatting.RED);
                case SWAP -> Text.literal("⇄").formatted(Formatting.LIGHT_PURPLE);
            };
        }
    }

    public enum Kind implements StringIdentifiable {
        NORMAL("normal", "", true),
        PREMIUM("premium", "premium_", true),
        CURSED("cursed", "cursed_", true),
        BLANK("blank", "blank_", true),
        /** The roller gains {@code value} coins of the party; the token doesn't move. */
        COIN("coin", "coin_", false),
        /** The roller loses {@code value} coins of the party (never more than they hold); the token doesn't move. */
        DEBT("debt", "debt_", false),
        /** The token swaps places with another token, chosen by the roller. */
        SWAP("swap", "swap_", false);

        public static final Codec<Kind> CODEC = StringIdentifiable.createCodec(Kind::values);
        private final String name;
        /** Prefix of the face items of this kind, before "dice_face_N". */
        private final String prefix;
        /** The value of the face is a number of steps. */
        private final boolean numeric;

        Kind(String name, String prefix, boolean numeric) {
            this.name = name;
            this.prefix = prefix;
            this.numeric = numeric;
        }

        public boolean isNumeric() {
            return numeric;
        }

        @Override
        public String asString() {
            return name;
        }
    }
}
