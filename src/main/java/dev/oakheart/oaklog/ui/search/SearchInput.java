package dev.oakheart.oaklog.ui.search;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

public interface SearchInput {
    /**
     * Prompts the player for a search query. On submit, runs onResult on the
     * main thread with the typed text. On cancel/timeout, runs onCancel.
     */
    void prompt(Player player, Consumer<String> onResult, Runnable onCancel);
}
