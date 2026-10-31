package pl.smigloeve;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ItemListener implements Listener {
    private final Smigloeventowki plugin;
    private final Map<UUID, Long> jumpBlock = new HashMap<>();
    private final Map<UUID, Long> elytraBlock = new HashMap<>();

    public ItemListener(Smigloeventowki plugin) { this.plugin = plugin; }

    /** Zadanie pilnujace, zeby zablokowany gracz nie mial elytry na klatce. */
    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            elytraBlock.entrySet().removeIf(en -> en.getValue() < now);
            for (UUID u : elytraBlock.keySet()) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) continue;
                ItemStack chest = p.getInventory().getChestplate();
                if (chest != null && chest.getType() == Material.ELYTRA) {
                    p.getInventory().setChestplate(null);
                    p.setGliding(false);
                    giveOrDrop(p, chest);
                }
            }
        }, 5L, 5L);
    }

    private void giveOrDrop(Player p, ItemStack it) {
        for (ItemStack rest : p.getInventory().addItem(it).values())
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    private boolean elytraBlocked(Player p) {
        Long u = elytraBlock.get(p.getUniqueId());
        return u != null && u > System.currentTimeMillis();
    }

    private boolean isElytra(ItemStack it) { return it != null && it.getType() == Material.ELYTRA; }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.selections.remove(e.getPlayer().getUniqueId());
    }

    // ---------- HAK FOXIEGO + GITARA BONNIEGO ----------
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player a) || !(e.getEntity() instanceof Player v)) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        String id = plugin.idOf(a.getInventory().getItemInMainHand());
        if (id == null) return;
        if (!id.equals(Smigloeventowki.HAK) && !id.equals(Smigloeventowki.GITARA)) return;
        if (plugin.isBlocked(a.getLocation())) {
            plugin.msg(a, "&cEventowki sa tu zablokowane.");
            return;
        }
        if (plugin.onCooldown(a, id)) return;

        if (id.equals(Smigloeventowki.HAK)) {
            plugin.setCooldown(a, id, 90);
            v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5 * 20, 1));
            jumpBlock.put(v.getUniqueId(), System.currentTimeMillis() + 5000L);
            v.sendActionBar(plugin.text("&6Hak Foxiego! &7Spowolnienie i brak skakania (5s)."));
        } else {
            plugin.setCooldown(a, id, 90);
            PotionEffect lev = new PotionEffect(PotionEffectType.LEVITATION, 5 * 20, 0);
            v.addPotionEffect(lev);
            a.addPotionEffect(lev);
            v.sendActionBar(plugin.text("&5Gitara Bonniego! &7Lewitacja (5s)."));
            a.sendActionBar(plugin.text("&5Gitara Bonniego! &7Lewitujecie razem (5s)."));
        }
    }

    // blokada skakania
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (jumpBlock.isEmpty()) return;
        Player p = e.getPlayer();
        Long until = jumpBlock.get(p.getUniqueId());
        if (until == null) return;
        if (until < System.currentTimeMillis()) { jumpBlock.remove(p.getUniqueId()); return; }
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || to.getY() <= from.getY()) return;
        if (p.getVelocity().getY() <= 0.1) return;
        if (p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isInWater()
                || p.hasPotionEffect(PotionEffectType.LEVITATION)) return;
        if (Tag.CLIMBABLE.isTagged(p.getLocation().getBlock().getType())) return;
        Location n = to.clone();
        n.setY(from.getY());
        e.setTo(n);
    }

    // ---------- BABECZKA ----------
    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        String id = plugin.idOf(e.getItem());
        if (!Smigloeventowki.BABECZKA.equals(id)) return;
        Player p = e.getPlayer();
        if (plugin.isBlocked(p.getLocation())) {
            e.setCancelled(true);
            plugin.msg(p, "&cEventowki sa tu zablokowane.");
            return;
        }
        // 3 zlote serca = 6 punktow absorpcji
        p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), 6.0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10 * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 0));
    }

    // ---------- INTERAKCJE (mikrofon, prezent, siekierka, elytra) ----------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;

        // zablokowane zakladanie elytry
        if (right && isElytra(item) && elytraBlocked(p)) {
            e.setCancelled(true);
            p.sendActionBar(plugin.text("&cNie mozesz teraz zalozyc elytry!"));
            return;
        }

        // siekierka regionow
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(plugin.wandKey, PersistentDataType.STRING)) {
            if (!p.hasPermission(Smigloeventowki.PERM)) return;
            if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) return;
            e.setCancelled(true);
            Location loc = e.getClickedBlock().getLocation();
            Location[] sel = plugin.selections.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
            int idx = a == Action.LEFT_CLICK_BLOCK ? 0 : 1;
            sel[idx] = loc;
            plugin.msg(p, "&aPunkt " + (idx + 1) + " ustawiony: &7" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            if (sel[0] != null && sel[1] != null) {
                if (!sel[0].getWorld().equals(sel[1].getWorld())) {
                    plugin.msg(p, "&cPunkty musza byc w tym samym swiecie.");
                    sel[0] = null; sel[1] = null;
                    return;
                }
                int c1x = sel[0].getBlockX() >> 4, c2x = sel[1].getBlockX() >> 4;
                int c1z = sel[0].getBlockZ() >> 4, c2z = sel[1].getBlockZ() >> 4;
                var r = new Smigloeventowki.Region(sel[0].getWorld().getName(),
                        Math.min(c1x, c2x), Math.max(c1x, c2x), Math.min(c1z, c2z), Math.max(c1z, c2z));
                plugin.addRegion(r);
                int count = (r.maxCx() - r.minCx() + 1) * (r.maxCz() - r.minCz() + 1);
                plugin.msg(p, "&aUtworzono region zakazu eventowek (&e" + count + " &achunkow). Zaznacz kolejne lub wpisz /smigloeve region list.");
                plugin.selections.remove(p.getUniqueId());
            }
            return;
        }

        String id = plugin.idOf(item);
        if (id == null || !right) return;

        if (id.equals(Smigloeventowki.MIKROFON)) {
            e.setCancelled(true);
            if (plugin.isBlocked(p.getLocation())) { plugin.msg(p, "&cEventowki sa tu zablokowane."); return; }
            if (plugin.onCooldown(p, id)) return;
            plugin.setCooldown(p, id, 120);
            shootShockwave(p);
        } else if (id.equals(Smigloeventowki.PREZENT)) {
            e.setCancelled(true);
            if (plugin.isBlocked(p.getLocation())) { plugin.msg(p, "&cEventowki sa tu zablokowane."); return; }
            if (plugin.onCooldown(p, id)) return;
            if (plugin.gifts.create(p)) plugin.setCooldown(p, id, 120);
        } else if (id.equals(Smigloeventowki.HAK) || id.equals(Smigloeventowki.GITARA)) {
            // zapobiega przypadkowym akcjom prawym klikiem (np. rzucanie/uzycie przedmiotu)
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    private void shootShockwave(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        double range = 40;
        RayTraceResult r = w.rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.6,
                en -> en instanceof Player t && !t.equals(p) && t.getGameMode() != GameMode.SPECTATOR);
        double dist = range;
        Player target = null;
        if (r != null) {
            dist = r.getHitPosition().distance(eye.toVector());
            if (r.getHitEntity() instanceof Player t) target = t;
        }
        for (double d = 1.5; d <= dist; d += 1.5) {
            Location pt = eye.clone().add(dir.clone().multiply(d));
            w.spawnParticle(Particle.SONIC_BOOM, pt, 1, 0, 0, 0, 0);
        }
        w.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.2f);

        if (target == null) return;
        elytraBlock.put(target.getUniqueId(), System.currentTimeMillis() + 8000L);
        ItemStack chest = target.getInventory().getChestplate();
        if (isElytra(chest)) {
            target.getInventory().setChestplate(null);
            target.setGliding(false);
            giveOrDrop(target, chest);
        }
        target.sendActionBar(plugin.text("&cMikrofon Freddiego! &7Nie mozesz zalozyc elytry przez 8s."));
        p.sendActionBar(plugin.text("&cTrafiles gracza &e" + target.getName() + "&c!"));
    }

    // blokada zakladania elytry w ekwipunku
    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !elytraBlocked(p)) return;
        boolean involves = isElytra(e.getCurrentItem()) || isElytra(e.getCursor());
        if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) {
            involves |= isElytra(p.getInventory().getItem(e.getHotbarButton()));
        }
        if (e.getClick() == ClickType.SWAP_OFFHAND) involves |= isElytra(p.getInventory().getItemInOffHand());
        if (!involves) return;
        if (e.getSlotType() == SlotType.ARMOR || e.isShiftClick()
                || e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.SWAP_OFFHAND) {
            e.setCancelled(true);
            p.sendActionBar(plugin.text("&cNie mozesz teraz zalozyc elytry!"));
        }
    }

    // siekierka nie psuje blokow
    @EventHandler(priority = EventPriority.HIGH)
    public void onWandBreak(BlockBreakEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItemInMainHand();
        if (it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(plugin.wandKey, PersistentDataType.STRING))
            e.setCancelled(true);
    }

    // eventowki nie mozna stawiac jako bloki
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.idOf(e.getItemInHand()) != null) e.setCancelled(true);
    }
}
