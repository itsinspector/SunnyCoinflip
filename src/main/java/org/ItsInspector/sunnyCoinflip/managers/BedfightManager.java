package org.ItsInspector.sunnyCoinflip.managers;

import net.milkbowl.vault.economy.Economy;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.BedfightCoinflip;
import org.ItsInspector.sunnyCoinflip.utils.NumberParser;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gestisce la modalità BedWars/Bedfight del coinflip.
 *
 * Le tre correzioni richieste sono contrassegnate con "FIX" nel sorgente:
 * - spettatore persistente anche senza OP;
 * - rimozione immediata della spawn protection al movimento reale;
 * - vita piena subito dopo l'ingresso in queue/partita.
 */
public final class BedfightManager {
    public enum BreakResult { ALLOW, DENY, OWN_BED, ENEMY_BED }

    private final SunnyCoinflip plugin;
    private final Map<UUID, BedfightCoinflip> waitingByCreator = new LinkedHashMap<>();
    private final Map<UUID, PlayerSnapshot> waitingSnapshots = new HashMap<>();
    private final Set<UUID> awaitingCreateAmount = new HashSet<>();
    private final Map<UUID, LastHit> lastHits = new HashMap<>();
    private ActiveRound activeRound;

    public BedfightManager(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("bedwars.enabled", true);
    }

    public boolean isAvailable() {
        return isEnabled() && isArenaConfigured() && activeRound == null;
    }

    public boolean isPlaying() {
        return activeRound != null && activeRound.playing && !activeRound.finishing;
    }

    public boolean isArenaConfigured() {
        return getFirstPosition() != null && getOpponentPosition() != null
                && getFirstBedLocation() != null && getOpponentBedLocation() != null;
    }

    public boolean isParticipant(UUID playerId) {
        if (playerId == null) return false;
        if (waitingByCreator.containsKey(playerId)) return true;
        ActiveRound round = activeRound;
        return round != null && round.match.includes(playerId);
    }

    public boolean isActiveParticipant(UUID playerId) {
        ActiveRound round = activeRound;
        return round != null && round.match.includes(playerId) && !round.finishing;
    }

    public BedfightCoinflip getActiveMatch() {
        return activeRound == null ? null : activeRound.match;
    }

    public Collection<BedfightCoinflip> getWaitingChallenges() {
        return Collections.unmodifiableList(new ArrayList<>(waitingByCreator.values()));
    }

    public boolean isArenaWorld(World world) {
        if (world == null) return false;
        return sameWorld(getFirstPosition(), world)
                || sameWorld(getOpponentPosition(), world)
                || sameWorld(getFirstBedLocation(), world)
                || sameWorld(getOpponentBedLocation(), world);
    }

    public boolean isRoundWorld(World world) {
        ActiveRound round = activeRound;
        return round != null && world != null && round.world != null
                && round.world.getUID().equals(world.getUID());
    }

    public void handleSimpleCommand(Player player) {
        if (!isEnabled()) {
            player.sendMessage("§cLa modalità BedWars è disabilitata.");
            return;
        }
        if (activeRound != null && activeRound.playing && !isActiveParticipant(player.getUniqueId())) {
            startSpectating(player);
            return;
        }
        if (waitingByCreator.containsKey(player.getUniqueId())) {
            player.sendMessage("§eSei già in queue. Usa §f/cf bedwars cancel §eper uscire.");
            return;
        }
        if (!waitingByCreator.isEmpty()) {
            listChallenges(player);
            return;
        }
        awaitingCreateAmount.add(player.getUniqueId());
        player.sendMessage("§eScrivi in chat la somma del BedWars coinflip, oppure §fcancel§e.");
    }

    public boolean isAwaitingCreateAmount(UUID playerId) {
        return awaitingCreateAmount.contains(playerId);
    }

