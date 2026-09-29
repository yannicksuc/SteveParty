package fr.lordfinn.steveparty.board;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks a board before playing it: missing start, dead ends, board spaces no start leads to, links to blocks that are
 * not board spaces, inventory tiles without chest, routers driving empty positions, teleport tiles sending nowhere, start
 * tiles without token. Run by a
 * Wrench click on the Party Controller, {@code /steveparty board check}, and at the start of a party (as a warning:
 * the party still starts).
 */
public final class BoardValidator {
    /** Same reach as the Party Controller's search for start tiles. */
    public static final int RADIUS = 100;
    /** Positions listed per problem in the chat (the others are counted). */
    private static final int LISTED = 5;

    private BoardValidator() {
    }

    /** The problems found, each with the positions concerned. */
    public record Report(int boardSpaces, int starts, List<Issue> issues) {
        public boolean ok() {
            return issues.stream().noneMatch(i -> i.severity() == Severity.ERROR || i.severity() == Severity.WARNING);
        }

        public long count(Severity severity) {
            return issues.stream().filter(i -> i.severity() == severity).count();
        }
    }

    public enum Severity { ERROR, WARNING, INFO }

    public record Issue(Severity severity, String key, List<BlockPos> positions) {
    }

    public static Report check(ServerWorld world, BlockPos center) {
        BoardGraph graph = BoardGraph.collect(world, center, RADIUS);
        Report report = check(graph);
        // Shop spaces without a merchant around (or whose chosen merchant is away): nothing happens there
        List<BlockPos> noShop = new ArrayList<>();
        for (BoardGraph.Node node : graph.nodes()) {
            if (!(world.getBlockEntity(node.pos()) instanceof fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity space)) continue;
            net.minecraft.item.ItemStack cartridge = fr.lordfinn.steveparty.service.ShopStops.shopCartridge(space);
            if (cartridge != null && fr.lordfinn.steveparty.service.ShopStops.findShop(world, node.pos(), cartridge) == null) noShop.add(node.pos());
        }
        if (noShop.isEmpty()) return report;
        List<Issue> issues = new ArrayList<>(report.issues());
        add(issues, Severity.WARNING, "no_shop", noShop);
        return new Report(report.boardSpaces(), report.starts(), issues);
    }

    public static Report check(BoardGraph graph) {
        List<Issue> issues = new ArrayList<>();
        List<BlockPos> starts = new ArrayList<>(), deadEnds = new ArrayList<>(), unreachable = new ArrayList<>(),
                broken = new ArrayList<>(), noChest = new ArrayList<>(), chestGone = new ArrayList<>(), noToken = new ArrayList<>();
        for (BoardGraph.Node node : graph.nodes()) {
            if (node.start()) {
                starts.add(node.pos());
                if (!node.hasStartToken()) noToken.add(node.pos());
            }
            if (graph.isDeadEnd(node)) deadEnds.add(node.pos());
            if (graph.isUnreachable(node)) unreachable.add(node.pos());
            if (node.edges().stream().anyMatch(e -> e.target() == BoardGraph.Target.BROKEN)) broken.add(node.pos());
            if (node.inventoryIssue() == BoardGraph.InventoryIssue.NO_CHEST) noChest.add(node.pos());
            if (node.inventoryIssue() == BoardGraph.InventoryIssue.CHEST_GONE) chestGone.add(node.pos());
        }
        List<BlockPos> noTeleportTarget = graph.nodes().stream().filter(BoardGraph.Node::teleportsNowhere).map(BoardGraph.Node::pos).toList();
        List<BlockPos> badRouters = graph.routers().stream().filter(r -> !r.brokenTargets().isEmpty()).map(BoardGraph.Router::pos).toList();

        int count = graph.nodes().size();
        if (count > 0 && starts.isEmpty()) issues.add(new Issue(Severity.ERROR, "no_start", List.of()));
        add(issues, Severity.WARNING, "dead_ends", deadEnds);
        add(issues, Severity.WARNING, "unreachable", unreachable);
        add(issues, Severity.WARNING, "broken_links", broken);
        add(issues, Severity.WARNING, "no_chest", noChest);
        add(issues, Severity.WARNING, "chest_gone", chestGone);
        add(issues, Severity.WARNING, "router_targets", badRouters);
        add(issues, Severity.WARNING, "teleport_no_target", noTeleportTarget);
        add(issues, Severity.INFO, "no_token", noToken);
        return new Report(count, starts.size(), issues);
    }

