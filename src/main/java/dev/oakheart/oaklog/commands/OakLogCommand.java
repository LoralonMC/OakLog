package dev.oakheart.oaklog.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.oakheart.command.CommandRegistrar;
import dev.oakheart.message.MessageManager;
import dev.oakheart.oaklog.OakLog;
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
                .requires(source -> source.getSender().hasPermission("oaklog.use"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        messages().sendCommand(sender, "player-only");
                        return Command.SINGLE_SUCCESS;
                    }
                    openHub(player, player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .requires(source -> source.getSender().hasPermission("oaklog.view-others"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player viewer)) {
                                messages().sendCommand(sender, "player-only");
                                return Command.SINGLE_SUCCESS;
                            }
                            PlayerSelectorArgumentResolver resolver =
                                    ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                            List<Player> targets;
                            try {
                                targets = resolver.resolve(ctx.getSource());
                            } catch (Exception e) {
                                messages().sendCommand(sender, "player-not-found");
                                return Command.SINGLE_SUCCESS;
                            }
                            if (targets.isEmpty()) {
                                messages().sendCommand(sender, "player-not-found");
                                return Command.SINGLE_SUCCESS;
                            }
                            openHub(viewer, targets.get(0));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        String alias = plugin.getConfigManager().getCommandAlias();
        List<String> aliases = alias == null || alias.isBlank() ? List.of() : List.of(alias);
        CommandRegistrar.register(plugin, root, "Open your collection log", aliases);
    }

    private void openHub(Player viewer, Player target) {
        new HubGui(plugin, viewer, target.getUniqueId(), iconRenderer).open();
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }
}