    public void handleCreateAmountChat(Player player, String message) {
        if (!awaitingCreateAmount.remove(player.getUniqueId())) return;
        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("annulla")) {
            player.sendMessage("§eCreazione annullata.");
            return;
        }
        try {
            createChallenge(player, NumberParser.parseNumber(message));
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        }
    }

    public void createChallenge(Player creator, double amount) {
        if (!isEnabled()) {
            creator.sendMessage("§cLa modalità BedWars è disabilitata.");
            return;
        }
        if (!isArenaConfigured()) {
            creator.sendMessage("§cArena BedWars non configurata completamente.");
            return;
        }
        if (activeRound != null || !waitingByCreator.isEmpty()) {
            creator.sendMessage("§cL'arena BedWars è già occupata.");
            return;
        }
        if (isParticipant(creator.getUniqueId())) {
            creator.sendMessage("§cSei già dentro una queue o partita.");
            return;
        }
        if (amount <= 0 || amount > plugin.getGameManager().getMaxAmount()) {
            creator.sendMessage("§cImporto non valido. Massimo: §f" + money(plugin.getGameManager().getMaxAmount()));
            return;
        }
        if (!economy().has(creator, amount)) {
            creator.sendMessage("§cNon hai abbastanza denaro.");
            return;
        }

        PlayerSnapshot snapshot = PlayerSnapshot.capture(creator);
        waitingSnapshots.put(creator.getUniqueId(), snapshot);
        BedfightCoinflip match = new BedfightCoinflip(creator.getUniqueId(), creator.getName(), amount);
        waitingByCreator.put(creator.getUniqueId(), match);

        prepareQueuePlayer(creator, getFirstPosition());
        creator.sendMessage("§aBedWars coinflip creato per §f" + money(amount) + "§a.");
        creator.sendMessage("§7In attesa di un opponent. Usa §f/cf bedwars cancel §7per annullare.");

        long expireTicks = Math.max(20L, plugin.getConfig().getLong("bedwars.challenge-expire-seconds", 300L) * 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BedfightCoinflip stillWaiting = waitingByCreator.get(creator.getUniqueId());
            if (stillWaiting != match) return;
            waitingByCreator.remove(creator.getUniqueId());
            Player online = Bukkit.getPlayer(creator.getUniqueId());
            if (online != null) {
                restoreWaitingPlayer(online);
                online.sendMessage("§eLa queue BedWars è scaduta.");
            } else {
                waitingSnapshots.remove(creator.getUniqueId());
            }
        }, expireTicks);
    }

    public void listChallenges(Player player) {
        if (waitingByCreator.isEmpty()) {
            player.sendMessage("§eNon ci sono BedWars coinflip disponibili.");
            return;
        }
        player.sendMessage("§6§lBED FIGHT COINFLIP DISPONIBILI");
        for (BedfightCoinflip match : waitingByCreator.values()) {
            player.sendMessage("§e- §f" + match.getCreatorName() + " §7• §a" + money(match.getAmount())
                    + " §7• §f/cf bedwars accept " + match.getCreatorName());
        }
    }

    public void acceptChallenge(Player opponent, String creatorName) {
        if (activeRound != null) {
            opponent.sendMessage("§cL'arena è già occupata.");
            return;
        }
        BedfightCoinflip match = findWaitingByName(creatorName);
        if (match == null) {
            opponent.sendMessage("§cQueue non trovata per §f" + creatorName + "§c.");
            return;
        }
        if (match.getCreator().equals(opponent.getUniqueId())) {
            opponent.sendMessage("§cNon puoi accettare la tua stessa queue.");
            return;
        }
        if (isParticipant(opponent.getUniqueId())) {
            opponent.sendMessage("§cSei già dentro una queue o partita.");
            return;
        }

        Player creator = Bukkit.getPlayer(match.getCreator());
        if (creator == null || !creator.isOnline()) {
            waitingByCreator.remove(match.getCreator());
            waitingSnapshots.remove(match.getCreator());
            opponent.sendMessage("§cIl creator non è più online.");
            return;
        }
        if (!economy().has(creator, match.getAmount()) || !economy().has(opponent, match.getAmount())) {
            opponent.sendMessage("§cUno dei due giocatori non ha più abbastanza denaro.");
            creator.sendMessage("§cImpossibile avviare: saldo insufficiente di uno dei partecipanti.");
            return;
        }

        economy().withdrawPlayer(creator, match.getAmount());
        economy().withdrawPlayer(opponent, match.getAmount());
        waitingByCreator.remove(match.getCreator());
        awaitingCreateAmount.remove(match.getCreator());

        match.setOpponent(opponent.getUniqueId(), opponent.getName());
        match.setState(BedfightCoinflip.State.COUNTDOWN);
        ActiveRound round = new ActiveRound(match, getFirstPosition().getWorld());
        round.snapshots.put(creator.getUniqueId(), waitingSnapshots.remove(creator.getUniqueId()));
        round.snapshots.put(opponent.getUniqueId(), PlayerSnapshot.capture(opponent));
        activeRound = round;

        prepareFighter(creator, getFirstPosition());
        prepareFighter(opponent, getOpponentPosition());
        creator.sendMessage("§aOpponent trovato: §f" + opponent.getName());
        opponent.sendMessage("§aHai accettato il coinflip di §f" + creator.getName());
        startCountdown(round);
    }

    public void cancelWaiting(Player player) {
        BedfightCoinflip removed = waitingByCreator.remove(player.getUniqueId());
        awaitingCreateAmount.remove(player.getUniqueId());
        if (removed == null) {
            player.sendMessage("§eNon hai una queue BedWars attiva.");
            return;
        }
        restoreWaitingPlayer(player);
        player.sendMessage("§eQueue BedWars annullata.");
    }

    public void placeBet(Player bettor, String targetName, double amount) {
        ActiveRound round = activeRound;
        if (round == null || round.playing || round.finishing) {
            bettor.sendMessage("§cLe scommesse sono disponibili soltanto durante il countdown.");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !round.match.includes(target.getUniqueId())) {
            bettor.sendMessage("§cQuel giocatore non partecipa al round.");
            return;
        }
        if (round.match.includes(bettor.getUniqueId())) {
            bettor.sendMessage("§cI partecipanti non possono scommettere sul proprio round.");
            return;
        }
        if (amount <= 0 || !economy().has(bettor, amount)) {
            bettor.sendMessage("§cImporto non valido o saldo insufficiente.");
            return;
        }
        economy().withdrawPlayer(bettor, amount);
        round.bets.computeIfAbsent(target.getUniqueId(), ignored -> new HashMap<>())
                .merge(bettor.getUniqueId(), amount, Double::sum);
        bettor.sendMessage("§aHai scommesso §f" + money(amount) + " §asu §f" + target.getName() + "§a.");
    }

    public void startSpectating(Player player) {
        ActiveRound round = activeRound;
        if (round == null || !round.playing || round.finishing) {
            player.sendMessage("§cNon c'è una partita BedWars attiva da spectare.");
            return;
        }
        if (round.match.includes(player.getUniqueId())) {
            player.sendMessage("§cSei già un partecipante del round.");
            return;
        }
        round.spectators.putIfAbsent(player.getUniqueId(), PlayerSnapshot.capture(player));
        Location destination = midpoint(round.firstSpawn, round.opponentSpawn).add(0, 5, 0);

        // Teletrasporto prima della modalità: plugin di gestione mondi possono cambiare il gamemode.
        player.teleport(destination);

        // FIX 1: modalità spectator mantenuta ogni tick, quindi funziona anche senza OP.
        forceSpectatorMode(player, round);
        player.sendMessage("§aOra stai spectando il BedWars coinflip.");
    }

    private void forceSpectatorMode(Player player, ActiveRound expectedRound) {
        BukkitTask previousTask = expectedRound.spectatorTasks.remove(player.getUniqueId());
        cancel(previousTask);
        applySpectatorMode(player);
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()
                    || activeRound != expectedRound
                    || expectedRound.finishing
                    || !expectedRound.spectators.containsKey(player.getUniqueId())) {
                task[0].cancel();
                return;
            }
            if (player.getGameMode() != GameMode.SPECTATOR
                    || !player.getAllowFlight()
                    || !player.isFlying()) {
                applySpectatorMode(player);
            }
        }, 1L, 1L);
        expectedRound.spectatorTasks.put(player.getUniqueId(), task[0]);
    }

    private void applySpectatorMode(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    private void prepareQueuePlayer(Player player, Location spawn) {
        player.teleport(spawn.clone());
        player.setGameMode(GameMode.SURVIVAL);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.getInventory().clear();

        // FIX 3: vita immediata dopo il teletrasporto in queue + ricontrollo al tick seguente.
        restoreFullHealthAfterEntry(player);
    }

    private void prepareFighter(Player player, Location spawn) {
        player.teleport(spawn.clone());
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.getInventory().clear();
        giveKit(player);

        // FIX 3: vale sia per First sia per Opponent, dopo il cambio mondo/teletrasporto.
        restoreFullHealthAfterEntry(player);
    }

    private void restoreFullHealthAfterEntry(Player player) {
        restoreFullHealth(player);
        UUID playerId = player.getUniqueId();

        // Alcuni plugin applicano attributi o stato del mondo alcuni tick dopo il teleport.
        // Ripristina la vita per un secondo, poi si ferma automaticamente.
        final BukkitTask[] task = new BukkitTask[1];
        final int[] runs = {0};
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !isParticipant(playerId) || runs[0]++ >= 20) {
                task[0].cancel();
                return;
            }
            restoreFullHealth(player);
        }, 1L, 1L);
    }

    private void restoreFullHealth(Player player) {
        if (!player.isOnline() || player.isDead()) return;
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maximum = attribute == null ? 20.0 : attribute.getValue();
        player.setHealth(Math.max(1.0, maximum));
    }

    private void giveKit(Player player) {
        player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
        player.getInventory().addItem(new ItemStack(Material.WHITE_WOOL, 64));
        player.getInventory().addItem(new ItemStack(Material.OAK_PLANKS, 32));
        player.getInventory().addItem(new ItemStack(Material.END_STONE, 16));
        player.getInventory().addItem(new ItemStack(Material.WOODEN_PICKAXE));
        player.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
    }

    private void startCountdown(ActiveRound round) {
        int configured = Math.max(1, plugin.getConfig().getInt("bedwars.countdown", 5));
        final int[] seconds = {configured};
        round.countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeRound != round || round.finishing) {
                cancel(round.countdownTask);
                return;
            }
            Player first = Bukkit.getPlayer(round.match.getCreator());
            Player opponent = Bukkit.getPlayer(round.match.getOpponent());
            if (first == null || opponent == null) {
                finishRound(null, true, "§cPartita annullata: un partecipante è offline.");
                return;
            }
            if (seconds[0] <= 0) {
                cancel(round.countdownTask);
                beginPlaying(round);
                return;
            }
            first.sendTitle("§e" + seconds[0], "§7Preparati", 0, 18, 2);
            opponent.sendTitle("§e" + seconds[0], "§7Preparati", 0, 18, 2);
            first.playSound(first.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
            opponent.playSound(opponent.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
            seconds[0]--;
        }, 0L, 20L);
    }

    private void beginPlaying(ActiveRound round) {
        if (activeRound != round || round.finishing) return;
        round.playing = true;
        round.match.setState(BedfightCoinflip.State.ACTIVE);
        round.spawnProtectedUntil.put(round.match.getCreator(), protectionDeadline());
        round.spawnProtectedUntil.put(round.match.getOpponent(), protectionDeadline());
        for (UUID id : List.of(round.match.getCreator(), round.match.getOpponent())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                restoreFullHealthAfterEntry(player);
                player.sendTitle("§aVIA!", "§7Distruggi il letto avversario", 0, 30, 10);
            }
        }
    }

    public boolean canMoveDuringCountdown(Player player, Location from, Location to) {
        ActiveRound round = activeRound;
        if (round == null || round.playing || round.finishing || !round.match.includes(player.getUniqueId())) return true;
        if (from == null || to == null) return true;
        return sameCoordinates(from, to);
    }

    /**
     * FIX 2: chiamato dal PlayerMoveEvent dopo il controllo del countdown.
     * Una rotazione non conta; basta una variazione reale di X/Y/Z per rimuovere la protezione.
     */
    public void handleSpawnProtectionMovement(Player player, Location from, Location to) {
        ActiveRound round = activeRound;
        UUID id = player.getUniqueId();
        if (round == null || !round.playing || round.finishing || from == null || to == null
                || !round.match.includes(id) || round.respawning.contains(id) || !hasSpawnProtection(round, id)) {
            return;
        }
        boolean worldChanged = from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().getUID().equals(to.getWorld().getUID());
        boolean positionChanged = Double.compare(from.getX(), to.getX()) != 0
                || Double.compare(from.getY(), to.getY()) != 0
                || Double.compare(from.getZ(), to.getZ()) != 0;
        if (!worldChanged && !positionChanged) return;

        if (round.spawnProtectedUntil.remove(id) != null) {
            player.sendMessage("§cProtezione spawn disattivata: ti sei mosso.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f);
        }
    }

    public void recordLastDamager(Player victim, Player attacker) {
        if (!isActiveParticipant(victim.getUniqueId()) || !isActiveParticipant(attacker.getUniqueId())) return;
        lastHits.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    public boolean canDamage(Player attacker, Player victim) {
        ActiveRound round = activeRound;
        if (round == null || !round.playing || round.finishing) return false;
        if (!round.match.includes(attacker.getUniqueId()) || !round.match.includes(victim.getUniqueId())) return false;
        if (round.respawning.contains(attacker.getUniqueId()) || round.respawning.contains(victim.getUniqueId())) return false;
        if (hasSpawnProtection(round, victim.getUniqueId())) return false;
        round.spawnProtectedUntil.remove(attacker.getUniqueId());
        recordLastDamager(victim, attacker);
        return true;
    }

    public boolean canTakeDamage(Player player) {
        ActiveRound round = activeRound;
        if (round == null || !round.match.includes(player.getUniqueId())) return true;
        if (!round.playing || round.finishing || round.respawning.contains(player.getUniqueId())) return false;
        return !hasSpawnProtection(round, player.getUniqueId());
    }

    public boolean handlePotentialElimination(Player player, double finalDamage) {
        if (!isActiveParticipant(player.getUniqueId()) || !isPlaying()) return false;
        if (player.getHealth() - finalDamage > 0.0) return false;
        eliminateOrRespawn(player, "combattimento");
        return true;
    }

    public void handleDeath(Player player) {
        if (isActiveParticipant(player.getUniqueId())) eliminateOrRespawn(player, "morte");
    }

    public void handleVoidLevel(Player player) {
        if (!isActiveParticipant(player.getUniqueId()) || !isPlaying()) return;
        if (player.getLocation().getY() <= plugin.getConfig().getDouble("bedwars.void-y", 43.0)) {
            eliminateOrRespawn(player, "void");
        }
    }

    private void eliminateOrRespawn(Player player, String reason) {
        ActiveRound round = activeRound;
        if (round == null || round.finishing || round.respawning.contains(player.getUniqueId())) return;
        boolean bedAlive = isOwnBedAlive(round, player.getUniqueId());
        if (!bedAlive) {
            UUID winner = round.match.getOtherParticipant(player.getUniqueId());
            finishRound(winner, false, "§6" + player.getName() + " §eè stato eliminato (" + reason + ").");
            return;
        }

        round.respawning.add(player.getUniqueId());
        round.spawnProtectedUntil.remove(player.getUniqueId());
        player.setHealth(Math.max(1.0, maxHealth(player)));
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();
        player.sendTitle("§cSei morto", "§7Respawn in 3 secondi", 0, 40, 10);
        long delay = Math.max(1L, plugin.getConfig().getLong("bedwars.respawn-delay-ticks", 60L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> respawnParticipant(player.getUniqueId(), round), delay);
    }

    private void respawnParticipant(UUID playerId, ActiveRound expectedRound) {
        if (activeRound != expectedRound || expectedRound.finishing) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            UUID winner = expectedRound.match.getOtherParticipant(playerId);
            finishRound(winner, false, "§cUn partecipante si è disconnesso.");
            return;
        }
        expectedRound.respawning.remove(playerId);
        Location location = getRespawnLocation(playerId);
        prepareFighter(player, location);
        expectedRound.spawnProtectedUntil.put(playerId, protectionDeadline());
        player.sendMessage("§aRespawn effettuato. §eLa protezione sparisce appena ti muovi o attacchi.");
    }

    public Location getRespawnLocation(UUID playerId) {
        ActiveRound round = activeRound;
        if (round == null) return null;
        return round.match.getCreator().equals(playerId) ? round.firstSpawn.clone() : round.opponentSpawn.clone();
    }

    public void handleRespawn(Player player) {
        ActiveRound round = activeRound;
        if (round == null || !round.match.includes(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Location location = getRespawnLocation(player.getUniqueId());
            if (location != null) player.teleport(location);
        });
    }

    public void handleChangedWorld(Player player) {
        ActiveRound round = activeRound;
        if (round == null) return;
        UUID id = player.getUniqueId();
        if (round.match.includes(id) && !isRoundWorld(player.getWorld())) {
            Location respawn = getRespawnLocation(id);
            if (respawn != null) Bukkit.getScheduler().runTask(plugin, () -> player.teleport(respawn));
            return;
        }
        if (round.spectators.containsKey(id) && !isRoundWorld(player.getWorld())) {
            stopSpectating(player, true);
        }
    }

    public void handleJoin(Player player) {
        UUID id = player.getUniqueId();
        ActiveRound round = activeRound;
        if (round != null && round.spectators.containsKey(id)) {
            Bukkit.getScheduler().runTask(plugin, () -> stopSpectating(player, true));
        }
    }

    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        awaitingCreateAmount.remove(id);
        BedfightCoinflip waiting = waitingByCreator.remove(id);
        if (waiting != null) {
            waitingSnapshots.remove(id);
            return;
        }
        ActiveRound round = activeRound;
        if (round == null) return;
        if (round.spectators.containsKey(id)) {
            BukkitTask task = round.spectatorTasks.remove(id);
            cancel(task);
            round.spectators.remove(id);
            return;
        }
        if (round.match.includes(id) && !round.finishing) {
            finishRound(round.match.getOtherParticipant(id), false, "§c" + player.getName() + " si è disconnesso.");
        }
    }

    public boolean canLeaveArena(Player player, Location to) {
        ActiveRound round = activeRound;
        if (round == null || !round.match.includes(player.getUniqueId()) || round.finishing) return true;
        return to != null && to.getWorld() != null && round.world != null
                && to.getWorld().getUID().equals(round.world.getUID());
    }

    public void handleBlockPlace(Player player, Block block) {
        ActiveRound round = activeRound;
        if (round == null || !round.playing || !round.match.includes(player.getUniqueId())) return;
        BlockKey key = BlockKey.of(block);
        round.originalBlocks.putIfAbsent(key, Material.AIR.createBlockData());
    }

    public BreakResult handleBlockBreak(Player player, Block block) {
        ActiveRound round = activeRound;
        if (round == null || !round.playing || !round.match.includes(player.getUniqueId())) return BreakResult.DENY;
        Set<BlockKey> ownBed = ownBedKeys(round, player.getUniqueId());
        Set<BlockKey> enemyBed = enemyBedKeys(round, player.getUniqueId());
        BlockKey key = BlockKey.of(block);
        if (ownBed.contains(key)) return BreakResult.OWN_BED;
        if (enemyBed.contains(key)) {
            if (round.match.getCreator().equals(player.getUniqueId())) round.match.setOpponentBedAlive(false);
            else round.match.setFirstBedAlive(false);
            for (BlockKey bedKey : enemyBed) {
                Block bedBlock = bedKey.getBlock();
                if (bedBlock != null) bedBlock.setType(Material.AIR, false);
            }
            broadcastRound(round, "§cIl letto di §f" + round.match.getName(round.match.getOtherParticipant(player.getUniqueId()))
                    + " §cè stato distrutto da §f" + player.getName() + "§c!");
            return BreakResult.ENEMY_BED;
        }
        if (!isBreakablePlacedMaterial(block.getType()) && !round.originalBlocks.containsKey(key)) return BreakResult.DENY;
        round.originalBlocks.putIfAbsent(key, block.getBlockData().clone());
        return BreakResult.ALLOW;
    }

    public boolean isUndroppableKitItem(ItemStack item) {
        if (item == null) return false;
        return switch (item.getType()) {
            case WOODEN_SWORD, WOODEN_PICKAXE, WOODEN_AXE -> true;
            default -> false;
        };
    }

    public void showStatus(CommandSender sender) {
        sender.sendMessage("§6SunnyCoinflip BedWars status:");
        sender.sendMessage("§7- enabled: §f" + isEnabled());
        sender.sendMessage("§7- configured: §f" + isArenaConfigured());
        sender.sendMessage("§7- waiting: §f" + waitingByCreator.size());
        sender.sendMessage("§7- active: §f" + (activeRound != null));
        sender.sendMessage("§7- playing: §f" + isPlaying());
    }

    public void abortByAdmin(CommandSender sender) {
        if (activeRound != null) {
            finishRound(null, true, "§cRound interrotto da un amministratore.");
            sender.sendMessage("§aRound BedWars interrotto e importi rimborsati.");
            return;
        }
        for (UUID id : new ArrayList<>(waitingByCreator.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) restoreWaitingPlayer(player);
        }
        waitingByCreator.clear();
        waitingSnapshots.clear();
        sender.sendMessage("§aQueue BedWars svuotate.");
    }

    public void setFirstPosition(Player player) {
        saveLocation("bedwars.first-position", player.getLocation());
        player.sendMessage("§aPosizione First BedWars impostata.");
    }

    public void setOpponentPosition(Player player) {
        saveLocation("bedwars.opponent-position", player.getLocation());
        player.sendMessage("§aPosizione Opponent BedWars impostata.");
    }

    public void setFirstBed(Player player) {
        Block block = targetBed(player);
        if (block == null) {
            player.sendMessage("§cGuarda un letto entro 8 blocchi.");
            return;
        }
        saveLocation("bedwars.first-bed", block.getLocation());
        player.sendMessage("§aLetto First BedWars impostato.");
    }

    public void setOpponentBed(Player player) {
        Block block = targetBed(player);
        if (block == null) {
            player.sendMessage("§cGuarda un letto entro 8 blocchi.");
            return;
        }
        saveLocation("bedwars.opponent-bed", block.getLocation());
        player.sendMessage("§aLetto Opponent BedWars impostato.");
    }

    public Location getFirstPosition() {
        return plugin.getConfig().getLocation("bedwars.first-position");
    }

    public Location getOpponentPosition() {
        return plugin.getConfig().getLocation("bedwars.opponent-position");
    }

    public Location getFirstBedLocation() {
        return plugin.getConfig().getLocation("bedwars.first-bed");
    }

    public Location getOpponentBedLocation() {
        return plugin.getConfig().getLocation("bedwars.opponent-bed");
    }

    public void handleShutdown() {
        if (activeRound != null) finishRound(null, true, null);
        for (Map.Entry<UUID, PlayerSnapshot> entry : new HashMap<>(waitingSnapshots).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) entry.getValue().restore(player);
        }
        waitingSnapshots.clear();
        waitingByCreator.clear();
        awaitingCreateAmount.clear();
    }

    private void finishRound(UUID winnerId, boolean refund, String reason) {
        ActiveRound round = activeRound;
        if (round == null || round.finishing) return;
        round.finishing = true;
        round.match.setState(BedfightCoinflip.State.FINISHED);
        cancel(round.countdownTask);
        for (BukkitTask task : round.spectatorTasks.values()) cancel(task);

        if (reason != null) broadcastRound(round, reason);
        if (refund || winnerId == null) {
            economy().depositPlayer(Bukkit.getOfflinePlayer(round.match.getCreator()), round.match.getAmount());
            economy().depositPlayer(Bukkit.getOfflinePlayer(round.match.getOpponent()), round.match.getAmount());
            refundBets(round);
        } else {
            double prize = round.match.getAmount() * 2.0;
            economy().depositPlayer(Bukkit.getOfflinePlayer(winnerId), prize);
            Player winner = Bukkit.getPlayer(winnerId);
            if (winner != null) winner.sendMessage("§6Hai vinto §f" + money(prize) + "§6!");
            payBets(round, winnerId);
        }

        restoreArena(round);

        // Disattiva il round prima dei teletrasporti di ripristino: gli eventi di cambio mondo
        // non devono riportare i giocatori dentro l'arena appena terminata.
        activeRound = null;

        for (Map.Entry<UUID, PlayerSnapshot> entry : round.snapshots.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline() && entry.getValue() != null) entry.getValue().restore(player);
        }
        for (Map.Entry<UUID, PlayerSnapshot> entry : round.spectators.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) entry.getValue().restore(player);
        }
        lastHits.clear();
    }

    private void stopSpectating(Player player, boolean restore) {
        ActiveRound round = activeRound;
        if (round == null) return;
        BukkitTask task = round.spectatorTasks.remove(player.getUniqueId());
        cancel(task);
        PlayerSnapshot snapshot = round.spectators.remove(player.getUniqueId());
        if (restore && snapshot != null) snapshot.restore(player);
    }

    private void restoreWaitingPlayer(Player player) {
        PlayerSnapshot snapshot = waitingSnapshots.remove(player.getUniqueId());
        if (snapshot != null) snapshot.restore(player);
    }

    private void refundBets(ActiveRound round) {
        for (Map<UUID, Double> side : round.bets.values()) {
            for (Map.Entry<UUID, Double> bet : side.entrySet()) {
                economy().depositPlayer(Bukkit.getOfflinePlayer(bet.getKey()), bet.getValue());
            }
        }
    }

    private void payBets(ActiveRound round, UUID winnerId) {
        Map<UUID, Double> winningBets = round.bets.getOrDefault(winnerId, Map.of());
        Map<UUID, Double> losingBets = round.bets.getOrDefault(round.match.getOtherParticipant(winnerId), Map.of());
        double winningPool = winningBets.values().stream().mapToDouble(Double::doubleValue).sum();
        double losingPool = losingBets.values().stream().mapToDouble(Double::doubleValue).sum();
        if (winningPool <= 0.0) return;
        for (Map.Entry<UUID, Double> bet : winningBets.entrySet()) {
            double payout = bet.getValue() + losingPool * (bet.getValue() / winningPool);
            economy().depositPlayer(Bukkit.getOfflinePlayer(bet.getKey()), payout);
        }
    }

    private void restoreArena(ActiveRound round) {
        for (Map.Entry<BlockKey, BlockData> entry : round.originalBlocks.entrySet()) {
            Block block = entry.getKey().getBlock();
            if (block != null) block.setBlockData(entry.getValue(), false);
        }
        restoreBed(getFirstBedLocation(), round.firstBedData);
        restoreBed(getOpponentBedLocation(), round.opponentBedData);
    }

    private void restoreBed(Location location, Map<BlockKey, BlockData> saved) {
        if (location == null) return;
        for (Map.Entry<BlockKey, BlockData> entry : saved.entrySet()) {
            Block block = entry.getKey().getBlock();
            if (block != null) block.setBlockData(entry.getValue(), false);
        }
    }

    private Set<BlockKey> ownBedKeys(ActiveRound round, UUID playerId) {
        return round.match.getCreator().equals(playerId) ? round.firstBed : round.opponentBed;
    }

    private Set<BlockKey> enemyBedKeys(ActiveRound round, UUID playerId) {
        return round.match.getCreator().equals(playerId) ? round.opponentBed : round.firstBed;
    }

    private boolean isOwnBedAlive(ActiveRound round, UUID playerId) {
        return round.match.getCreator().equals(playerId) ? round.match.isFirstBedAlive() : round.match.isOpponentBedAlive();
    }

    private boolean hasSpawnProtection(ActiveRound round, UUID playerId) {
        Long deadline = round.spawnProtectedUntil.get(playerId);
        if (deadline == null) return false;
        if (deadline <= System.currentTimeMillis()) {
            round.spawnProtectedUntil.remove(playerId);
            return false;
        }
        return true;
    }

    private long protectionDeadline() {
        long ticks = Math.max(0L, plugin.getConfig().getLong("bedwars.spawn-protection-ticks", 60L));
        return System.currentTimeMillis() + ticks * 50L;
    }

    private BedfightCoinflip findWaitingByName(String creatorName) {
        for (BedfightCoinflip match : waitingByCreator.values()) {
            if (match.getCreatorName().equalsIgnoreCase(creatorName)) return match;
        }
        return null;
    }

    private Economy economy() {
        return SunnyCoinflip.getEconomy();
    }

    private void saveLocation(String path, Location location) {
        plugin.getConfig().set(path, location.clone());
        plugin.saveConfig();
    }

    private Block targetBed(Player player) {
        Block block = player.getTargetBlockExact(8);
        if (block == null || !block.getType().name().endsWith("_BED")) return null;
        return block;
    }

    private boolean isBreakablePlacedMaterial(Material material) {
        String name = material.name();
        return material == Material.END_STONE || name.endsWith("_WOOL") || name.endsWith("_PLANKS")
                || name.endsWith("_LOG") || name.endsWith("_WOOD");
    }

    private void broadcastRound(ActiveRound round, String message) {
        Set<UUID> recipients = new HashSet<>();
        recipients.add(round.match.getCreator());
        recipients.add(round.match.getOpponent());
        recipients.addAll(round.spectators.keySet());
        for (UUID id : recipients) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(message);
        }
    }

    private String money(double amount) {
        return String.format(Locale.US, "%,.0f", amount);
    }

    private double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private boolean sameWorld(Location location, World world) {
        return location != null && location.getWorld() != null && location.getWorld().getUID().equals(world.getUID());
    }

    private boolean sameCoordinates(Location first, Location second) {
        if (first.getWorld() == null || second.getWorld() == null) return false;
        return first.getWorld().getUID().equals(second.getWorld().getUID())
                && Double.compare(first.getX(), second.getX()) == 0
                && Double.compare(first.getY(), second.getY()) == 0
                && Double.compare(first.getZ(), second.getZ()) == 0;
    }

    private Location midpoint(Location first, Location second) {
        if (first == null) return second == null ? new Location(Bukkit.getWorlds().getFirst(), 0, 100, 0) : second.clone();
        if (second == null || first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().getUID().equals(second.getWorld().getUID())) return first.clone();
        return new Location(first.getWorld(), (first.getX() + second.getX()) / 2.0,
                (first.getY() + second.getY()) / 2.0, (first.getZ() + second.getZ()) / 2.0);
    }

    private static void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private final class ActiveRound {
        private final BedfightCoinflip match;
        private final World world;
        private final Location firstSpawn;
        private final Location opponentSpawn;
        private final Set<BlockKey> firstBed;
        private final Set<BlockKey> opponentBed;
        private final Map<BlockKey, BlockData> firstBedData = new HashMap<>();
        private final Map<BlockKey, BlockData> opponentBedData = new HashMap<>();
        private final Map<BlockKey, BlockData> originalBlocks = new LinkedHashMap<>();
        private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
        private final Map<UUID, PlayerSnapshot> spectators = new HashMap<>();
        private final Map<UUID, BukkitTask> spectatorTasks = new HashMap<>();
        private final Map<UUID, Long> spawnProtectedUntil = new HashMap<>();
        private final Set<UUID> respawning = new HashSet<>();
        private final Map<UUID, Map<UUID, Double>> bets = new HashMap<>();
        private BukkitTask countdownTask;
        private boolean playing;
        private boolean finishing;

        private ActiveRound(BedfightCoinflip match, World world) {
            this.match = match;
            this.world = world;
            this.firstSpawn = getFirstPosition().clone();
            this.opponentSpawn = getOpponentPosition().clone();
            this.firstBed = resolveBed(getFirstBedLocation());
            this.opponentBed = resolveBed(getOpponentBedLocation());
            saveBedData(firstBed, firstBedData);
            saveBedData(opponentBed, opponentBedData);
        }
    }

    private void saveBedData(Set<BlockKey> keys, Map<BlockKey, BlockData> destination) {
        for (BlockKey key : keys) {
            Block block = key.getBlock();
            if (block != null) destination.put(key, block.getBlockData().clone());
        }
    }

    private Set<BlockKey> resolveBed(Location location) {
        if (location == null || location.getWorld() == null) return Set.of();
        Block block = location.getBlock();
        if (!(block.getBlockData() instanceof Bed bedData)) return Set.of(BlockKey.of(block));
        Set<BlockKey> result = new HashSet<>();
        result.add(BlockKey.of(block));
        BlockFace direction = bedData.getFacing();
        Block other = bedData.getPart() == Bed.Part.HEAD ? block.getRelative(direction.getOppositeFace()) : block.getRelative(direction);
        if (other.getBlockData() instanceof Bed) result.add(BlockKey.of(other));
        return result;
    }

    private record LastHit(UUID attacker, long at) {}

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        private Block getBlock() {
            World world = Bukkit.getWorld(worldId);
            return world == null ? null : world.getBlockAt(x, y, z);
        }
    }

    private static final class PlayerSnapshot {
        private final Location location;
        private final ItemStack[] storage;
        private final ItemStack[] armor;
        private final ItemStack offHand;
        private final GameMode gameMode;
        private final double health;
        private final int food;
        private final float saturation;
        private final boolean allowFlight;
        private final boolean flying;
        private final int level;
        private final float experience;

        private PlayerSnapshot(Player player) {
            location = player.getLocation().clone();
            storage = cloneItems(player.getInventory().getStorageContents());
            armor = cloneItems(player.getInventory().getArmorContents());
            offHand = player.getInventory().getItemInOffHand().clone();
            gameMode = player.getGameMode();
            health = player.getHealth();
            food = player.getFoodLevel();
            saturation = player.getSaturation();
            allowFlight = player.getAllowFlight();
            flying = player.isFlying();
            level = player.getLevel();
            experience = player.getExp();
        }

        private static PlayerSnapshot capture(Player player) {
            return new PlayerSnapshot(player);
        }

        private void restore(Player player) {
            player.setSpectatorTarget(null);
            if (location.getWorld() != null) player.teleport(location);
            player.setGameMode(gameMode);
            player.getInventory().setStorageContents(cloneItems(storage));
            player.getInventory().setArmorContents(cloneItems(armor));
            player.getInventory().setItemInOffHand(offHand.clone());
            player.setFoodLevel(food);
            player.setSaturation(saturation);
            player.setFireTicks(0);
            player.setLevel(level);
            player.setExp(experience);
            player.setAllowFlight(allowFlight || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
            if (player.getAllowFlight()) player.setFlying(flying);
            AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maximum = attribute == null ? 20.0 : attribute.getValue();
            player.setHealth(Math.max(1.0, Math.min(health, maximum)));
        }

        private static ItemStack[] cloneItems(ItemStack[] source) {
            ItemStack[] result = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].clone();
            return result;
        }
    }
}
