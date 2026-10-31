package pl.smigloeve;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiHolder implements InventoryHolder {
    public final boolean edit;
    private Inventory inventory;

    public GuiHolder(boolean edit) { this.edit = edit; }

    public void setInventory(Inventory inv) { this.inventory = inv; }

    @Override
    public Inventory getInventory() { return inventory; }
}
