package dev.oakheart.oaklog.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.oakheart.command.CommandRegistrar;
import dev.oakheart.message.MessageManager;
import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.model.SetDefinition;
import dev.oakheart.oaklog.ui.HubGui;
import dev.oakheart.oaklog.ui.IconRenderer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class OakLogCommand {

    private final OakLog plugin;
    private final IconRenderer iconRenderer;

    public OakLogCommand(OakLog plugin, IconRenderer iconRenderer) {
        this.plugin = plugin;
        this.iconRenderer = iconRenderer;
    }

    public void register() {
        LiteralCommandNode<CommandSourceStack> root = Commands.literal("oaklog")
                .requires(s -> s.getSender().hasPermission("oaklog.use"))
                .executes(this::executeOpenSelf)
                .then(Commands.argument("player", ArgumentTypes.player())
                        .requires(s -> s.getSender().hasPermission("oaklog.view-others"))
                        .executes(this::executeOpenOther))
                .then(buildToastCommand())
                .then(buildTestToastCommand())
                .then(buildGrantCommand())
                .then(buildRevokeCommand())
                .then(buildReloadCommand())
                .then(buildInfoCommand())
                .then(buildDebugCommand())
                .build();

        String alias = plugin.getConfigManager().getCommandAlias();
        List<String> aliases = alias == null || alias.isBlank() ? List.of() : List.of(alias);
        CommandRegistrar.register(plugin, root, "Open your collection log", aliases);
    }

    // --- Open ---

    private int executeOpenSelf(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            messages().sendCommand(sender, "player-only");
            return Command.SINGLE_SUCCESS;
        }
        new HubGui(plugin, player, player.getUniqueId(), iconRenderer).open();
        return Command.SINGLE_SUCCESS;
    }

    private int executeOpenOther(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player viewer)) {
            messages().sendCommand(sender, "player-only");
            return Command.SINGLE_SUCCESS;
        }
        Player target = resolveSinglePlayer(ctx, "player");
        if (target == null) {
            messages().sendCommand(sender, "player-not-found");
            return Command.SINGLE_SUCCESS;
        }
        new HubGui(plugin, viewer, target.getUniqueId(), iconRenderer).open();
        return Command.SINGLE_SUCCESS;
    }

    // --- /oaklog toast on|off|milestones ---

    private LiteralCommandNode<CommandSourceStack> buildToastCommand() {
        return Commands.literal("toast")
                .requires(s -> s.getSender().hasPermission("oaklog.toast"))
                .then(Commands.literal("on").executes(c -> setToast(c, PlayerProgress.ToastState.ON)))
                .then(Commands.literal("off").executes(c -> setToast(c, PlayerProgress.ToastState.OFF)))
                .then(Commands.literal("milestones").executes(c -> setToast(c, PlayerProgress.ToastState.MILESTONES)))
                .build();
    }

    private int setToast(CommandContext<CommandSourceStack> ctx, PlayerProgress.ToastState state) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            messages().sendCommand(sender, "player-only");
            return Command.SINGLE_SUCCESS;
        }
        plugin.getProgressManager().setToastState(player.getUniqueId(), state);
        messages().sendCommand(sender, "toast-set-" + state.id());
        return Command.SINGLE_SUCCESS;
    }

    // --- /oaklog test-toast (debug helper) ---

    private LiteralCommandNode<CommandSourceStack> buildTestToastCommand() {
        return Commands.literal("test-toast")
                .requires(s -> s.getSender().hasPermission("oaklog.admin.info"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        messages().sendCommand(sender, "player-only");
                        return Command.SINGLE_SUCCESS;
                    }
                    if (!plugin.getToastService().isAvailable()) {
                        sender.sendMessage(net.kyori.adventure.text.Component.text(
                                "PacketEvents not detected — toasts unavailable."));
                        return Command.SINGLE_SUCCESS;
                    }
                    plugin.getToastService().sendTestToast(player);
                    sender.sendMessage(net.kyori.adventure.text.Component.text(
                            "Test toast dispatched. Check the top-right of your screen."));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    // --- /oaklog grant <player> <entry|category|set|all> ---

    private LiteralCommandNode<CommandSourceStack> buildGrantCommand() {
        return Commands.literal("grant")
                .requires(s -> s.getSender().hasPermission("oaklog.admin.grant"))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.literal("entry")
                                .then(Commands.argument("key", StringArgumentType.greedyString())
                                        .suggests(entrySuggestions())
                                        .executes(this::grantEntry)))
                        .then(Commands.literal("category")
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests(categorySuggestions())
                                        .executes(this::grantCategory)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("set", StringArgumentType.word())
                                        .suggests(setSuggestions())
                                        .executes(this::grantSet)))
                        .then(Commands.literal("all")
                                .executes(this::grantAll)))
                .build();
    }

    private int grantEntry(CommandContext<CommandSourceStack> ctx) {
        Player target = resolveSinglePlayer(ctx, "player");
        if (target == null) {
            messages().sendCommand(ctx.getSource().getSender(), "player-not-found");
            return Command.SINGLE_SUCCESS;
        }
        String key = StringArgumentType.getString(ctx, "key").toLowerCase(Locale.ROOT);
        Integer entryId = resolveEntryId(key);
        if (entryId == null) {
            messages().sendCommand(ctx.getSource().getSender(), "unknown-entry",
                    Placeholder.unparsed("entry", key));
            return Command.SINGLE_SUCCESS;
        }
        plugin.getProgressManager().grant(target.getUniqueId(), entryId);
        plugin.getLeaderboardManager().invalidate();
        audit(ctx, "GRANT", target.getName(), "entry=" + key);
        messages().sendCommand(ctx.getSource().getSender(), "grant-entry-success",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("entry", key));
        return Command.SINGLE_SUCCESS;
    }

    private int grantCategory(CommandContext<CommandSourceStack> ctx) {
        Player target = resolveSinglePlayer(ctx, "player");
        if (target == null) {
            messages().sendCommand(ctx.getSource().getSender(), "player-not-found");
            return Command.SINGLE_SUCCESS;
        }
        String catName = StringArgumentType.getString(ctx, "category");
        Category category = Category.fromId(catName);
        if (category == null) {
            messages().sendCommand(ctx.getSource().getSender(), "unknown-category",
                    Placeholder.unparsed("category", catName));
            return Command.SINGLE_SUCCESS;
        }
        int count = bulkGrantEntries(target.getUniqueId(),
                plugin.getEntryRegistry().byCategory(category).stream()
                        .filter(e -> !e.isRetired())
                        .toList());
        audit(ctx, "GRANT", target.getName(),
                "category=" + category.id() + " count=" + count);
        messages().sendCommand(ctx.getSource().getSender(), "grant-bulk-success",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("scope", "category " + category.id()),
                Placeholder.unparsed("count", String.valueOf(count)));
        return Command.SINGLE_SUCCESS;
    }

    private int grantSet(CommandContext<CommandSourceStack> ctx) {
        Player target = resolveSinglePlayer(ctx, "player");
        if (target == null) {
            messages().sendCommand(ctx.getSource().getSender(), "player-not-found");
            return Command.SINGLE_SUCCESS;
        }
        String setId = StringArgumentType.getString(ctx, "set").toLowerCase(Locale.ROOT);
        if (plugin.getSetsConfig().sets().stream().noneMatch(s -> s.id().equals(setId))) {
            messages().sendCommand(ctx.getSource().getSender(), "unknown-set",
                    Placeholder.unparsed("set", setId));
            return Command.SINGLE_SUCCESS;
        }
        int count = bulkGrantEntries(target.getUniqueId(),
                plugin.getEntryRegistry().entriesInSetResolved(setId));
        audit(ctx, "GRANT", target.getName(),
                "set=" + setId + " count=" + count);
        messages().sendCommand(ctx.getSource().getSender(), "grant-bulk-success",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("scope", "set " + setId),
                Placeholder.unparsed("count", String.valueOf(count)));
        return Command.SINGLE_SUCCESS;
    }

    private int grantAll(CommandContext<CommandSourceStack> ctx) {
        Player target = resolveSinglePlayer(ctx, "player");
        if (target == null) {
            messages().sendCommand(ctx.getSource().getSender(), "player-not-found");
            return Command.SINGLE_SUCCESS;
        }
        int count = bulkGrantEntries(target.getUniqueId(),
                plugin.getEntryRegistry().all().stream()
                        .filter(e -> !e.isRetired())
                        .toList());
        audit(ctx, "GRANT", target.getName(), "all count=" + count);
        messages().sendCommand(ctx.getSource().getSender(), "grant-bulk-success",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("scope", "all entries"),
                Placeholder.unparsed("count", String.valueOf(count)));
        return Command.SINGLE_SUCCESS;
    }

    private int bulkGrantEntries(UUID uuid, List<Entry> entries) {
        int count = 0;
        for (Entry e : entries) {
            plugin.getProgressManager().grant(uuid, e.id());
            count++;
        }
        plugin.getLeaderboardManager().invalidate();
        return count;
    }

    // --- /oaklog revoke <player> <entry|category|set|all confirm> ---

    private LiteralCommandNode<CommandSourceStack> buildRevokeCommand() {
        return Commands.literal("revoke")
                .requires(s -> s.getSender().hasPermission("oaklog.admin.revoke"))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.literal("entry")
                                .then(Commands.argument("key", StringArgumentType.greedyString())
                                        .suggests(entrySuggestions())
                                        .executes(this::revokeEntry)))
                        .then(Commands.literal("category")
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests(categorySuggestions())
                                        .executes(this::revokeCategory)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("set", StringArgumentType.word())
                                        .suggests(setSuggestions())
                                        .executes(this::revokeSet)))
                        .then(Commands.literal("all")
                                .executes(ctx -> {
                                    messages().sendCommand(ctx.getSource().getSender(),
                                            "revoke-confirm-required",
                                            Placeholder.unparsed("player",
                                                    resolvePlayerName(ctx, "player")));
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.literal("confirm")
                                        .executes(this::revokeAll))))
                .build();
    }

    private int revokeEntry(CommandContext<CommandSourceStack> ctx) {
        Player target = resolveSinglePlayer(ctx, "player");
        if (target == null) {
            messages().sendCommand(ctx.getSource().getSender(), "player-not-found");
            return Command.SINGLE_SUCCESS;
        }
        String key = StringArgumentType.getString(ctx, "key").toLowerCase(Locale.ROOT);
        Integer entryId = resolveEntryId(key);
        if (entryId == null) {
            messages().sendCommand(ctx.getSource().getSender(), "unknown-entry",
                    Placeholder.unparsed("entry", key));
            return Command.SINGLE_SUCCESS;
        }
        plugin.getProgressManager().revoke(target.getUniqueId(), entryId);
        plugin.getLeaderboardManager().invalidate();
        audit(ctx, "REVOKE", target.getName(), "entry=" + key);
        messages().sendCommand(ctx.getSource().getSender(), "revoke-entry-success",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("entry", key));
        return Command.SINGLE_SUCCESS;
    }

    private int revokeCategory(CommandContext<CommandSourceStack> ctx) {
        Player target = resolveSinglePlayer(ctx, "player");
        if (target == null) {
            messages().sendCommand(ctx.getSource().getSender(), "player-not-found");
            return Command.SINGLE_SUCCESS;
        }
        String catName = StringArgumentType.getString(ctx, "category");
        Category category = Category.fromId(catName);
        if (category == null) {
            messages().sendCommand(ctx.getSource().getSender(), "unknown-category",
                    Placeholder.unparsed("category", catName));
            return Command.SINGLE_SUCCESS;
        }
        int count = bulkRevokeEntries(target.getUniqueId(),
                plugin.getEntryRegistry().byCategory(category));
        audit(ctx, "REVOKE", target.getName(),
                "category=" + category.id() + " count=" + count);
        messages().sendCommand(ctx.getSource().getSender(), "revoke-bulk-success",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("scope", "category " + category.id()),
                Placeholder.unparsed("count", String.valueOf(count)));
        return Command.SINGLE_SUCCESS;
    }

    private int revokeSet(CommandContext<CommandSourceStack> ctx) {
        Player target = resolveSinglePlayer(ctx, "player");
        if (target == null) {
            messages().sendCommand(ctx.getSource().getSender(), "player-not-found");
            return Command.SINGLE_SUCCESS;
        }
        String setId = StringArgumentType.getString(ctx, "set").toLowerCase(Locale.ROOT);
        if (plugin.getSetsConfig().sets().stream().noneMatch(s -> s.id().equals(setId))) {
            messages().sendCommand(ctx.getSource().getSender(), "unknown-set",
                    Placeholder.unparsed("set", setId));
            return Command.SINGLE_SUCCESS;
        }
        int count = bulkRevokeEntries(target.getUniqueId(),
                plugin.getEntryRegistry().entriesInSetResolved(setId));
        audit(ctx, "REVOKE", target.getName(),
                "set=" + setId + " count=" + count);
        messages().sendCommand(ctx.getSource().getSender(), "revoke-bulk-success",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("scope", "set " + setId),
                Placeholder.unparsed("count", String.valueOf(count)));
        return Command.SINGLE_SUCCESS;
    }

    private int revokeAll(CommandContext<CommandSourceStack> ctx) {
        Player target = resolveSinglePlayer(ctx, "player");
        if (target == null) {
            messages().sendCommand(ctx.getSource().getSender(), "player-not-found");
            return Command.SINGLE_SUCCESS;
        }
        int count = plugin.getProgressManager().wipe(target.getUniqueId());
        plugin.getLeaderboardManager().invalidate();
        audit(ctx, "WIPE", target.getName(), "total=" + count);
        messages().sendCommand(ctx.getSource().getSender(), "revoke-all-success",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("count", String.valueOf(count)));
        return Command.SINGLE_SUCCESS;
    }

    private int bulkRevokeEntries(UUID uuid, List<Entry> entries) {
        int count = 0;
        for (Entry e : entries) {
            plugin.getProgressManager().revoke(uuid, e.id());
            count++;
        }
        plugin.getLeaderboardManager().invalidate();
        return count;
    }

    // --- /oaklog debug structures ---

    private LiteralCommandNode<CommandSourceStack> buildDebugCommand() {
        return Commands.literal("debug")
                .requires(s -> s.getSender().hasPermission("oaklog.admin.info"))
                .then(Commands.literal("structures")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(net.kyori.adventure.text.Component
                                        .text("Run this in-game, standing where detection should fire."));
                                return Command.SINGLE_SUCCESS;
                            }
                            for (String line : plugin.getStructureListener().diagnose(player)) {
                                sender.sendMessage(net.kyori.adventure.text.Component.text(line));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    // --- /oaklog reload ---

    private LiteralCommandNode<CommandSourceStack> buildReloadCommand() {
        return Commands.literal("reload")
                .requires(s -> s.getSender().hasPermission("oaklog.admin.reload"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    boolean ok = plugin.reload();
                    if (ok) {
                        audit(ctx, "RELOAD", "-", "ok");
                        messages().sendCommand(sender, "reload-success-simple");
                    } else {
                        messages().sendCommand(sender, "reload-failed");
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    // --- /oaklog info <entry> ---

    private LiteralCommandNode<CommandSourceStack> buildInfoCommand() {
        return Commands.literal("info")
                .requires(s -> s.getSender().hasPermission("oaklog.admin.info"))
                .then(Commands.argument("key", StringArgumentType.greedyString())
                        .suggests(entrySuggestions())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String key = StringArgumentType.getString(ctx, "key").toLowerCase(Locale.ROOT);
                            Integer entryId = resolveEntryId(key);
                            if (entryId == null) {
                                messages().sendCommand(sender, "unknown-entry",
                                        Placeholder.unparsed("entry", key));
                                return Command.SINGLE_SUCCESS;
                            }
                            Entry entry = plugin.getEntryRegistry().get(entryId);
                            if (entry == null) {
                                messages().sendCommand(sender, "unknown-entry",
                                        Placeholder.unparsed("entry", key));
                                return Command.SINGLE_SUCCESS;
                            }
                            String sets = String.join(", ", plugin.getEntryRegistry().setsFor(entryId));
                            if (sets.isEmpty()) sets = "(none)";
                            messages().sendCommand(sender, "info-line",
                                    Placeholder.unparsed("entry", entry.namespacedKey()),
                                    Placeholder.unparsed("display", entry.displayName()),
                                    Placeholder.unparsed("category", entry.category().id()),
                                    Placeholder.unparsed("sets", sets),
                                    Placeholder.unparsed("status",
                                            entry.isRetired() ? "retired" : "live"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    // --- Suggestions ---

    private SuggestionProvider<CommandSourceStack> entrySuggestions() {
        return (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            int added = 0;
            for (Entry e : plugin.getEntryRegistry().all()) {
                if (e.isRetired()) continue;
                String full = e.namespacedKey();
                String shorthand = e.key();
                if (full.startsWith(remaining) || shorthand.startsWith(remaining)) {
                    builder.suggest(full);
                    if (++added >= 50) break;
                }
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> categorySuggestions() {
        return (ctx, builder) -> {
            for (Category c : Category.values()) {
                if (c.id().startsWith(builder.getRemaining().toLowerCase(Locale.ROOT))) {
                    builder.suggest(c.id());
                }
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> setSuggestions() {
        return (ctx, builder) -> {
            for (SetDefinition def : plugin.getSetsConfig().sets()) {
                if (def.id().startsWith(builder.getRemaining().toLowerCase(Locale.ROOT))) {
                    builder.suggest(def.id());
                }
            }
            return builder.buildFuture();
        };
    }

    // --- Helpers ---

    private Player resolveSinglePlayer(CommandContext<CommandSourceStack> ctx, String argName) {
        PlayerSelectorArgumentResolver resolver = ctx.getArgument(argName, PlayerSelectorArgumentResolver.class);
        try {
            List<Player> resolved = resolver.resolve(ctx.getSource());
            return resolved.isEmpty() ? null : resolved.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolvePlayerName(CommandContext<CommandSourceStack> ctx, String argName) {
        Player p = resolveSinglePlayer(ctx, argName);
        return p != null ? p.getName() : "unknown";
    }

    private Integer resolveEntryId(String key) {
        // Allow shorthand: "diamond" -> "minecraft:diamond"
        if (!key.contains(":")) {
            Integer id = plugin.getEntryRegistry().idFor("minecraft", key);
            if (id != null) return id;
        }
        return plugin.getEntryRegistry().idFor(key);
    }

    private void audit(CommandContext<CommandSourceStack> ctx, String action, String target, String detail) {
        String actor = ctx.getSource().getSender().getName();
        plugin.getAuditLog().record(action, actor, "target=" + target + " " + detail);
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }
}
