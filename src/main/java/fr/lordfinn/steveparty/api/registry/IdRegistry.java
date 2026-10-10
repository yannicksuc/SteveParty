package fr.lordfinn.steveparty.api.registry;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A registry of Steve Party content keyed by {@link Identifier}: the board space roles, the party steps, the party
 * cards, the dice modules... Steve Party registers its own content in it the way an addon does, from its
 * initializer; an addon registers its own from its {@link fr.lordfinn.steveparty.api.StevePartyAddon} entrypoint
 * (or its main initializer), under its own namespace.
 * <p>
 * Entries keep their registration order (Steve Party's first). An id is registered once: a second registration under
 * the same id is a mistake and throws. Server and client both register the same entries (the registries are filled
 * by common code), so ids may be sent over the network.
 *
 * @param <T> what is registered
 */
public class IdRegistry<T> {
    private final String name;
    private final Map<Identifier, T> entries = new LinkedHashMap<>();
    private final Map<T, Identifier> ids = new LinkedHashMap<>();

    /** @param name what it holds, for the error messages ("board space role"...) */
    public IdRegistry(String name) {
        this.name = name;
    }

    /**
     * Registers {@code value} under {@code id}.
     *
     * @return {@code value}, to keep it in a constant
     * @throws IllegalStateException if the id (or the very same value) is already registered
     */
    public <V extends T> V register(Identifier id, V value) {
        if (entries.containsKey(id))
            throw new IllegalStateException("Steve Party " + name + " registered twice: " + id);
        if (ids.containsKey(value))
            throw new IllegalStateException("Steve Party " + name + " " + id + " is already registered as " + ids.get(value));
        entries.put(id, value);
        ids.put(value, id);
        onRegistered(id, value);
        return value;
    }

    /** Called after each registration (a registry deriving content from its entries hooks here). */
    protected void onRegistered(Identifier id, T value) {
    }

    public @Nullable T get(@Nullable Identifier id) {
        return id == null ? null : entries.get(id);
    }

    public Optional<T> getOptional(@Nullable Identifier id) {
        return Optional.ofNullable(get(id));
    }

    /** @return the id {@code value} is registered under, null if it is not registered */
    public @Nullable Identifier getId(@Nullable T value) {
        return value == null ? null : ids.get(value);
    }

    public boolean contains(@Nullable Identifier id) {
        return id != null && entries.containsKey(id);
    }

    /** Every entry, in registration order (read-only view). */
    public Collection<T> values() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** Every id, in registration order (read-only view). */
    public Set<Identifier> ids() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public int size() {
        return entries.size();
    }

    @Override
    public String toString() {
        return "IdRegistry[" + name + ", " + entries.size() + " entries]";
    }
}
