package org.ItsInspector.sunnyCoinflip.managers;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PillarSpectatorManager implements Listener {

    private static PillarSpectatorManager instance;

    private final SunnyCoinflip plugin;
    private final Map<UUID, SpectatorState> spectators = new HashMap<>();
    private final BukkitTask roundWatcher;

    private PillarSpectatorManager(SunnyCoinflip plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.roundWatcher = Bukkit.getScheduler().runTaskTimer(plugin, this::checkRound, 10L, 10L);
    }

    public static PillarSpectatorManager get(SunnyCoinflip plugin) {
        if (instance == null) {
            instance = new PillarSpectatorManager(plugin);
        }
        return instance;
    }

    public boolean startSpectating(Player player) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null || !match.isPlaying()) {
            player.sendMessage("§cNon c'è nessun round di Pillars iniziato da spectare!");
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (uuid.equals(match.getCreator()) ||
                (match.getOpponent() != null && uuid.equals(match.getOpponent()))) {
            player.sendMessage("§cSei già un partecipante del round!");
            return false;
        }

        if (spectators.containsKey(uuid)) {
            player.sendMessage("§eStai già spectando il round di Pillars.");
            return true;
        }

        World pillarsWorld = Bukkit.getWorld("pillars");
        if (pillarsWorld == null) {
            player.sendMessage("§cIl mondo §fpillars §cnon è caricato.");
            return false;
        }

        Location destination = plugin.getGameManager().getPillarFirst();
        if (destination == null || destination.getWorld() == null ||
                !destination.getWorld().getName().equalsIgnoreCase("pillars")) {
            destination = pillarsWorld.getSpawnLocation();
        } else {
            destination = destination.clone().add(0.5, 5.0, 0.5);
        }

        spectators.put(uuid, new SpectatorState(player.getLocation().clone(), player.getGameMode()));

        if (!player.teleport(destination)) {
            SpectatorState state = spectators.remove(uuid);
            if (state != null) {
                player.setGameMode(state.gameMode());
            }
            player.sendMessage("§cNon è stato possibile teletrasportarti nel mondo Pillars.");
            return false;
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage("§aOra stai spectando il round di Pillars. §7Esci dal mondo per tornare indietro.");
        return true;
    }

    public boolean isSpectating(UUID uuid) {
        return spectators.containsKey(uuid);
    }

    public void stopSpectating(Player player, String message) {
        SpectatorState state = spectators.remove(player.getUniqueId());
        if (state == null) return;

        player.setSpectatorTarget(null);
        player.setGameMode(state.gameMode());
        player.teleport(state.location());

        if (message != null && !message.isBlank()) {
            player.sendMessage(message);
        }
    }

    private void checkRound() {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match != null && match.isPlaying()) return;

        for (UUID uuid : spectators.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                stopSpectating(player, "§eIl round di Pillars è terminato.");
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!isSpectating(player.getUniqueId())) return;

        if (!player.getWorld().getName().equalsIgnoreCase("pillars")) {
            stopSpectating(player, "§eHai smesso di spectare il round di Pillars.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Lo stato resta salvato: verrà ripristinato al prossimo accesso.
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isSpectating(event.getPlayer().getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    stopSpectating(event.getPlayer(), "§eLa tua posizione precedente è stata ripristinata."));
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) return;

        roundWatcher.cancel();
        for (UUID uuid : spectators.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                stopSpectating(player, null);
            }
        }
        instance = null;
    }

    private record SpectatorState(Location location, GameMode gameMode) {}
}
