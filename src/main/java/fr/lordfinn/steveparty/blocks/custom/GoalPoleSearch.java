package fr.lordfinn.steveparty.blocks.custom;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The search behind the goal pole base's goal field: the player types natural words ("jumps", "sauts", "blocs
 * cassés pierre") and gets readable goals (an objective of the server, a criterion, a statistic).
 * <p>
 * Everything is compared {@linkplain #normalize normalized}: no case, no accents, no {@code minecraft} namespace,
 * dots, colons and underscores as spaces. Every word typed must be found in the entry's label, its value (the
 * technical id) or its keywords. Entries are ranked by group (objectives first, then the common goals, the
 * criteria and the other statistics), then by how well the label matches.
 */
public final class GoalPoleSearch {
    /** Objectives of the server. */
    public static final int GROUP_OBJECTIVE = 0;
    /** The goals offered when nothing is typed (the presets). */
    public static final int GROUP_COMMON = 1;
    /** Simple criteria and custom statistics. */
    public static final int GROUP_CRITERION = 2;
    /** Statistics of an item, a block or an entity. */
    public static final int GROUP_STAT = 3;

    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern NAMESPACE = Pattern.compile("minecraft[.:]");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    private GoalPoleSearch() {}

    /** A goal that can be picked: the value saved, its readable label, extra words to find it with, and data (e.g. its icon). */
    public static final class Entry<T> {
        private final String value, label;
        private final int group;
        private final T data;
        private final String normalizedLabel, haystack;

        public Entry(String value, String label, String keywords, int group, T data) {
            this.value = value;
            this.label = label;
            this.group = group;
            this.data = data;
            this.normalizedLabel = normalize(label);
            this.haystack = normalizedLabel + " " + normalize(value) + " " + normalize(keywords == null ? "" : keywords);
        }

        public String value() { return value; }
        public String label() { return label; }
        public int group() { return group; }
        public T data() { return data; }
        public String normalizedLabel() { return normalizedLabel; }
    }

    /** Lower case, no accents, no {@code minecraft.}/{@code minecraft:} namespace, words separated by single spaces. */
    public static String normalize(String text) {
        if (text == null) return "";
        String lower = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        lower = ACCENTS.matcher(lower).replaceAll("");
        lower = NAMESPACE.matcher(lower).replaceAll(" ");
        return SEPARATORS.matcher(lower).replaceAll(" ").strip();
    }

    /** The words of a query, a final plural "s" then a final "e" dropped (so "cassés" also finds "casser"). */
    static List<String> words(String query) {
        List<String> words = new ArrayList<>();
        for (String word : normalize(query).split(" ")) {
            if (word.isEmpty()) continue;
            if (word.length() > 3 && word.endsWith("s")) word = word.substring(0, word.length() - 1);
            if (word.length() > 3 && word.endsWith("e")) word = word.substring(0, word.length() - 1);
            words.add(word);
        }
        return words;
    }

    /**
     * How well an entry matches a query: -1 if one of its words is nowhere, else from 0 (the label is the query) to
     * 5 (only found in the id or the keywords).
     */
    public static int score(Entry<?> entry, String query) {
        return score(entry, words(query), normalize(query));
    }

    private static int score(Entry<?> entry, List<String> words, String typed) {
        for (String word : words) if (!entry.haystack.contains(word)) return -1;
        String label = entry.normalizedLabel;
        if (label.equals(typed)) return 0;
        if (label.startsWith(typed)) return 1;
        if ((" " + label).contains(" " + typed)) return 2;
        if (label.contains(typed)) return 3;
        for (String word : words) if (!label.contains(word)) return 5;
        return 4;
    }

    /**
     * The entries matching a query, best first: by group, then score, then shorter label. An empty query gives the
     * objectives and the common goals, in their order.
     */
    public static <T> List<Entry<T>> search(String query, List<Entry<T>> entries) {
        List<Entry<T>> found = new ArrayList<>();
        List<String> words = words(query);
        if (words.isEmpty()) {
            for (Entry<T> entry : entries) if (entry.group <= GROUP_COMMON) found.add(entry);
            return found;
        }
        String typed = normalize(query);
        List<int[]> scores = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            int score = score(entries.get(i), words, typed);
            if (score >= 0) scores.add(new int[]{i, score});
        }
        scores.sort(Comparator.<int[]>comparingInt(s -> entries.get(s[0]).group)
                .thenComparingInt(s -> s[1])
                .thenComparingInt(s -> entries.get(s[0]).label.length())
                .thenComparing(s -> entries.get(s[0]).normalizedLabel));
        for (int[] s : scores) found.add(entries.get(s[0]));
        return found;
    }

    /**
     * The entry a text stands for: the one with that label (case and accents ignored), else the one with that value;
     * objectives first. Null if none.
     */
    public static <T> Entry<T> resolve(String text, List<Entry<T>> entries) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) return null;
        for (Entry<T> entry : entries) if (entry.normalizedLabel.equals(normalized)) return entry;
        for (Entry<T> entry : entries) if (entry.value.equals(text)) return entry;
        return null;
    }

    /** The entry saved with that value (objectives first), or null. */
    public static <T> Entry<T> byValue(String value, List<Entry<T>> entries) {
        for (Entry<T> entry : entries) if (entry.value.equals(value)) return entry;
        return null;
    }
}
