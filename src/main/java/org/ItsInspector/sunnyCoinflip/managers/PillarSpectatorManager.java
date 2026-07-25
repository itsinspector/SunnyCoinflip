package org.ItsInspector.sunnyCoinflip.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.scheduler.BukkitTask;

public final class PillarSpectatorManager implements Listener {
    private static PillarSpectatorManager instance;
    private final SunnyCoinflip plugin;
    private final Map<UUID, SpectatorState> spectators = new HashMap();
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
        PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
        if (match != null && match.isPlaying()) {
            UUID uuid = player.getUniqueId();
            if (!uuid.equals(match.getCreator()) && (match.getOpponent() == null || !uuid.equals(match.getOpponent()))) {
                if (this.spectators.containsKey(uuid)) {
                    player.sendMessage("§eStai già spectando il round di Pillars.");
                    return true;
                } else {
                    World pillarsWorld = Bukkit.getWorld("pillars");
                    if (pillarsWorld == null) {
                        player.sendMessage("§cIl mondo §fpillars §cnon è caricato.");
                        return false;
                    } else {
                        Location destination = this.plugin.getGameManager().getPillarFirst();
                        if (destination != null && destination.getWorld() != null && destination.getWorld().getName().equalsIgnoreCase("pillars")) {
                            destination = destination.clone().add((double)0.5F, (double)5.0F, (double)0.5F);
                        } else {
                            destination = pillarsWorld.getSpawnLocation();
                        }

                        this.spectators.put(uuid, new SpectatorState(player.getLocation().clone(), player.getGameMode()));
                        if (!player.teleport(destination)) {
                            SpectatorState state = (SpectatorState)this.spectators.remove(uuid);
                            if (state != null) {
                                player.setGameMode(state.gameMode());
                            }

                            player.sendMessage("§cNon è stato possibile teletrasportarti nel mondo Pillars.");
                            return false;
                        } else {
                            player.setGameMode(GameMode.SPECTATOR);
                            player.sendMessage("§aOra stai spectando il round di Pillars. §7Esci dal mondo per tornare indietro.");
                            return true;
                        }
                    }
                }
            } else {
                player.sendMessage("§cSei già un partecipante del round!");
                return false;
            }
        } else {
            player.sendMessage("§cNon c'è nessun round di Pillars iniziato da spectare!");
            return false;
        }
    }

    public boolean isSpectating(UUID uuid) {
        return this.spectators.containsKey(uuid);
    }

    public void stopSpectating(Player player, String message) {
        SpectatorState state = (SpectatorState)this.spectators.remove(player.getUniqueId());
        if (state != null) {
            player.setSpectatorTarget((Entity)null);
            player.setGameMode(state.gameMode());
            player.teleport(state.location());
            if (message != null && !message.isBlank()) {
                player.sendMessage(message);
            }

        }
    }

    private void checkRound() {
        PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
        if (match == null || !match.isPlaying()) {
            for(UUID uuid : (UUID[])this.spectators.keySet().toArray((x$0) -> new UUID[x$0])) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    this.stopSpectating(player, "§eIl round di Pillars è terminato.");
                }
            }

        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (this.isSpectating(player.getUniqueId())) {
            if (!player.getWorld().getName().equalsIgnoreCase("pillars")) {
                this.stopSpectating(player, "§eHai smesso di spectare il round di Pillars.");
            }

        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (this.isSpectating(event.getPlayer().getUniqueId())) {
            Bukkit.getScheduler().runTask(this.plugin, () -> this.stopSpectating(event.getPlayer(), "§eLa tua posizione precedente è stata ripristinata."));
        }

    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this.plugin) {
            this.roundWatcher.cancel();

            for(UUID uuid : (UUID[])this.spectators.keySet().toArray((x$0) -> new UUID[x$0])) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    this.stopSpectating(player, (String)null);
                }
            }

            instance = null;
        }
    }

    private static record SpectatorState(Location location, GameMode gameMode) {
    }
}
