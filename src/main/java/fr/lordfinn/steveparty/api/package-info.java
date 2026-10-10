/**
 * Steve Party's API for addons: what another mod uses to add content to Steve Party and to follow its parties.
 * <ul>
 *     <li>Entrypoints: {@link fr.lordfinn.steveparty.api.StevePartyAddon} ({@code steveparty}, both sides) and
 *     {@code fr.lordfinn.steveparty.api.client.StevePartyClientAddon} ({@code steveparty:client}), called at the end
 *     of Steve Party's initialization ({@link fr.lordfinn.steveparty.api.StevePartyApi}).</li>
 *     <li>Registries ({@link fr.lordfinn.steveparty.api.StevePartyRegistries}, keyed by Identifier): board space
 *     roles ({@link fr.lordfinn.steveparty.api.board.BoardSpaceRoles}), party step kinds
 *     ({@link fr.lordfinn.steveparty.api.party.PartySteps}), party cards
 *     ({@link fr.lordfinn.steveparty.api.party.PartyCards}), dice modules
 *     ({@link fr.lordfinn.steveparty.dice.DiceModules}) and power-ups
 *     ({@link fr.lordfinn.steveparty.powerups.PowerUps}). Steve Party registers its own content in them, the way an
 *     addon does.</li>
 *     <li>Events ({@code fr.lordfinn.steveparty.api.event}): the life of a party
 *     ({@link fr.lordfinn.steveparty.api.event.PartyEvents}), its mini-games
 *     ({@link fr.lordfinn.steveparty.api.event.MiniGameEvents}), the board and its dice
 *     ({@link fr.lordfinn.steveparty.api.event.BoardEvents}).</li>
 * </ul>
 * The classes an addon extends (board space behaviours, cartridges, party steps, dice modules, power-ups) are
 * Steve Party's own: the API's javadoc says which of their methods to override.
 */
package fr.lordfinn.steveparty.api;
