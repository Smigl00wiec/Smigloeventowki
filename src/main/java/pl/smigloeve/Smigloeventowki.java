package pl.smigloeve;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class Smigloeventowki extends JavaPlugin {

    public static final String HAK = "HAK";
    public static final String BABECZKA = "BABECZKA";
    public static final String GITARA = "GITARA";
    public static final String MIKROFON = "MIKROFON";
    public static final String PREZENT = "PREZENT";

    /** Stale sloty w GUI (4x9 = 36 slotow). */
    public static final Map<String, Integer> SLOTS = new LinkedHashMap<>();
    static {
        SLOTS.put(HAK, 11);
        SLOTS.put(BABECZKA, 12);
        SLOTS.put(GITARA, 13);
        SLOTS.put(MIKROFON, 14);
        SLOTS.put(PREZENT, 15);
    }

    public static final String PERM = "smigloeve.admin";

    public NamespacedKey idKey;
    public NamespacedKey wandKey;

    private final Map<String, ItemStack> templates = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final List<Region> regions = new ArrayList<>();
    public final Map<UUID, Location[]> selections = new HashMap<>();
    public GiftManager gifts;

    public record Region(String world, int minCx, int maxCx, int minCz, int maxCz) {
        boolean contains(Location l) {
            if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
            int cx = l.getBlockX() >> 4, cz = l.getBlockZ() >> 4;
            return cx >= minCx && cx <= maxCx && cz >= minCz && cz <= maxCz;
        }
    }

    @Override
    public void onEnable() {
        idKey = new NamespacedKey(this, "eventitem");
        wandKey = new NamespacedKey(this, "regionwand");
        getDataFolder().mkdirs();
        loadItems();
        loadRegions();
        gifts = new GiftManager(this);
        ItemListener listener = new ItemListener(this);
        Bukkit.getPluginManager().registerEvents(listener, this);
        Bukkit.getPluginManager().registerEvents(gifts, this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        listener.startTasks();
        var cmd = getCommand("smigloeve");
        if (cmd != null) {
            Commands handler = new Commands(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getLogger().info("Smigloeventowki wlaczone.");
    }

    @Override
    public void onDisable() {
        if (gifts != null) gifts.endAll();
    }

    // ---------- tekst ----------
    public Component text(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s)
                .decoration(TextDecoration.ITALIC, false);
    }

    public void msg(CommandSender s, String m) {
        s.sendMessage(text("&8[&bSmigloeve&8] &r" + m));
    }

    // ---------- itemy ----------
    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public ItemStack template(String id) {
        ItemStack t = templates.get(id);
        return t == null ? null : t.clone();
    }

    public void setTemplate(String id, ItemStack item) {
        ItemStack c = item.clone();
        c.setAmount(1);
        templates.put(id, c);
        saveItems();
    }

    private ItemStack createDefault(String id) {
        Material m;
        String name;
        List<String> lore;
        switch (id) {
            case HAK -> {
                m = Material.BLAZE_ROD;
                name = "&6&lHak Foxiego";
                lore = List.of("&7Uderz gracza: Slowness II i brak skakania (5s).", "&8Cooldown: 90s");
            }
            case BABECZKA -> {
                m = Material.COOKIE;
                name = "&d&lBabeczka";
                lore = List.of("&7Zjedz ja kiedy chcesz!", "&73 zlote serca, Regeneracja II (10s), Odpornosc I (15s).", "&8Brak cooldownu");
            }
            case GITARA -> {
                m = Material.AMETHYST_SHARD;
                name = "&5&lGitara Bonniego";
                lore = List.of("&7Uderz gracza: Lewitacja I dla was obu (5s).", "&8Cooldown: 90s");
            }
            case MIKROFON -> {
                m = Material.GOAT_HORN;
                name = "&c&lMikrofon Freddiego";
                lore = List.of("&7PPM: fala uderzeniowa sciaga elytre", "&7i blokuje jej zalozenie na 8s.", "&8Cooldown: 120s");
            }
            default -> {
                m = Material.NETHER_STAR;
                name = "&a&lPrezent Niespodzianka";
                lore = List.of("&7PPM: zamyka graczy w prezencie 8x8 na 10s.", "&7W srodku mozna stawiac tylko pajeczyny.", "&8Cooldown: 120s");
            }
        }
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(text(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(text(s));
        meta.lore(l);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        if (id.equals(BABECZKA)) {
            FoodComponent fc = meta.getFood();
            fc.setCanAlwaysEat(true);
            fc.setNutrition(1);
            fc.setSaturation(0f);
            meta.setFood(fc);
        }
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack createWand() {
        ItemStack it = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(text("&e&lSiekierka regionow"));
        meta.lore(List.of(text("&7LPM - punkt 1, PPM - punkt 2"), text("&7Zaznaczone chunki: eventowki zablokowane")));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, "wand");
        it.setItemMeta(meta);
        return it;
    }

    private void loadItems() {
        File f = new File(getDataFolder(), "items.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String id : SLOTS.keySet()) {
            ItemStack it = y.getItemStack("items." + id);
            if (it == null || !id.equals(idOf(it))) it = createDefault(id);
            templates.put(id, it);
        }
        saveItems();
    }

    public void saveItems() {
        File f = new File(getDataFolder(), "items.yml");
        YamlConfiguration y = new YamlConfiguration();
        for (var e : templates.entrySet()) y.set("items." + e.getKey(), e.getValue());
        try { y.save(f); } catch (IOException ex) { getLogger().warning("Blad zapisu items.yml: " + ex.getMessage()); }
    }

    // ---------- GUI ----------
    public void openGui(Player p, boolean edit) {
        GuiHolder h = new GuiHolder(edit);
        Inventory inv = Bukkit.createInventory(h, 36, text(edit ? "&8Smigloeve - edycja" : "&8Smigloeventowki"));
        h.setInventory(inv);
        if (!edit) {
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta gm = glass.getItemMeta();
            gm.displayName(Component.text(" "));
            glass.setItemMeta(gm);
            for (int i = 0; i < 36; i++) inv.setItem(i, glass);
        }
        for (var e : SLOTS.entrySet()) inv.setItem(e.getValue(), template(e.getKey()));
        p.openInventory(inv);
    }

    // ---------- cooldowny ----------
    public boolean onCooldown(Player p, String id) {
        Map<String, Long> m = cooldowns.get(p.getUniqueId());
        long until = m == null ? 0L : m.getOrDefault(id, 0L);
        long rem = until - System.currentTimeMillis();
        if (rem > 0) {
            p.sendActionBar(text("&cCooldown: &e" + ((rem + 999) / 1000) + "s"));
            return true;
        }
        return false;
    }

    public void setCooldown(Player p, String id, int seconds) {
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(id, System.currentTimeMillis() + seconds * 1000L);
    }

    public void resetCooldowns(UUID uuid) {
        cooldowns.remove(uuid);
    }

    // ---------- regiony ----------
    public boolean isBlocked(Location l) {
        for (Region r : regions) if (r.contains(l)) return true;
        return false;
    }

    public List<Region> regions() { return regions; }

    public void addRegion(Region r) { regions.add(r); saveRegions(); }

    public void removeRegion(int index) { regions.remove(index); saveRegions(); }

    private void loadRegions() {
        File f = new File(getDataFolder(), "regions.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String s : y.getStringList("regions")) {
            String[] a = s.split(";");
            if (a.length != 5) continue;
            try {
                regions.add(new Region(a[0], Integer.parseInt(a[1]), Integer.parseInt(a[2]),
                        Integer.parseInt(a[3]), Integer.parseInt(a[4])));
            } catch (NumberFormatException ignored) { }
        }
    }

    private void saveRegions() {
        File f = new File(getDataFolder(), "regions.yml");
        YamlConfiguration y = new YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (Region r : regions) out.add(r.world() + ";" + r.minCx() + ";" + r.maxCx() + ";" + r.minCz() + ";" + r.maxCz());
        y.set("regions", out);
        try { y.save(f); } catch (IOException ex) { getLogger().warning("Blad zapisu regions.yml: " + ex.getMessage()); }
    }
}
