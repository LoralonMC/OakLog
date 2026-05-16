package dev.oakheart.oaklog.ui;

import dev.oakheart.oaklog.OakLog;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Base for all OakLog inventory GUIs. Implements InventoryHolder so the
 * GuiListener can identify our inventories with a single instanceof check.
 */
public abstract class OakLogGui implements InventoryHolder {

    protected final OakLog plugin;
    protected final Player viewer;
    protected final UUID targetUuid;
    protected Inventory inventory;

    protected OakLogGui(OakLog plugin, Player viewer, UUID targetUuid) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetUuid = targetUuid;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public abstract void open();

    public abstract void onClick(int slot, ClickType clickType);

    public boolean isSelfView() {
        return viewer.getUniqueId().equals(targetUuid);
    }
}
