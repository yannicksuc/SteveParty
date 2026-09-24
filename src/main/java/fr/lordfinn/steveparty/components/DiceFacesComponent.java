package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
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
 * Faces of a die forged in the Dice Forge. A die carrying this component rolls one of these faces
 * (uniformly) instead of the default 1..10 range.
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
        DiceFacesComponent component = stack == null ? null : stack.get(TYPE);
        if (component == null || component.faces().isEmpty()) {
            return random.nextBetween(DiceEntity.MIN, DiceEntity.MAX);
        }
        return component.faces().get(random.nextInt(component.faces().size())).value();
    }

    /** Same as {@link #rollFace(ItemStack, Random)} but also tells which face was rolled (null for a plain die). */
    public static @Nullable DiceFace rollDiceFace(@Nullable ItemStack stack, Random random) {
        DiceFacesComponent component = stack == null ? null : stack.get(TYPE);
        if (component == null || component.faces().isEmpty()) return null;
        return component.faces().get(random.nextInt(component.faces().size()));
    }

    /**
     * Builds the die produced by the forge for these face items (empty stacks are ignored).
     * Faces are sorted so the same set of faces always gives stackable dice.
     *
     * @return the die, or {@link ItemStack#EMPTY} if no valid face was given
     */
    public static ItemStack createDie(List<ItemStack> faceStacks) {
        List<DiceFace> faces = new ArrayList<>();
        for (ItemStack stack : faceStacks) {
            if (stack == null || stack.isEmpty()) continue;
            DiceFace.fromItem(stack.getItem()).ifPresent(faces::add);
        }
        if (faces.isEmpty()) return ItemStack.EMPTY;
        if (faces.size() > MAX_FACES) faces = faces.subList(0, MAX_FACES);
        faces.sort(DiceFace.ORDER);

        DiceFacesComponent component = new DiceFacesComponent(faces);
        ItemStack die = new ItemStack(ModItems.DEFAULT_DICE);
        die.set(TYPE, component);
        die.set(DataComponentTypes.ITEM_NAME,
                Text.translatableWithFallback("item.steveparty.forged_dice", "Forged Dice"));
        die.set(DataComponentTypes.LORE, new LoreComponent(List.of(component.describe())));
        return die;
    }

    /** One tooltip line listing the faces, e.g. "Faces: 1, 3, 5★, 2☠, –". */
    public Text describe() {
        MutableText list = Text.empty();
        for (int i = 0; i < faces.size(); i++) {
            if (i > 0) list.append(Text.literal(", ").formatted(Formatting.GRAY));
            list.append(faces.get(i).asText());
        }
        return Text.translatableWithFallback("tooltip.steveparty.dice_faces", "Faces: %s", list)
                .styled(style -> style.withItalic(false).withColor(Formatting.GRAY));
    }

    // ------------------------------------------------------------------ face

    public record DiceFace(Kind kind, int value) {
        public static final Codec<DiceFace> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Kind.CODEC.fieldOf("kind").forGetter(DiceFace::kind),
                Codec.INT.optionalFieldOf("value", 0).forGetter(DiceFace::value)
        ).apply(instance, DiceFace::new));
        public static final Comparator<DiceFace> ORDER =
                Comparator.comparing(DiceFace::kind).thenComparingInt(DiceFace::value);
        private static final Pattern FACE_PATTERN = Pattern.compile("^(premium_|cursed_)?dice_face_(\\d+)$");

        /** @return the face represented by this item (dice_face_N, premium_dice_face_N, cursed_dice_face_N, blank_dice_face). */
        public static Optional<DiceFace> fromItem(Item item) {
            if (item == null || item == Items.AIR) return Optional.empty();
            Identifier id = Registries.ITEM.getId(item);
            if (!Steveparty.MOD_ID.equals(id.getNamespace())) return Optional.empty();
            String path = id.getPath();
            if (path.equals("blank_dice_face")) return Optional.of(new DiceFace(Kind.BLANK, 0));
            Matcher matcher = FACE_PATTERN.matcher(path);
            if (!matcher.matches()) return Optional.empty();
            Kind kind = matcher.group(1) == null ? Kind.NORMAL
                    : matcher.group(1).equals("premium_") ? Kind.PREMIUM : Kind.CURSED;
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
            String path = kind == Kind.BLANK ? "blank_dice_face" : kind.prefix + "dice_face_" + value;
            return Registries.ITEM.get(Steveparty.id(path));
        }

        public Text asText() {
            return switch (kind) {
                case NORMAL -> Text.literal(Integer.toString(value)).formatted(Formatting.WHITE);
                case PREMIUM -> Text.literal(value + "★").formatted(Formatting.GOLD);
                case CURSED -> Text.literal(value + "☠").formatted(Formatting.DARK_PURPLE);
                case BLANK -> Text.literal("–").formatted(Formatting.DARK_GRAY);
            };
        }
    }

    public enum Kind implements StringIdentifiable {
        NORMAL("normal", ""),
        PREMIUM("premium", "premium_"),
        CURSED("cursed", "cursed_"),
        BLANK("blank", "blank_");

        public static final Codec<Kind> CODEC = StringIdentifiable.createCodec(Kind::values);
        private final String name;
        private final String prefix;

        Kind(String name, String prefix) {
            this.name = name;
            this.prefix = prefix;
        }

        @Override
        public String asString() {
            return name;
        }
    }
}
