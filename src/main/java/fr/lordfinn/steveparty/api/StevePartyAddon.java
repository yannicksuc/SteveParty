package fr.lordfinn.steveparty.api;

/**
 * The entrypoint of a Steve Party addon, on both sides (server and client). Declared in the addon's
 * {@code fabric.mod.json}:
 * <pre>{@code
 * "entrypoints": {
 *   "steveparty": [ "com.example.myaddon.MyStevePartyAddon" ]
 * }
 * }</pre>
 * It is called once, at the end of Steve Party's own initialization: Steve Party's content is registered by then
 * (its board space roles, party steps, party cards, dice modules), and the addon registers its own in the
 * registries of {@link StevePartyRegistries} and listens to the events of {@code fr.lordfinn.steveparty.api.event}.
 * The client side has its own entrypoint, {@code steveparty:client}
 * ({@code fr.lordfinn.steveparty.api.client.StevePartyClientAddon}).
 * <p>
 * Items, blocks and entities of the addon are registered as any Fabric mod does, in the vanilla registries, from its
 * own {@code main} initializer or from here.
 */
@FunctionalInterface
public interface StevePartyAddon {
    /** Registers the addon's content. Steve Party's own content is already registered. */
    void onStevePartyInitialize();
}
