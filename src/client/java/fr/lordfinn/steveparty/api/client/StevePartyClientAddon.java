package fr.lordfinn.steveparty.api.client;

/**
 * The client entrypoint of a Steve Party addon. Declared in the addon's {@code fabric.mod.json}:
 * <pre>{@code
 * "entrypoints": {
 *   "steveparty:client": [ "com.example.myaddon.client.MyStevePartyClientAddon" ]
 * }
 * }</pre>
 * It is called once, at the end of Steve Party's client initialization (after the common
 * {@link fr.lordfinn.steveparty.api.StevePartyAddon} entrypoints): the place for the addon's renderers, screens and
 * client-side listeners.
 */
@FunctionalInterface
public interface StevePartyClientAddon {
    void onStevePartyInitializeClient();
}
