package pl.smigloeve;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class Commands implements CommandExecutor, TabCompleter {
    private final Smigloeventowki plugin;

    public Commands(Smigloeventowki plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission(Smigloeventowki.PERM)) {
            plugin.msg(s, "&cNie masz uprawnien.");
            return true;
        }
        if (a.length == 0) {
            plugin.msg(s, "&e/smigloeve gui &7| &e/smigloeve editgui &7| &e/smigloeve region [list|usun <nr>] &7| &e/smigloeve resetuj <nick>");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "gui" -> {
                if (!(s instanceof Player p)) { plugin.msg(s, "&cTylko dla graczy."); return true; }
                plugin.openGui(p, false);
            }
            case "editgui" -> {
                if (!(s instanceof Player p)) { plugin.msg(s, "&cTylko dla graczy."); return true; }
                plugin.openGui(p, true);
                plugin.msg(s, "&7Wloz eventowki (z nowa nazwa/lore) do GUI i zamknij je, aby zapisac.");
            }
            case "region" -> {
                if (a.length >= 2 && a[1].equalsIgnoreCase("list")) {
                    var list = plugin.regions();
                    if (list.isEmpty()) { plugin.msg(s, "&7Brak regionow."); return true; }
                    for (int i = 0; i < list.size(); i++) {
                        var r = list.get(i);
                        plugin.msg(s, "&e#" + (i + 1) + " &7" + r.world() + " chunki X[" + r.minCx() + ".." + r.maxCx()
                                + "] Z[" + r.minCz() + ".." + r.maxCz() + "]");
                    }
                    return true;
                }
                if (a.length >= 3 && a[1].equalsIgnoreCase("usun")) {
                    try {
                        int n = Integer.parseInt(a[2]) - 1;
                        if (n < 0 || n >= plugin.regions().size()) { plugin.msg(s, "&cZly numer."); return true; }
                        plugin.removeRegion(n);
                        plugin.msg(s, "&aUsunieto region.");
                    } catch (NumberFormatException e) { plugin.msg(s, "&cPodaj numer."); }
                    return true;
                }
                if (!(s instanceof Player p)) { plugin.msg(s, "&cTylko dla graczy."); return true; }
                p.getInventory().addItem(plugin.createWand());
                plugin.msg(s, "&aOtrzymales siekierke. &7LPM = punkt 1, PPM = punkt 2. Po zaznaczeniu obu region zapisze sie automatycznie.");
            }
            case "resetuj", "reset" -> {
                if (a.length < 2) { plugin.msg(s, "&cUzycie: /smigloeve resetuj <nick>"); return true; }
                Player online = Bukkit.getPlayerExact(a[1]);
                OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(a[1]);
                if (target == null) { plugin.msg(s, "&cNie znaleziono gracza."); return true; }
                plugin.resetCooldowns(target.getUniqueId());
                plugin.msg(s, "&aZresetowano cooldowny gracza &e" + a[1] + "&a.");
            }
            default -> plugin.msg(s, "&cNieznana podkomenda.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission(Smigloeventowki.PERM)) return out;
        if (a.length == 1) {
            for (String x : List.of("gui", "editgui", "region", "resetuj"))
                if (x.startsWith(a[0].toLowerCase())) out.add(x);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("resetuj")) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) out.add(p.getName());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("region")) {
            for (String x : List.of("list", "usun"))
                if (x.startsWith(a[1].toLowerCase())) out.add(x);
        }
        return out;
    }
}
