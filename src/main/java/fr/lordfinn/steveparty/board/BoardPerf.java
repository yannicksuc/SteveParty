package fr.lordfinn.steveparty.board;

/**
 * Debug counters of the board's server side work (cartridges, routers, links), read by the benchmark GameTest
 * (BoardPerfGameTests) to compare the cost of the same board operations before and after a change. Plain counters,
 * server thread only: a few increments, no other cost.
 */
public final class BoardPerf {
    /** Router power reads ({@code getReceivedRedstonePower} of a router). */
    public static long routerPowerReads;
    /** Board spaces a router looked up to push its power to. */
    public static long routerPushLookups;
    /** Board space power reads (own redstone). */
    public static long boardSpacePowerReads;
    /** Lookups of the router of a board space in the persistent state. */
    public static long routerStateLookups;
    /** Board space data sent to the clients (block entity updates requested). */
    public static long boardSpaceSyncs;

    private BoardPerf() {
    }

    public static void reset() {
        routerPowerReads = routerPushLookups = boardSpacePowerReads = routerStateLookups = boardSpaceSyncs = 0;
    }

    public static String summary() {
        return "routerPowerReads=" + routerPowerReads + " routerPushLookups=" + routerPushLookups
                + " boardSpacePowerReads=" + boardSpacePowerReads + " routerStateLookups=" + routerStateLookups
                + " boardSpaceSyncs=" + boardSpaceSyncs;
    }
}