    private static void add(List<Issue> issues, Severity severity, String key, List<BlockPos> positions) {
        if (!positions.isEmpty()) issues.add(new Issue(severity, key, positions));
    }

    // ---------------------------------------------------------------- messages

    /** The full report, in the chat of {@code player}. */
    public static void send(ServerPlayerEntity player, Report report) {
        for (Text line : lines(report)) player.sendMessage(line, false);
    }

    public static List<Text> lines(Report report) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("message.steveparty.board.check.title", report.boardSpaces(), report.starts())
                .formatted(Formatting.GOLD, Formatting.BOLD));
        if (report.boardSpaces() == 0) {
            lines.add(Text.translatable("message.steveparty.board.check.empty", RADIUS).formatted(Formatting.GRAY));
            return lines;
        }
        if (report.ok()) lines.add(Text.translatable("message.steveparty.board.check.ok").formatted(Formatting.GREEN));
        for (Issue issue : report.issues()) {
            Formatting color = switch (issue.severity()) {
                case ERROR -> Formatting.RED;
                case WARNING -> Formatting.YELLOW;
                case INFO -> Formatting.GRAY;
            };
            String mark = switch (issue.severity()) {
                case ERROR -> "✖ ";
                case WARNING -> "⚠ ";
                case INFO -> "ℹ ";
            };
            MutableText line = Text.literal(mark).formatted(color)
                    .append(Text.translatable("message.steveparty.board.check." + issue.key(), issue.positions().size(), RADIUS).formatted(color));
            List<BlockPos> positions = issue.positions();
            for (int i = 0; i < Math.min(LISTED, positions.size()); i++) line.append(" ").append(clickable(positions.get(i)));
            if (positions.size() > LISTED) {
                line.append(" ").append(Text.translatable("message.steveparty.board.check.more", positions.size() - LISTED).formatted(Formatting.GRAY));
            }
            lines.add(line);
        }
        return lines;
    }

    /** At the start of a party: one line for the players around the controller if the board has problems. */
    public static void warnAtStart(ServerWorld world, BlockPos controller) {
        Report report = check(world, controller);
        if (report.ok()) return;
        fr.lordfinn.steveparty.utils.MessageUtils.sendToNearby(world, controller.toCenterPos(), RADIUS, summary(report),
                fr.lordfinn.steveparty.utils.MessageUtils.MessageType.CHAT);
    }

    /** One line for everyone around when a party starts, only if something is wrong. */
    public static Text summary(Report report) {
        List<Text> parts = new ArrayList<>();
        for (Issue issue : report.issues()) {
            if (issue.severity() == Severity.INFO) continue;
            parts.add(Text.translatable("message.steveparty.board.summary." + issue.key(), issue.positions().size()));
        }
        MutableText text = Text.translatable("message.steveparty.board.summary").formatted(Formatting.YELLOW);
        for (int i = 0; i < parts.size(); i++) text.append(i == 0 ? " " : ", ").append(parts.get(i));
        return text.append(" ").append(Text.translatable("message.steveparty.board.summary.hint").formatted(Formatting.GRAY));
    }

    /** A position that teleports above it when clicked (operators), with a hint on hover. */
    private static Text clickable(BlockPos pos) {
        String command = "/tp @s " + pos.getX() + " " + (pos.getY() + 1) + " " + pos.getZ();
        return BoardText.pos(pos).styled(style -> style.withColor(Formatting.AQUA).withUnderline(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("message.steveparty.board.check.teleport"))));
    }
}
