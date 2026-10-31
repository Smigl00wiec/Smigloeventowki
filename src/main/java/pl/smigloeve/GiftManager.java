package pl.smigloeve;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.*;

public final class GiftManager implements Listener {
    private static final long CHUNK_LOCK_MS = 120_000L;
    private static final int DURATION_TICKS = 10 * 20;

    private final Smigloeventowki plugin;
    private final List<Gift> gifts = new ArrayList<>();
    private final Map<String, Long> chunkLocks = new HashMap<>();

    public GiftManager(Smigloeventowki plugin) { this.plugin = plugin; }

    private static final class Gift {
        final World world;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final List<BlockState> saved = new ArrayList<>();
        final Set<Location> cobwebs = new HashSet<>();
        int taskId = -1;

        Gift(World w, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.world = w;
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }

        boolean inBox(World w, int x, int y, int z) {
            return w.equals(world) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        boolean inInterior(int x, int y, int z) {
            return x > minX && x < maxX && y > minY && y < maxY && z > minZ && z < maxZ;
        }
    }

    private static String key(World w, int cx, int cz) { return w.getName() + ":" + cx + ":" + cz; }

    /** Tworzy prezent wokol gracza. Zwraca true, jezeli sie udalo. */
    public boolean create(Player p) {
        Location l = p.getLocation();
        World w = l.getWorld();
        int px = l.getBlockX(), py = l.getBlockY(), pz = l.getBlockZ();
        int minX = px - 4, maxX = px + 3;
        int minZ = pz - 4, maxZ = pz + 3;
        int minY = py - 1, maxY = py + 6;
        if (minY < w.getMinHeight() || maxY >= w.getMaxHeight()) {
            plugin.msg(p, "&cNie mozna postawic prezentu na tej wysokosci.");
            return false;
        }

        long now = System.currentTimeMillis();
        chunkLocks.values().removeIf(t -> t < now);
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                Long until = chunkLocks.get(key(w, cx, cz));
                if (until != null && until > now) {
                    p.sendActionBar(plugin.text("&cW tym chunku stal juz prezent! Odczekaj &e" + ((until - now + 999) / 1000) + "s"));
                    return false;
                }
            }
        }
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
                chunkLocks.put(key(w, cx, cz), now + CHUNK_LOCK_MS);

        Gift g = new Gift(w, minX, minY, minZ, maxX, maxY, maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (g.inInterior(x, y, z)) continue;
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() == Material.BEDROCK || b.getState() instanceof TileState) continue;
                    g.saved.add(b.getState());
                    int ix = x - minX, iz = z - minZ;
                    boolean ribbon = ix == 3 || ix == 4 || iz == 3 || iz == 4;
                    b.setType(ribbon ? Material.YELLOW_CONCRETE : Material.RED_CONCRETE, false);
                }
            }
        }
        gifts.add(g);

        // gracze stojacy w scianach zostaja przeniesieni do srodka
        Location inside = new Location(w, px + 0.5, py, pz + 0.5);
        for (Player o : w.getPlayers()) {
            Location ol = o.getLocation();
            if (g.inBox(w, ol.getBlockX(), ol.getBlockY(), ol.getBlockZ())
                    && !g.inInterior(ol.getBlockX(), ol.getBlockY(), ol.getBlockZ())) {
                o.teleport(inside);
            }
        }
        w.playSound(inside, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.8f);
        for (Player o : w.getPlayers()) {
            Location ol = o.getLocation();
            if (g.inInterior(ol.getBlockX(), ol.getBlockY(), ol.getBlockZ()))
                o.sendActionBar(plugin.text("&aZostales zamkniety w prezencie na 10s! &7Mozesz stawiac tylko pajeczyny."));
        }

        g.taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> end(g), DURATION_TICKS).getTaskId();
        return true;
    }

    private void end(Gift g) {
        if (!gifts.remove(g)) return;
        if (g.taskId != -1) Bukkit.getScheduler().cancelTask(g.taskId);
        for (Location c : g.cobwebs) {
            Block b = c.getBlock();
            if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
        }
        for (BlockState s : g.saved) s.update(true, false);
    }

    public void endAll() {
        for (Gift g : new ArrayList<>(gifts)) end(g);
    }

    private Gift giftAt(Block b) {
        for (Gift g : gifts) if (g.inBox(b.getWorld(), b.getX(), b.getY(), b.getZ())) return g;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Gift g = giftAt(b);
        if (g == null) return;
        if (g.inInterior(b.getX(), b.getY(), b.getZ()) && b.getType() == Material.COBWEB) {
            g.cobwebs.remove(b.getLocation());
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        Gift g = giftAt(b);
        if (g == null) return;
        if (g.inInterior(b.getX(), b.getY(), b.getZ()) && b.getType() == Material.COBWEB) {
            g.cobwebs.add(b.getLocation());
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> giftAt(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> giftAt(b) != null);
    }
}
