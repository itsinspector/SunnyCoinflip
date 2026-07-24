package org.ItsInspector.sunnyCoinflip.managers;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PillarSpectatorManager implements Listener {
    private static PillarSpectatorManager instance;
    private final SunnyCoinflip plugin;
    private final Map<UUID, SpectatorState> spectators = new HashMap<>();
    private final BukkitTask watcher;

    private PillarSpectatorManager(SunnyCoinflip plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        watcher = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public static PillarSpectatorManager get(SunnyCoinflip plugin) {
        if (instance == null) instance = new PillarSpectatorManager(plugin);
        return instance;
    }

    public boolean startSpectating(Player player) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null || !match.isPlaying()) {
            player.sendMessage("§cNon c'è nessun round di Pillars iniziato da spectare!");
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (uuid.equals(match.getCreator()) || uuid.equals(match.getOpponent())) {
            player.sendMessage("§cSei già un partecipante del round!");
            return false;
        }
        Location destination = plugin.getGameManager().getPillarFirst();
        if (destination == null) return false;
        spectators.putIfAbsent(uuid, new SpectatorState(player.getLocation().clone(), player.getGameMode()));
        player.teleport(destination.clone().add(0, 5, 0));
        applySpectator(player);
        player.sendMessage("§aOra stai spectando il round di Pillars.\n§7Esci dal mondo per tornare indietro.");
        return true;
    }

    public boolean isSpectating(UUID uuid) { return spectators.containsKey(uuid); }

    public void stopSpectating(Player player, String message) {
        SpectatorState state = spectators.remove(player.getUniqueId());
        if (state == null) return;
        player.setSpectatorTarget(null);
        player.setGameMode(state.gameMode());
        player.teleport(state.location());
        if (message != null && !message.isBlank()) player.sendMessage(message);
    }

    private void tick() {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        for (UUID uuid : spectators.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            if (match == null || !match.isPlaying()) {
                stopSpectating(player, "§eIl round di Pillars è terminato.");
            } else if (player.getGameMode() != GameMode.SPECTATOR) {
                applySpectator(player);
            }
        }
    }

    private void applySpectator(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        if (isSpectating(event.getPlayer().getUniqueId()) && !plugin.getGameManager().isPillarWorld(event.getPlayer().getWorld())) {
            stopSpectating(event.getPlayer(), "§eHai smesso di spectare il round di Pillars.");
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (isSpectating(event.getPlayer().getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> stopSpectating(event.getPlayer(), "§eLa tua posizione precedente è stata ripristinata."));
        }
    }

    @EventHandler public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) return;
        watcher.cancel();
        for (UUID uuid : spectators.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) stopSpectating(player, null);
        }
        instance = null;
    }

    private record SpectatorState(Location location, GameMode gameMode) {}
}
