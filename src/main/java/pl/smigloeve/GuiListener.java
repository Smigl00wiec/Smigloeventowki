package pl.smigloeve;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

public final class GuiListener implements Listener {
    private final Smigloeventowki plugin;

    public GuiListener(Smigloeventowki plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder h)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission(Smigloeventowki.PERM)) { e.setCancelled(true); return; }
        if (h.edit) return; // edycja: wszystko dozwolone
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        ItemStack it = e.getCurrentItem();
        String id = plugin.idOf(it);
        if (id == null) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof GuiHolder h && !h.edit) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (!(inv.getHolder() instanceof GuiHolder h) || !h.edit) return;
        if (!(e.getPlayer() instanceof Player p) || !p.hasPermission(Smigloeventowki.PERM)) return;
        int saved = 0;
        Map<Integer, ItemStack> giveBack = new HashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType().isAir()) continue;
            String id = plugin.idOf(it);
            if (id == null) {
                // brak tagu: jezeli lezy w slocie eventowki, przypisz jej id
                for (var en : Smigloeventowki.SLOTS.entrySet()) {
                    if (en.getValue() == slot) {
                        id = en.getKey();
                        ItemStack c = it.clone();
                        ItemMeta m = c.getItemMeta();
                        m.getPersistentDataContainer().set(plugin.idKey, PersistentDataType.STRING, id);
                        c.setItemMeta(m);
                        it = c;
                    }
                }
            }
            if (id != null && Smigloeventowki.SLOTS.containsKey(id)) {
                plugin.setTemplate(id, it);
                saved++;
            } else {
                giveBack.put(slot, it);
            }
        }
        for (ItemStack it : giveBack.values()) {
            for (ItemStack rest : p.getInventory().addItem(it).values())
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        plugin.msg(p, "&aZapisano eventowek: &e" + saved);
    }
}
