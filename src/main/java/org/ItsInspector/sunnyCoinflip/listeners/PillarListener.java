package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Implementazione compatta della modalità Pillars inclusa nel progetto originale. */
public final class PillarListener implements Listener {
    private final SunnyCoinflip plugin;
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private BukkitTask countdownTask;

    public PillarListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    public void startPillarMatch(Player opponent) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null || match.getOpponent() != null || match.isPlaying()) {
            opponent.sendMessage("§cQuesto match Pillars non è disponibile.");
            return;
        }
        Player creator = Bukkit.getPlayer(match.getCreator());
        if (creator == null || !creator.isOnline()) {
            plugin.getGameManager().setActivePillarMatch(null);
            opponent.sendMessage("§cIl creator non è più online.");
            return;
        }
        if (!SunnyCoinflip.getEconomy().has(creator, match.getAmount())
                || !SunnyCoinflip.getEconomy().has(opponent, match.getAmount())) {
            opponent.sendMessage("§cSaldo insufficiente di uno dei partecipanti.");
            return;
        }
        Location first = plugin.getGameManager().getPillarFirst();
        Location second = plugin.getGameManager().getPillarOpponent();
        if (first == null || second == null) {
            opponent.sendMessage("§cLe posizioni Pillars non sono configurate.");
            return;
        }

        SunnyCoinflip.getEconomy().withdrawPlayer(creator, match.getAmount());
        SunnyCoinflip.getEconomy().withdrawPlayer(opponent, match.getAmount());
        match.setOpponent(opponent.getUniqueId());
        match.setOpponentJoinTime(System.currentTimeMillis());
        match.setStarted(true);
        snapshots.put(creator.getUniqueId(), new Snapshot(creator));
        snapshots.put(opponent.getUniqueId(), new Snapshot(opponent));
        prepare(creator, first);
        prepare(opponent, second);
        startCountdown(match, creator, opponent);
    }

    private void startCountdown(PillarMatch expected, Player creator, Player opponent) {
        final int[] seconds = {Math.max(1, plugin.getGameManager().getPillarCountdown())};
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.getGameManager().getActivePillarMatch() != expected) {
                cancelCountdown();
                return;
            }
            if (!creator.isOnline() || !opponent.isOnline()) {
                finish(null, true, "§cMatch annullato: un partecipante è offline.");
                return;
            }
            if (seconds[0] <= 0) {
                cancelCountdown();
                expected.setPlaying(true);
                expected.setStartTime(System.currentTimeMillis());
                creator.sendTitle("§aVIA!", "§7Rimani sul pillar", 0, 30, 10);
                opponent.sendTitle("§aVIA!", "§7Rimani sul pillar", 0, 30, 10);
                return;
            }
            creator.sendTitle("§e" + seconds[0], "§7Preparati", 0, 18, 2);
            opponent.sendTitle("§e" + seconds[0], "§7Preparati", 0, 18, 2);
            creator.playSound(creator.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
            opponent.playSound(opponent.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
            seconds[0]--;
        }, 0L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null || !includes(match, event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        if (!match.isPlaying()) {
            Location locked = event.getFrom().clone();
            locked.setYaw(event.getTo().getYaw());
            locked.setPitch(event.getTo().getPitch());
            event.setTo(locked);
            return;
        }
        if (event.getTo().getY() <= plugin.getGameManager().getMaxHeight()) {
            finish(match.getCreator().equals(event.getPlayer().getUniqueId()) ? match.getOpponent() : match.getCreator(), false,
                    "§c" + event.getPlayer().getName() + " è caduto dal pillar.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null || !includes(match, player.getUniqueId())) return;
        if (!match.isPlaying()) {
            event.setCancelled(true);
            return;
        }
        if (player.getHealth() - event.getFinalDamage() <= 0.0) {
            event.setCancelled(true);
            UUID winner = match.getCreator().equals(player.getUniqueId()) ? match.getOpponent() : match.getCreator();
            finish(winner, false, "§c" + player.getName() + " è stato eliminato.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null) return;
        if (includes(match, victim.getUniqueId()) != includes(match, attacker.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null || !includes(match, event.getPlayer().getUniqueId())) return;
        UUID winner = match.getCreator().equals(event.getPlayer().getUniqueId()) ? match.getOpponent() : match.getCreator();
        finish(winner, match.getOpponent() == null, "§c" + event.getPlayer().getName() + " si è disconnesso.");
    }

    public void handleServerShutdown() {
        if (plugin.getGameManager().getActivePillarMatch() != null) finish(null, true, null);
    }

    private void prepare(Player player, Location location) {
        player.teleport(location.clone());
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.getInventory().clear();
        player.getInventory().addItem(new ItemStack(Material.STICK));
        player.getInventory().addItem(new ItemStack(Material.SNOWBALL, 16));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        restoreHealth(player);
        Bukkit.getScheduler().runTask(plugin, () -> restoreHealth(player));
    }

    private void finish(UUID winnerId, boolean refund, String message) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null) return;
        plugin.getGameManager().setActivePillarMatch(null);
        cancelCountdown();
        if (message != null) {
            for (UUID id : participantIds(match)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) player.sendMessage(message);
            }
        }
        if (match.getOpponent() != null) {
            if (refund || winnerId == null) {
                SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(match.getCreator()), match.getAmount());
                SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(match.getOpponent()), match.getAmount());
                refundBets(match);
            } else {
                SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(winnerId), match.getAmount() * 2.0);
                payBets(match, winnerId);
                Player winner = Bukkit.getPlayer(winnerId);
                if (winner != null) winner.sendMessage("§6Hai vinto il match Pillars!");
            }
        }
        for (Map.Entry<UUID, Snapshot> entry : new HashMap<>(snapshots).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) entry.getValue().restore(player);
        }
        snapshots.clear();
    }

    private void refundBets(PillarMatch match) {
        for (Map.Entry<UUID, Double> bet : match.getCreatorBets().entrySet()) {
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(bet.getKey()), bet.getValue());
        }
        for (Map.Entry<UUID, Double> bet : match.getOpponentBets().entrySet()) {
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(bet.getKey()), bet.getValue());
        }
    }

    private void payBets(PillarMatch match, UUID winnerId) {
        Map<UUID, Double> winners = winnerId.equals(match.getCreator()) ? match.getCreatorBets() : match.getOpponentBets();
        Map<UUID, Double> losers = winnerId.equals(match.getCreator()) ? match.getOpponentBets() : match.getCreatorBets();
        double winnerPool = winners.values().stream().mapToDouble(Double::doubleValue).sum();
        double loserPool = losers.values().stream().mapToDouble(Double::doubleValue).sum();
        if (winnerPool <= 0.0) return;
        for (Map.Entry<UUID, Double> bet : winners.entrySet()) {
            double payout = bet.getValue() + loserPool * (bet.getValue() / winnerPool);
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(bet.getKey()), payout);
        }
    }

    private boolean includes(PillarMatch match, UUID id) {
        return match.getCreator().equals(id) || (match.getOpponent() != null && match.getOpponent().equals(id));
    }

    private UUID[] participantIds(PillarMatch match) {
        return match.getOpponent() == null ? new UUID[]{match.getCreator()} : new UUID[]{match.getCreator(), match.getOpponent()};
    }

    private void restoreHealth(Player player) {
        if (!player.isOnline() || player.isDead()) return;
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        player.setHealth(attribute == null ? 20.0 : attribute.getValue());
    }

    private void cancelCountdown() {
        if (countdownTask != null && !countdownTask.isCancelled()) countdownTask.cancel();
        countdownTask = null;
    }

    private static final class Snapshot {
        private final Location location;
        private final ItemStack[] storage;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        private final GameMode gameMode;
        private final double health;
        private final int food;
        private final boolean allowFlight;
        private final boolean flying;

        private Snapshot(Player player) {
            location = player.getLocation().clone();
            storage = cloneItems(player.getInventory().getStorageContents());
            armor = cloneItems(player.getInventory().getArmorContents());
            offhand = player.getInventory().getItemInOffHand().clone();
            gameMode = player.getGameMode();
            health = player.getHealth();
            food = player.getFoodLevel();
            allowFlight = player.getAllowFlight();
            flying = player.isFlying();
        }

        private void restore(Player player) {
            player.teleport(location);
            player.setGameMode(gameMode);
            player.getInventory().setStorageContents(cloneItems(storage));
            player.getInventory().setArmorContents(cloneItems(armor));
            player.getInventory().setItemInOffHand(offhand.clone());
            player.setFoodLevel(food);
            AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double max = attribute == null ? 20.0 : attribute.getValue();
            player.setHealth(Math.max(1.0, Math.min(health, max)));
            player.setAllowFlight(allowFlight || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
            if (player.getAllowFlight()) player.setFlying(flying);
        }

        private static ItemStack[] cloneItems(ItemStack[] source) {
            ItemStack[] result = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].clone();
            return result;
        }
    }
}
