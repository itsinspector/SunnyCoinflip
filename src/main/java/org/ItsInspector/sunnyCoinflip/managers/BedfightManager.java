package org.ItsInspector.sunnyCoinflip.managers;

import net.milkbowl.vault.economy.EconomyResponse;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.BedfightCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Internal 1v1 BedWars implementation used by the coinflip system.
 *
 * The legacy BedfightManager name is intentionally retained for drop-in
 * compatibility with the first overlay. No external BedFight plugin is used.
 */
public final class BedfightManager {

    private static final String PREFIX = "§8[§bBedWars§8] §r";

    private final SunnyCoinflip plugin;
    private final Map<UUID, BedfightCoinflip> waitingByCreator = new LinkedHashMap<>();
    private final Map<UUID, PlayerSnapshot> waitingSnapshots = new HashMap<>();
    private final Set<UUID> awaitingCreateAmount = new HashSet<>();
    private final Map<UUID, PlayerSnapshot> pendingRestores = new HashMap<>();
    /** Prevents PlayerChangedWorldEvent from re-applying the BedWars kit while a snapshot is being restored. */
    private final Set<UUID> restoringPlayers = new HashSet<>();
    private final Map<UUID, LastHit> lastHits = new HashMap<>();

    private volatile ActiveRound activeRound;

    public BedfightManager(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("bedwars.enabled", true);
    }

    public boolean isParticipant(UUID playerId) {
        return waitingByCreator.containsKey(playerId) || isActiveParticipant(playerId);
    }

    public boolean isActiveParticipant(UUID playerId) {
        return activeRound != null && activeRound.match.includes(playerId);
    }

    public BedfightCoinflip getActiveMatch(UUID playerId) {
        return isActiveParticipant(playerId) ? activeRound.match : null;
    }

    public boolean isPlaying() {
        ActiveRound round = activeRound;
        return round != null && round.playing && !round.finishing;
    }

    /**
     * Returns whether the BedWars arena can currently accept a match.
     * A waiting challenge does not occupy the arena; countdown and active rounds do.
     */
    public boolean isAvailable() {
        return isEnabled() && activeRound == null && isArenaConfigured();
    }

    /**
     * Checks the saved arena configuration without scanning world blocks. This
     * method is intentionally lightweight because scoreboard plugins may parse
     * placeholders very frequently or from an asynchronous task.
     */
    public boolean isArenaConfigured() {
        Location first = getFirstPosition();
        Location opponent = getOpponentPosition();
        Location firstBed = getFirstBedPosition();
        Location opponentBed = getOpponentBedPosition();

        if (first == null || opponent == null || firstBed == null || opponentBed == null
                || first.getWorld() == null || opponent.getWorld() == null
                || firstBed.getWorld() == null || opponentBed.getWorld() == null) {
            return false;
        }

        UUID worldId = first.getWorld().getUID();
        if (!worldId.equals(opponent.getWorld().getUID())
                || !worldId.equals(firstBed.getWorld().getUID())
                || !worldId.equals(opponentBed.getWorld().getUID())) {
            return false;
        }

        return firstBed.getBlockX() != opponentBed.getBlockX()
                || firstBed.getBlockY() != opponentBed.getBlockY()
                || firstBed.getBlockZ() != opponentBed.getBlockZ();
    }

    public boolean isArenaWorld(World world) {
        if (world == null) {
            return false;
        }
        Location first = getFirstPosition();
        Location opponent = getOpponentPosition();
        return isSameWorld(first, world) || isSameWorld(opponent, world);
    }


    /** Context-sensitive /cf bedwars shortcut. */
    public void handleSimpleCommand(Player player) {
        purgeExpiredChallenges();
        if (!isEnabled()) {
            player.sendMessage(PREFIX + "§cLa modalità è disabilitata.");
            return;
        }
        ActiveRound round = activeRound;
        if (round != null) {
            if (round.match.includes(player.getUniqueId())) {
                player.sendMessage(PREFIX + "§eSei già dentro questa partita.");
                return;
            }
            startSpectating(player);
            return;
        }
        BedfightCoinflip waiting = waitingByCreator.values().stream().findFirst().orElse(null);
        if (waiting != null) {
            if (waiting.getCreator().equals(player.getUniqueId())) {
                player.sendMessage(PREFIX + "§eStai già aspettando un opponent.");
                return;
            }
            acceptChallenge(player, waiting.getCreatorName());
            return;
        }
        if (!isArenaReady(player)) {
            return;
        }
        awaitingCreateAmount.add(player.getUniqueId());
        player.sendMessage(PREFIX + "§eScrivi in chat la somma da scommettere, oppure §ccancel §eper annullare.");
    }

    public boolean isAwaitingCreateAmount(UUID playerId) {
        return awaitingCreateAmount.contains(playerId);
    }

    /** Called synchronously by the chat listener. */
    public void handleCreateAmountChat(Player player, String message) {
        if (!awaitingCreateAmount.remove(player.getUniqueId())) {
            return;
        }
        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("annulla")) {
            player.sendMessage(PREFIX + "§7Creazione annullata.");
            return;
        }
        try {
            double amount = org.ItsInspector.sunnyCoinflip.utils.NumberParser.parseNumber(message);
            createChallenge(player, amount);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(PREFIX + "§c" + exception.getMessage());
            player.sendMessage(PREFIX + "§7Usa di nuovo §e/cf bedwars §7per riprovare.");
        }
    }

    public void startSpectating(Player player) {
        ActiveRound round = activeRound;
        if (round == null || round.finishing) {
            player.sendMessage(PREFIX + "§cNon c'è una partita da spectare.");
            return;
        }
        if (round.match.includes(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§cSei un partecipante della partita.");
            return;
        }
        if (round.spectators.containsKey(player.getUniqueId())) {
            player.teleport(getSpectatorLocation(round));
            return;
        }
        round.spectators.put(player.getUniqueId(), PlayerSnapshot.capture(player));
        player.closeInventory();

        // Teleport first: world-management plugins may enforce the destination world's
        // default gamemode during PlayerChangedWorldEvent, especially for non-OP players.
        player.teleport(getSpectatorLocation(round));
        forceSpectatorMode(player, round);

        player.sendMessage(PREFIX + "§aStai spectando §f" + round.match.getCreatorName()
                + " §7vs §f" + round.match.getOpponentName() + "§a.");
    }

    /**
     * Forces vanilla spectator mode after a world teleport. Some world-management
     * plugins apply the destination world's default gamemode one or more ticks after
     * PlayerChangedWorldEvent; setting spectator before teleport therefore only works
     * reliably for players who bypass those rules (often OPs).
     */
    private void forceSpectatorMode(Player player, ActiveRound expectedRound) {
        applySpectatorMode(player);

        long[] retryDelays = {1L, 3L, 10L};
        for (long delay : retryDelays) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || activeRound != expectedRound || expectedRound.finishing) {
                    return;
                }
                boolean isMatchPlayer = expectedRound.match.includes(player.getUniqueId());
                boolean shouldSpectate = expectedRound.spectators.containsKey(player.getUniqueId())
                        || expectedRound.respawning.contains(player.getUniqueId());
                if (!isMatchPlayer && !expectedRound.spectators.containsKey(player.getUniqueId())) {
                    return;
                }
                if (shouldSpectate && player.getGameMode() != GameMode.SPECTATOR) {
                    applySpectatorMode(player);
                }
            }, delay);
        }
    }

    /**
     * Keeps an eliminated participant in vanilla spectator mode for the entire
     * respawn delay. This does not depend on permissions or OP status. Reapplying
     * the mode every tick also wins against world-management plugins that restore
     * SURVIVAL shortly after a teleport or gamemode change.
     */
    private void forceRespawnSpectatorMode(Player player, ActiveRound expectedRound) {
        applySpectatorMode(player);

        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()
                    || activeRound != expectedRound
                    || expectedRound.finishing
                    || !expectedRound.respawning.contains(player.getUniqueId())) {
                task[0].cancel();
                return;
            }

            if (player.getGameMode() != GameMode.SPECTATOR
                    || !player.getAllowFlight()
                    || !player.isFlying()) {
                applySpectatorMode(player);
            }
        }, 1L, 1L);
    }

    private void applySpectatorMode(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0.0f);
    }

    private Location getSpectatorLocation(ActiveRound round) {
        Location location = round.firstSpawn.clone().add(round.opponentSpawn).multiply(0.5);
        location.setWorld(round.world);
        location.setY(Math.max(round.firstSpawn.getY(), round.opponentSpawn.getY()) + 8.0);
        return location;
    }

    public Collection<BedfightCoinflip> getWaitingChallenges() {
        purgeExpiredChallenges();
        return new ArrayList<>(waitingByCreator.values());
    }

    public void createChallenge(Player creator, double amount) {
        purgeExpiredChallenges();

        if (!isEnabled()) {
            creator.sendMessage(PREFIX + "§cLa modalità è disabilitata.");
            return;
        }
        if (!isArenaReady(creator)) {
            return;
        }
        if (amount <= 0 || amount > plugin.getGameManager().getMaxAmount()) {
            creator.sendMessage(PREFIX + "§cL'importo deve essere tra 1 e "
                    + String.format(Locale.US, "%.0f", plugin.getGameManager().getMaxAmount()) + ".");
            return;
        }
        if (isParticipant(creator.getUniqueId())) {
            creator.sendMessage(PREFIX + "§cHai già una sfida BedWars in attesa o in corso.");
            return;
        }
        if (isBusyInOtherMode(creator.getUniqueId())) {
            creator.sendMessage(PREFIX + "§cSei già impegnato in un altro coinflip.");
            return;
        }
        if (SunnyCoinflip.getEconomy().getBalance(creator) < amount) {
            creator.sendMessage(PREFIX + "§cNon hai abbastanza soldi.");
            return;
        }

        BedfightCoinflip challenge = new BedfightCoinflip(
                creator.getUniqueId(), creator.getName(), amount
        );
        waitingByCreator.put(creator.getUniqueId(), challenge);
        waitingSnapshots.put(creator.getUniqueId(), PlayerSnapshot.capture(creator));
        Location waitingSpawn = getFirstPosition();
        creator.closeInventory();
        creator.setGameMode(GameMode.SURVIVAL);
        creator.setAllowFlight(false);
        creator.setFlying(false);
        creator.setInvulnerable(true);
        clearPotionEffects(creator);
        resetCombatState(creator);
        giveKit(creator, Team.FIRST);
        if (waitingSpawn != null) {
            creator.teleport(waitingSpawn);
        }

        creator.sendMessage(PREFIX + "§aCoinflip creato per §f\uE0D8 §e" + formatMoney(amount) + "§a.");
        Bukkit.broadcastMessage("§b§lBEDWARS CF! §f" + creator.getName()
                + " §7ha creato una sfida da §f\uE0D8 §e" + formatMoney(amount)
                + "§7. §e/cf bedwars accept " + creator.getName());
    }

    public void listChallenges(CommandSender viewer) {
        purgeExpiredChallenges();
        if (waitingByCreator.isEmpty()) {
            viewer.sendMessage(PREFIX + "§7Non ci sono sfide in attesa.");
            return;
        }

        viewer.sendMessage("§b§lCoinflip BedWars in attesa:");
        for (BedfightCoinflip challenge : waitingByCreator.values()) {
            viewer.sendMessage("§8- §f" + challenge.getCreatorName()
                    + " §7• §f\uE0D8 §e" + formatMoney(challenge.getAmount())
                    + " §7• §e/cf bedwars accept " + challenge.getCreatorName());
        }
    }

    public void acceptChallenge(Player opponent, String creatorName) {
        purgeExpiredChallenges();

        if (!isEnabled()) {
            opponent.sendMessage(PREFIX + "§cLa modalità è disabilitata.");
            return;
        }
        if (activeRound != null) {
            opponent.sendMessage(PREFIX + "§cL'arena è già occupata.");
            return;
        }
        if (!isArenaReady(opponent)) {
            return;
        }

        BedfightCoinflip challenge = findWaitingByName(creatorName);
        if (challenge == null) {
            opponent.sendMessage(PREFIX + "§cNessuna sfida trovata per " + creatorName + ".");
            return;
        }
        if (challenge.getCreator().equals(opponent.getUniqueId())) {
            opponent.sendMessage(PREFIX + "§cNon puoi accettare la tua sfida.");
            return;
        }
        if (isParticipant(opponent.getUniqueId())) {
            opponent.sendMessage(PREFIX + "§cHai già una sfida in attesa o in corso.");
            return;
        }

        Player creator = Bukkit.getPlayer(challenge.getCreator());
        if (creator == null || !creator.isOnline()) {
            waitingByCreator.remove(challenge.getCreator());
            waitingSnapshots.remove(challenge.getCreator());
            opponent.sendMessage(PREFIX + "§cIl creatore non è più online.");
            return;
        }
        if (isBusyInOtherMode(creator.getUniqueId()) || isBusyInOtherMode(opponent.getUniqueId())) {
            opponent.sendMessage(PREFIX + "§cUno dei due giocatori è già impegnato in un altro coinflip.");
            return;
        }

        double amount = challenge.getAmount();
        if (SunnyCoinflip.getEconomy().getBalance(creator) < amount) {
            waitingByCreator.remove(challenge.getCreator());
            restoreWaitingCreator(challenge.getCreator());
            creator.sendMessage(PREFIX + "§cSfida rimossa: saldo insufficiente.");
            opponent.sendMessage(PREFIX + "§cIl creatore non ha più abbastanza soldi.");
            return;
        }
        if (SunnyCoinflip.getEconomy().getBalance(opponent) < amount) {
            opponent.sendMessage(PREFIX + "§cNon hai abbastanza soldi.");
            return;
        }

        Location firstSpawn = getFirstPosition();
        Location opponentSpawn = getOpponentPosition();
        World world = firstSpawn == null ? null : firstSpawn.getWorld();
        if (world == null || opponentSpawn == null || opponentSpawn.getWorld() == null
                || !world.getUID().equals(opponentSpawn.getWorld().getUID())) {
            opponent.sendMessage(PREFIX + "§cLe due posizioni dell'arena non sono valide o non sono nello stesso mondo.");
            return;
        }

        Set<BlockKey> firstBed = resolveConfiguredOrNearbyBed(
                getFirstBedPosition(), firstSpawn, getBedSearchRadius()
        );
        Set<BlockKey> opponentBed = resolveConfiguredOrNearbyBed(
                getOpponentBedPosition(), opponentSpawn, getBedSearchRadius()
        );
        if (firstBed.isEmpty() || opponentBed.isEmpty()) {
            opponent.sendMessage(PREFIX + "§cImpossibile trovare entrambi i letti. Usa setfirstbed e setopponentbed.");
            creator.sendMessage(PREFIX + "§cConfigurazione letti incompleta. Avvisa un amministratore.");
            return;
        }
        Set<BlockKey> overlappingBedBlocks = new HashSet<>(firstBed);
        overlappingBedBlocks.retainAll(opponentBed);
        if (!overlappingBedBlocks.isEmpty()) {
            opponent.sendMessage(PREFIX + "§cI letti First e Opponent coincidono. Riconfigurali separatamente.");
            creator.sendMessage(PREFIX + "§cConfigurazione letti non valida: i due letti coincidono.");
            return;
        }

        challenge.setOpponent(opponent.getUniqueId(), opponent.getName());
        challenge.setState(BedfightCoinflip.State.COUNTDOWN);

        EconomyResponse creatorWithdraw = SunnyCoinflip.getEconomy().withdrawPlayer(creator, amount);
        if (!creatorWithdraw.transactionSuccess()) {
            resetWaitingChallenge(challenge);
            opponent.sendMessage(PREFIX + "§cImpossibile prelevare la puntata del creatore.");
            return;
        }
        EconomyResponse opponentWithdraw = SunnyCoinflip.getEconomy().withdrawPlayer(opponent, amount);
        if (!opponentWithdraw.transactionSuccess()) {
            SunnyCoinflip.getEconomy().depositPlayer(creator, amount);
            resetWaitingChallenge(challenge);
            opponent.sendMessage(PREFIX + "§cImpossibile prelevare la tua puntata.");
            return;
        }

        waitingByCreator.remove(challenge.getCreator());
        ActiveRound round = new ActiveRound(challenge, world, firstSpawn.clone(), opponentSpawn.clone(), firstBed, opponentBed);
        PlayerSnapshot creatorSnapshot = waitingSnapshots.remove(creator.getUniqueId());
        round.snapshots.put(creator.getUniqueId(), creatorSnapshot != null
                ? creatorSnapshot : PlayerSnapshot.capture(creator));
        round.snapshots.put(opponent.getUniqueId(), PlayerSnapshot.capture(opponent));
        for (Entity entity : world.getEntities()) {
            round.initialEntities.add(entity.getUniqueId());
        }
        captureBedBlocks(round, firstBed);
        captureBedBlocks(round, opponentBed);
        activeRound = round;

        if (!prepareFighter(creator, firstSpawn, Team.FIRST)
                || !prepareFighter(opponent, opponentSpawn, Team.OPPONENT)) {
            abortActiveRound("§cAvvio fallito: puntate rimborsate.", true);
            return;
        }

        creator.sendMessage(PREFIX + "§a" + opponent.getName() + " ha accettato la tua sfida.");
        opponent.sendMessage(PREFIX + "§aHai accettato la sfida di " + creator.getName() + ".");
        Bukkit.broadcastMessage("§b§lBEDWARS CF! §f" + creator.getName() + " §7vs §f"
                + opponent.getName() + " §7per §f\uE0D8 §e" + formatMoney(amount) + "§7.");

        startCountdown(round);
    }

    public void cancelWaiting(Player creator) {
        BedfightCoinflip removed = waitingByCreator.remove(creator.getUniqueId());
        if (removed == null) {
            creator.sendMessage(PREFIX + "§cNon hai una sfida in attesa.");
            return;
        }
        removed.setState(BedfightCoinflip.State.FINISHED);
        restoreWaitingCreator(creator.getUniqueId());
        creator.sendMessage(PREFIX + "§aSfida annullata.");
    }

    public void showStatus(CommandSender sender) {
        if (activeRound == null) {
            sender.sendMessage(PREFIX + "§7Nessun round attivo.");
            return;
        }
        BedfightCoinflip match = activeRound.match;
        sender.sendMessage(PREFIX + "§f" + match.getCreatorName() + " §7vs §f" + match.getOpponentName());
        sender.sendMessage(PREFIX + "§9First: " + bedStatus(match.isFirstBedAlive())
                + " §8| §cOpponent: " + bedStatus(match.isOpponentBedAlive()));
    }

    public void abortByAdmin(CommandSender sender) {
        if (activeRound == null) {
            sender.sendMessage(PREFIX + "§cNessun round attivo.");
            return;
        }
        abortActiveRound("§eRound annullato da un amministratore; puntata rimborsata.", true);
        sender.sendMessage(PREFIX + "§aRound annullato.");
    }

    public void setFirstPosition(Player player) {
        setLocation("bedwars.first-position", player.getLocation());
        player.sendMessage(PREFIX + "§aPosizione First impostata.");
    }

    public void setOpponentPosition(Player player) {
        setLocation("bedwars.opponent-position", player.getLocation());
        player.sendMessage(PREFIX + "§aPosizione Opponent impostata.");
    }

    public void setFirstBed(Player player) {
        setBedFromTarget(player, "bedwars.first-bed", "First");
    }

    public void setOpponentBed(Player player) {
        setBedFromTarget(player, "bedwars.opponent-bed", "Opponent");
    }

    public Location getFirstPosition() {
        return plugin.getConfig().getLocation("bedwars.first-position");
    }

    public Location getOpponentPosition() {
        return plugin.getConfig().getLocation("bedwars.opponent-position");
    }

    public Location getFirstBedPosition() {
        return plugin.getConfig().getLocation("bedwars.first-bed");
    }

    public Location getOpponentBedPosition() {
        return plugin.getConfig().getLocation("bedwars.opponent-bed");
    }

    public void placeBet(Player bettor, String targetName, double amount) {
        ActiveRound round = activeRound;
        if (round == null || round.finishing) {
            bettor.sendMessage(PREFIX + "§cNon c'è una partita BedWars su cui scommettere.");
            return;
        }
        if (round.match.includes(bettor.getUniqueId())) {
            bettor.sendMessage(PREFIX + "§cI partecipanti non possono scommettere sulla propria partita.");
            return;
        }
        if (amount <= 0 || amount > plugin.getGameManager().getMaxAmount()) {
            bettor.sendMessage(PREFIX + "§cL'importo deve essere tra 1 e "
                    + String.format(Locale.US, "%.0f", plugin.getGameManager().getMaxAmount()) + ".");
            return;
        }
        if (round.bets.containsKey(bettor.getUniqueId())) {
            bettor.sendMessage(PREFIX + "§cHai già piazzato una scommessa in questo round.");
            return;
        }

        UUID selected;
        if (targetName.equalsIgnoreCase("first") || targetName.equalsIgnoreCase("blu")
                || targetName.equalsIgnoreCase(round.match.getCreatorName())) {
            selected = round.match.getCreator();
        } else if (targetName.equalsIgnoreCase("opponent") || targetName.equalsIgnoreCase("rosso")
                || targetName.equalsIgnoreCase(round.match.getOpponentName())) {
            selected = round.match.getOpponent();
        } else {
            bettor.sendMessage(PREFIX + "§cGiocatore non valido. Scegli §f"
                    + round.match.getCreatorName() + " §co §f" + round.match.getOpponentName() + "§c.");
            return;
        }

        if (SunnyCoinflip.getEconomy().getBalance(bettor) < amount) {
            bettor.sendMessage(PREFIX + "§cNon hai abbastanza soldi.");
            return;
        }
        EconomyResponse withdraw = SunnyCoinflip.getEconomy().withdrawPlayer(bettor, amount);
        if (!withdraw.transactionSuccess()) {
            bettor.sendMessage(PREFIX + "§cImpossibile prelevare la scommessa.");
            return;
        }

        round.bets.put(bettor.getUniqueId(), new SpectatorBet(
                bettor.getUniqueId(), bettor.getName(), selected, amount
        ));
        bettor.sendMessage(PREFIX + "§aHai scommesso §f §e" + formatMoney(amount)
                + " §asu §f" + round.match.getName(selected) + "§a.");
    }

    public void handleQuit(Player player) {
        BedfightCoinflip waiting = waitingByCreator.remove(player.getUniqueId());
        if (waiting != null) {
            waiting.setState(BedfightCoinflip.State.FINISHED);
            waitingSnapshots.remove(player.getUniqueId());
        }
        awaitingCreateAmount.remove(player.getUniqueId());
        ActiveRound spectatorRound = activeRound;
        if (spectatorRound != null) {
            spectatorRound.spectators.remove(player.getUniqueId());
        }

        ActiveRound round = activeRound;
        if (!isActiveParticipant(player.getUniqueId()) || round == null) {
            return;
        }

        UUID quitterId = player.getUniqueId();
        UUID winner = round.match.getOtherParticipant(quitterId);
        if (!round.playing) {
            // Prima del VIA vengono annullate soltanto le puntate effettuate sul giocatore
            // che ha abbandonato. Le altre vengono restituite come round non disputato.
            refundPreStartQuitBets(round, quitterId);
            abortActiveRound("§eRound annullato: un partecipante ha abbandonato prima del VIA.", false);
            return;
        }

        if (winner != null) {
            finishRound(winner, true);
        } else {
            abortActiveRound("§eRound annullato; puntata rimborsata.", true);
        }
    }

    public void handleDeath(Player player) {
        if (!isActiveParticipant(player.getUniqueId()) || activeRound == null || activeRound.finishing) {
            return;
        }
        // Fallback for damage sources that could not be intercepted before Bukkit death.
        Team team = teamOf(player.getUniqueId());
        boolean bedAlive = isBedAlive(activeRound, team);
        if (!bedAlive && activeRound.playing) {
            UUID winner = activeRound.match.getOtherParticipant(player.getUniqueId());
            if (winner != null) {
                finishRound(winner, false);
            }
            return;
        }
        activeRound.respawning.add(player.getUniqueId());
        player.sendMessage(PREFIX + "§eRientrerai in partita tra 3 secondi...");
        playSound(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        }, getRespawnDelayTicks());
    }

    public boolean handlePotentialElimination(Player player, double finalDamage, DamageCause cause) {
        ActiveRound round = activeRound;
        if (round == null || !round.playing || round.finishing
                || !isActiveParticipant(player.getUniqueId())
                || round.respawning.contains(player.getUniqueId())) {
            return false;
        }
        if (player.getHealth() - Math.max(0.0, finalDamage) > 1.0) {
            return false;
        }
        announceCustomDeath(player, cause);
        eliminateTemporarily(player, "§cSei stato eliminato!");
        return true;
    }

    public void recordLastDamager(Player victim, Player attacker) {
        if (victim == null || attacker == null || victim.getUniqueId().equals(attacker.getUniqueId())
                || !isActiveParticipant(victim.getUniqueId()) || !isActiveParticipant(attacker.getUniqueId())) {
            return;
        }
        lastHits.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    public void handleVoidLevel(Player player, Location destination) {
        ActiveRound round = activeRound;
        if (destination == null || round == null || !round.playing || round.finishing
                || !isActiveParticipant(player.getUniqueId())
                || round.respawning.contains(player.getUniqueId())) {
            return;
        }
        double voidY = plugin.getConfig().getDouble("bedwars.void-y", 43.0);
        if (destination.getY() <= voidY) {
            announceCustomDeath(player, DamageCause.VOID);
            eliminateTemporarily(player, "§cSei caduto nel vuoto!");
        }
    }

    private void eliminateTemporarily(Player player, String title) {
        ActiveRound round = activeRound;
        if (round == null || round.finishing || !round.respawning.add(player.getUniqueId())) {
            return;
        }
        Team team = teamOf(player.getUniqueId());
        if (!isBedAlive(round, team)) {
            UUID winner = round.match.getOtherParticipant(player.getUniqueId());
            if (winner != null) {
                finishRound(winner, false);
            }
            return;
        }

        player.setInvulnerable(true);
        resetCombatState(player);
        player.teleport(team == Team.FIRST ? round.firstSpawn : round.opponentSpawn);
        forceRespawnSpectatorMode(player, round);
        playSound(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        startRespawnTitleCountdown(round, player, team, title);
    }

    private void startRespawnTitleCountdown(ActiveRound round, Player player, Team team, String deathTitle) {
        long totalTicks = Math.max(20L, getRespawnDelayTicks());
        int totalSeconds = Math.max(1, (int) Math.ceil(totalTicks / 20.0));
        final int[] remaining = {totalSeconds};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeRound != round || round.finishing || !player.isOnline()
                    || !round.respawning.contains(player.getUniqueId())) {
                task[0].cancel();
                return;
            }
            if (remaining[0] <= 0) {
                task[0].cancel();
                respawnAfterElimination(round, player, team);
                return;
            }
            player.sendTitle(deathTitle, "§eRespawn tra §f" + remaining[0] + "§e...", 0, 22, 0);
            playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.0f + ((totalSeconds - remaining[0]) * 0.15f));
            remaining[0]--;
        }, 0L, 20L);
    }

    private void respawnAfterElimination(ActiveRound round, Player player, Team team) {
        if (activeRound != round || round.finishing || !player.isOnline()) {
            return;
        }
        if (!isBedAlive(round, team)) {
            UUID winner = round.match.getOtherParticipant(player.getUniqueId());
            if (winner != null) {
                finishRound(winner, false);
            }
            return;
        }
        round.respawning.remove(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(false);
        resetCombatState(player);
        player.teleport(team == Team.FIRST ? round.firstSpawn : round.opponentSpawn);
        giveKit(player, team);
        applySpawnProtection(round, player);
        player.sendTitle("§aRESPAWN!", "§73 secondi di protezione", 0, 25, 5);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    public Location getRespawnLocation(UUID playerId) {
        PlayerSnapshot pending = pendingRestores.get(playerId);
        if (pending != null) {
            return pending.location.clone();
        }
        if (!isActiveParticipant(playerId) || activeRound == null) {
            return null;
        }
        return teamOf(playerId) == Team.FIRST
                ? activeRound.firstSpawn.clone()
                : activeRound.opponentSpawn.clone();
    }

    public void handleRespawn(Player player) {
        PlayerSnapshot restore = pendingRestores.remove(player.getUniqueId());
        if (restore != null) {
            Bukkit.getScheduler().runTask(plugin, () -> applySnapshotSafely(player, restore));
            return;
        }
        ActiveRound round = activeRound;
        if (!isActiveParticipant(player.getUniqueId()) || round == null || round.finishing) {
            return;
        }
        Team team = teamOf(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (activeRound != round || round.finishing) {
                return;
            }
            round.respawning.remove(player.getUniqueId());
            player.setGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(false);
            resetCombatState(player);
            player.teleport(team == Team.FIRST ? round.firstSpawn : round.opponentSpawn);
            giveKit(player, team);
            applySpawnProtection(round, player);
        });
    }

    public void handleChangedWorld(Player player) {
        UUID playerId = player.getUniqueId();
        if (restoringPlayers.contains(playerId)) {
            return;
        }

        PlayerSnapshot pending = pendingRestores.remove(playerId);
        if (pending != null) {
            applySnapshotSafely(player, pending);
            return;
        }

        ActiveRound round = activeRound;
        if (!isActiveParticipant(playerId) || round == null) {
            return;
        }
        if (player.getWorld().getUID().equals(round.world.getUID())) {
            giveKit(player, teamOf(playerId));
            return;
        }

        // A participant left the arena world through another plugin/portal.
        // Treat it as a forfeit; finishRound restores the original snapshot.
        UUID winner = round.match.getOtherParticipant(playerId);
        if (winner != null) {
            finishRound(winner, true);
        } else {
            abortActiveRound("§eRound annullato; puntate rimborsate.", true);
        }
    }

    public void handleJoin(Player player) {
        PlayerSnapshot pending = pendingRestores.remove(player.getUniqueId());
        if (pending != null) {
            Bukkit.getScheduler().runTask(plugin, () -> applySnapshotSafely(player, pending));
        }
    }

    public boolean canMoveDuringCountdown(Player player, Location from, Location to) {
        if (to == null) {
            return true;
        }
        boolean waitingFirst = waitingByCreator.containsKey(player.getUniqueId());
        boolean countdownParticipant = isActiveParticipant(player.getUniqueId())
                && activeRound != null && !activeRound.playing;
        if (!waitingFirst && !countdownParticipant) {
            return true;
        }
        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }

    public boolean canLeaveArena(Player player, Location destination) {
        if (!isActiveParticipant(player.getUniqueId()) || activeRound == null || destination == null
                || destination.getWorld() == null) {
            return true;
        }
        return destination.getWorld().getUID().equals(activeRound.world.getUID());
    }

    public boolean handleBlockPlace(Player player, Block block, BlockData replacedData) {
        if (activeRound == null || !block.getWorld().getUID().equals(activeRound.world.getUID())) {
            return true;
        }
        if (!isActiveParticipant(player.getUniqueId()) || !activeRound.playing) {
            return false;
        }
        int maxHeight = getMaxBuildHeight();
        if (block.getY() > maxHeight) {
            player.sendMessage(PREFIX + "§cNon puoi piazzare blocchi sopra Y=" + maxHeight + ".");
            return false;
        }

        BlockKey key = BlockKey.of(block);
        activeRound.originalBlocks.putIfAbsent(key, replacedData.clone());
        return true;
    }

    public BreakResult handleBlockBreak(Player player, Block block) {
        if (activeRound == null || !block.getWorld().getUID().equals(activeRound.world.getUID())) {
            return BreakResult.ALLOW;
        }
        if (!isActiveParticipant(player.getUniqueId()) || !activeRound.playing) {
            return BreakResult.DENY;
        }

        BlockKey key = BlockKey.of(block);
        Team team = teamOf(player.getUniqueId());
        Set<BlockKey> ownBed = team == Team.FIRST ? activeRound.firstBed : activeRound.opponentBed;
        Set<BlockKey> enemyBed = team == Team.FIRST ? activeRound.opponentBed : activeRound.firstBed;

        if (ownBed.contains(key)) {
            player.sendMessage(PREFIX + "§cNon puoi rompere il tuo letto.");
            return BreakResult.DENY;
        }
        if (enemyBed.contains(key)) {
            captureBedBlocks(activeRound, enemyBed);
            if (team == Team.FIRST) {
                activeRound.match.setOpponentBedAlive(false);
            } else {
                activeRound.match.setFirstBedAlive(false);
            }
            announceBedDestroyed(player, team == Team.FIRST ? Team.OPPONENT : Team.FIRST);
            return BreakResult.BED;
        }
        if (isBreakableArenaMaterial(block.getType())) {
            // Save the exact original state even when this block belongs to the map.
            // Player-placed blocks are already present in originalBlocks, so putIfAbsent
            // also preserves the state that existed before the first placement.
            activeRound.originalBlocks.putIfAbsent(key, block.getBlockData().clone());
            return BreakResult.BREAKABLE_ARENA_BLOCK;
        }

        player.sendMessage(PREFIX + "§cPuoi rompere solo end stone, legno, lana e il letto avversario.");
        return BreakResult.DENY;
    }

    /**
     * Materials considered "legno" for arena protection. This deliberately
     * includes planks, logs, wood, stems and hyphae, including stripped variants,
     * but not crafted wooden utility blocks such as chests, doors or crafting tables.
     */
    private boolean isBreakableArenaMaterial(Material material) {
        if (material == Material.END_STONE || material.name().endsWith("_WOOL")) {
            return true;
        }

        String name = material.name();
        return name.endsWith("_PLANKS")
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || name.equals("BAMBOO_BLOCK")
                || name.equals("STRIPPED_BAMBOO_BLOCK")
                || name.equals("BAMBOO_MOSAIC");
    }

    public boolean isRoundWorld(World world) {
        return activeRound != null && world != null && world.getUID().equals(activeRound.world.getUID());
    }

    public boolean canDamage(Player attacker, Player victim) {
        ActiveRound round = activeRound;
        if (round == null) {
            return true;
        }
        boolean attackerIn = attacker != null && isActiveParticipant(attacker.getUniqueId());
        boolean victimIn = victim != null && isActiveParticipant(victim.getUniqueId());
        if (!attackerIn && !victimIn) {
            return true;
        }
        if (!round.playing || !attackerIn || !victimIn
                || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return false;
        }
        if (round.respawning.contains(attacker.getUniqueId())
                || round.respawning.contains(victim.getUniqueId())) {
            return false;
        }

        // Attacking always consumes the attacker's spawn protection before the hit is evaluated.
        // The same first hit is not cancelled unless the victim still has their own protection.
        if (hasSpawnProtection(round, attacker.getUniqueId())) {
            removeSpawnProtection(round, attacker, true);
        }
        return !hasSpawnProtection(round, victim.getUniqueId());
    }

    public boolean canTakeDamage(Player victim) {
        ActiveRound round = activeRound;
        if (!isActiveParticipant(victim.getUniqueId())) {
            return true;
        }
        return round != null && round.playing
                && !round.respawning.contains(victim.getUniqueId())
                && !hasSpawnProtection(round, victim.getUniqueId());
    }

    public void handleShutdown() {
        if (activeRound != null) {
            abortActiveRound("§eServer in arresto; puntata rimborsata.", true);
        }
        for (UUID creatorId : new ArrayList<>(waitingSnapshots.keySet())) {
            restoreWaitingCreator(creatorId);
        }
        awaitingCreateAmount.clear();
        waitingByCreator.clear();
    }

    private void startCountdown(ActiveRound round) {
        int configured = Math.max(0, plugin.getConfig().getInt("bedwars.countdown", 5));
        if (configured == 0) {
            beginPlaying(round);
            return;
        }

        final int[] seconds = {configured};
        round.countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeRound != round || round.finishing) {
                if (round.countdownTask != null) {
                    round.countdownTask.cancel();
                }
                return;
            }

            Player first = Bukkit.getPlayer(round.match.getCreator());
            Player opponent = Bukkit.getPlayer(round.match.getOpponent());
            if (first == null || opponent == null || !first.isOnline() || !opponent.isOnline()) {
                UUID winner = first != null && first.isOnline() ? first.getUniqueId()
                        : opponent != null && opponent.isOnline() ? opponent.getUniqueId() : null;
                if (winner == null) {
                    abortActiveRound("§eRound annullato; puntate rimborsate.", true);
                } else {
                    finishRound(winner, true);
                }
                return;
            }

            if (seconds[0] <= 0) {
                round.countdownTask.cancel();
                beginPlaying(round);
                return;
            }

            String title = "§b" + seconds[0];
            first.sendTitle(title, "§7Preparati!", 0, 25, 0);
            opponent.sendTitle(title, "§7Preparati!", 0, 25, 0);
            playSound(first, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.2f);
            playSound(opponent, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.2f);
            seconds[0]--;
        }, 0L, 20L);
    }

    private void beginPlaying(ActiveRound round) {
        if (activeRound != round || round.finishing) {
            return;
        }
        round.playing = true;
        round.match.setState(BedfightCoinflip.State.ACTIVE);

        Player first = Bukkit.getPlayer(round.match.getCreator());
        Player opponent = Bukkit.getPlayer(round.match.getOpponent());
        if (first != null) {
            first.setInvulnerable(false);
            applySpawnProtection(round, first);
            first.sendTitle("§aVIA!", "§73 secondi di protezione", 0, 30, 10);
            playSound(first, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        }
        if (opponent != null) {
            opponent.setInvulnerable(false);
            applySpawnProtection(round, opponent);
            opponent.sendTitle("§aVIA!", "§73 secondi di protezione", 0, 30, 10);
            playSound(opponent, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        }
        startRoundClock(round);
    }

    private void startRoundClock(ActiveRound round) {
        int bedsDestroyAfter = Math.max(1, plugin.getConfig().getInt("bedwars.beds-auto-destroy-seconds", 300));
        int deathmatchAfter = Math.max(bedsDestroyAfter, plugin.getConfig().getInt("bedwars.deathmatch-start-seconds", 420));
        double startingDamage = Math.max(0.1, plugin.getConfig().getDouble("bedwars.deathmatch-starting-damage", 1.0));
        double damageIncrease = Math.max(0.0, plugin.getConfig().getDouble("bedwars.deathmatch-damage-increase", 1.0));

        round.roundClockTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeRound != round || round.finishing || !round.playing) {
                cancelRoundClock(round);
                return;
            }
            round.elapsedSeconds++;

            if (!round.bedsAutoDestroyed && round.elapsedSeconds >= bedsDestroyAfter) {
                round.bedsAutoDestroyed = true;
                autoDestroyBothBeds(round);
            }

            if (round.elapsedSeconds >= deathmatchAfter) {
                int deathmatchSecond = round.elapsedSeconds - deathmatchAfter + 1;
                double damage = startingDamage + ((deathmatchSecond - 1) * damageIncrease);
                applyDeathmatchDamage(round, damage, deathmatchSecond);
            }
        }, 20L, 20L);
    }

    private void autoDestroyBothBeds(ActiveRound round) {
        round.match.setFirstBedAlive(false);
        round.match.setOpponentBedAlive(false);
        removeBedBlocks(round, round.firstBed);
        removeBedBlocks(round, round.opponentBed);

        forEachRoundViewer(round, player -> {
            player.sendTitle("§c§lLETTI DISTRUTTI!", "§7Non potete più respawnare", 5, 45, 10);
            player.sendMessage(PREFIX + "§cTempo scaduto: entrambi i letti si sono autodistrutti!");
            playSound(player, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.2f);
        });
    }

    private void removeBedBlocks(ActiveRound round, Set<BlockKey> bed) {
        for (BlockKey key : bed) {
            if (round.world.getUID().equals(key.worldId())) {
                round.world.getBlockAt(key.x(), key.y(), key.z()).setType(Material.AIR, false);
            }
        }
    }

    private void applyDeathmatchDamage(ActiveRound round, double damage, int deathmatchSecond) {
        if (!round.deathmatchAnnounced) {
            round.deathmatchAnnounced = true;
            forEachRoundViewer(round, player -> {
                player.sendTitle("§4§lDEATHMATCH", "§cIl danno aumenta ogni secondo!", 5, 50, 10);
                player.sendMessage(PREFIX + "§4Deathmatch iniziato: perderete vita ogni secondo.");
                playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.9f);
            });
        }

        damageDeathmatchPlayer(round, Bukkit.getPlayer(round.match.getCreator()), damage);
        damageDeathmatchPlayer(round, Bukkit.getPlayer(round.match.getOpponent()), damage);

        if (deathmatchSecond % 5 == 0) {
            String hearts = String.format(Locale.US, "%.1f", damage / 2.0);
            forEachRoundViewer(round, player -> player.sendActionBar("§4DEATHMATCH §8• §cDanno attuale: §f" + hearts + " cuori/s"));
        }
    }

    private void damageDeathmatchPlayer(ActiveRound round, Player player, double damage) {
        if (player == null || !player.isOnline() || round.finishing
                || round.respawning.contains(player.getUniqueId())
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (player.getHealth() - damage <= 1.0) {
            announceCustomDeath(player, DamageCause.CUSTOM);
            eliminateTemporarily(player, "§4Eliminato dal Deathmatch!");
            return;
        }
        player.setHealth(Math.max(1.0, player.getHealth() - damage));
        playSound(player, Sound.ENTITY_PLAYER_HURT, 0.7f, 0.7f);
    }

    private void announceCustomDeath(Player victim, DamageCause cause) {
        if (!victim.getWorld().getName().equalsIgnoreCase("bedfight")) {
            return;
        }
        Player attacker = null;
        LastHit hit = lastHits.get(victim.getUniqueId());
        if (hit != null && System.currentTimeMillis() - hit.timestampMillis() <= 10_000L) {
            attacker = Bukkit.getPlayer(hit.attackerId());
        }
        lastHits.remove(victim.getUniqueId());

        String message;
        if (attacker != null && attacker.isOnline()) {
            message = "§c☠ §f" + victim.getName() + " §7è stato ucciso da §f" + attacker.getName() + "§7.";
        } else {
            message = switch (cause) {
                case VOID -> "§c☠ §f" + victim.getName() + " §7è caduto nel vuoto.";
                case FALL -> "§c☠ §f" + victim.getName() + " §7si è schiantato.";
                case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> "§c☠ §f" + victim.getName() + " §7è finito arrosto.";
                case PROJECTILE -> "§c☠ §f" + victim.getName() + " §7è stato colpito a distanza.";
                case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "§c☠ §f" + victim.getName() + " §7è esploso.";
                case DROWNING -> "§c☠ §f" + victim.getName() + " §7non sapeva nuotare.";
                case SUFFOCATION -> "§c☠ §f" + victim.getName() + " §7è rimasto incastrato nei blocchi.";
                default -> "§c☠ §f" + victim.getName() + " §7è stato eliminato.";
            };
        }
        Bukkit.broadcastMessage(message);
    }

    private void forEachRoundViewer(ActiveRound round, java.util.function.Consumer<Player> action) {
        Player first = Bukkit.getPlayer(round.match.getCreator());
        Player opponent = Bukkit.getPlayer(round.match.getOpponent());
        if (first != null) action.accept(first);
        if (opponent != null) action.accept(opponent);
        for (UUID spectatorId : round.spectators.keySet()) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null) action.accept(spectator);
        }
    }

    private boolean prepareFighter(Player player, Location spawn, Team team) {
        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(true);
        clearPotionEffects(player);
        resetCombatState(player);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        if (!player.teleport(spawn)) {
            return false;
        }
        giveKit(player, team);
        return true;
    }

    private void resetCombatState(Player player) {
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setFoodLevel(20);
        player.setSaturation(0.0f);
        player.setExhaustion(0.0f);
        player.setHealth(player.getMaxHealth());
        player.setLevel(0);
        player.setExp(0.0f);
        player.setTotalExperience(0);
    }

    private void giveKit(Player player, Team team) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);

        Color color = team == Team.FIRST ? Color.BLUE : Color.RED;
        ChatColor chatColor = team == Team.FIRST ? ChatColor.BLUE : ChatColor.RED;
        Material wool = team == Team.FIRST ? Material.BLUE_WOOL : Material.RED_WOOL;

        inventory.setHelmet(leatherArmor(Material.LEATHER_HELMET, color, chatColor + "Elmo BedWars"));
        inventory.setChestplate(leatherArmor(Material.LEATHER_CHESTPLATE, color, chatColor + "Corazza BedWars"));
        inventory.setLeggings(leatherArmor(Material.LEATHER_LEGGINGS, color, chatColor + "Gambali BedWars"));
        inventory.setBoots(leatherArmor(Material.LEATHER_BOOTS, color, chatColor + "Stivali BedWars"));

        inventory.setItem(0, unbreakableItem(Material.WOODEN_SWORD, "§fSpada di legno", false));
        inventory.setItem(1, unbreakableItem(Material.SHEARS, "§fCesoie", false));
        inventory.setItem(2, unbreakableItem(Material.WOODEN_AXE, "§fAscia", true));
        inventory.setItem(3, unbreakableItem(Material.WOODEN_PICKAXE, "§fPiccone", true));
        inventory.setItem(4, plainStack(wool, 64));
        inventory.setItemInOffHand(plainStack(wool, 64));
        inventory.setHeldItemSlot(0);
        player.updateInventory();
    }

    private ItemStack leatherArmor(Material material, Color color, String name) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        meta.setDisplayName(name);
        applyUnbreakable(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack unbreakableItem(Material material, String name, boolean efficiency) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        applyUnbreakable(meta);
        item.setItemMeta(meta);
        if (efficiency) {
            item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 1);
        }
        return item;
    }

    private ItemStack plainStack(Material material, int amount) {
        // Placeable blocks do not have durability. Keeping them free of custom metadata
        // allows blocks recovered from the arena to stack with the kit wool.
        return new ItemStack(material, amount);
    }

    public boolean isUndroppableKitItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        Material type = item.getType();
        if (type == Material.BLUE_WOOL || type == Material.RED_WOOL) {
            return true;
        }

        return type == Material.WOODEN_SWORD
                || type == Material.SHEARS
                || type == Material.WOODEN_AXE
                || type == Material.WOODEN_PICKAXE
                || type == Material.LEATHER_HELMET
                || type == Material.LEATHER_CHESTPLATE
                || type == Material.LEATHER_LEGGINGS
                || type == Material.LEATHER_BOOTS;
    }

    private void applyUnbreakable(ItemMeta meta) {
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
    }

    private void finishRound(UUID winnerId, boolean forfeit) {
        ActiveRound round = activeRound;
        if (round == null || round.finishing || !round.match.includes(winnerId)) {
            return;
        }
        round.finishing = true;
        cancelCountdown(round);
        cancelRoundClock(round);
        activeRound = null;
        lastHits.clear();

        restoreArena(round);

        double prize = round.match.getAmount() * plugin.getGameManager().getWinMultiplier();
        OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerId);
        EconomyResponse payout = SunnyCoinflip.getEconomy().depositPlayer(winner, prize);
        boolean paid = payout.transactionSuccess();
        if (!paid) {
            plugin.getLogger().severe("Pagamento BedWars fallito per " + round.match.getName(winnerId)
                    + ": " + payout.errorMessage + ". Rimborso delle puntate in corso.");
            refundStakes(round.match);
        }

        UUID loserId = round.match.getOtherParticipant(winnerId);
        playRoundEndSounds(round, winnerId, loserId);
        if (paid) {
            settleBets(round, winnerId);
        } else {
            refundBets(round);
        }
        restoreParticipants(round);
        round.match.setState(BedfightCoinflip.State.FINISHED);

        String winnerName = round.match.getName(winnerId);
        String loserName = loserId == null ? "Sconosciuto" : round.match.getName(loserId);
        messageOnline(winnerId, paid
                ? PREFIX + "§aHai vinto contro " + loserName + "! Premio: §f\uE0D8 §e" + formatMoney(prize)
                : PREFIX + "§ePagamento non riuscito: entrambe le puntate sono state rimborsate.");
        if (loserId != null) {
            messageOnline(loserId, paid
                    ? PREFIX + "§cHai perso contro " + winnerName + "."
                    : PREFIX + "§ePagamento non riuscito: puntata rimborsata.");
        }

        Bukkit.broadcastMessage("§b§lBEDWARS CF! §f" + winnerName + " §7ha sconfitto §f"
                + loserName + (paid ? " §7e ha vinto §f\uE0D8 §e" + formatMoney(prize) : " §7(puntate rimborsate)")
                + (forfeit ? " §7per abbandono." : "§7."));
    }

    private void abortActiveRound(String message, boolean refund) {
        ActiveRound round = activeRound;
        if (round == null || round.finishing) {
            return;
        }
        round.finishing = true;
        cancelCountdown(round);
        cancelRoundClock(round);
        activeRound = null;
        lastHits.clear();
        restoreArena(round);
        if (refund) {
            refundStakes(round.match);
        }
        refundBets(round);
        restoreParticipants(round);
        round.match.setState(BedfightCoinflip.State.FINISHED);
        messageOnline(round.match.getCreator(), PREFIX + message);
        messageOnline(round.match.getOpponent(), PREFIX + message);
    }

    private void restoreParticipants(ActiveRound round) {
        Map<UUID, PlayerSnapshot> allSnapshots = new LinkedHashMap<>(round.snapshots);
        allSnapshots.putAll(round.spectators);
        for (Map.Entry<UUID, PlayerSnapshot> entry : allSnapshots.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerSnapshot snapshot = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                pendingRestores.put(playerId, snapshot);
                continue;
            }
            if (player.isDead()) {
                pendingRestores.put(playerId, snapshot);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && player.isDead()) {
                        player.spigot().respawn();
                    }
                });
            } else {
                applySnapshotSafely(player, snapshot);
            }
        }
    }

    private void restoreWaitingCreator(UUID playerId) {
        PlayerSnapshot snapshot = waitingSnapshots.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (snapshot != null && player != null && player.isOnline()) {
            applySnapshotSafely(player, snapshot);
        } else if (snapshot != null) {
            pendingRestores.put(playerId, snapshot);
        }
    }

    private void applySnapshotSafely(Player player, PlayerSnapshot snapshot) {
        UUID playerId = player.getUniqueId();
        restoringPlayers.add(playerId);

        // Restore the location and non-inventory state first. A world-change listener from
        // this or another plugin may clear/replace the inventory during the teleport.
        snapshot.applyBaseState(player);

        // Re-apply the saved inventory on the following tick, after PlayerChangedWorldEvent
        // and other teleport listeners have completed. This prevents players from returning
        // from BedWars with an empty inventory or with the BedWars kit still equipped.
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (player.isOnline()) {
                    snapshot.applyInventory(player);
                } else {
                    pendingRestores.put(playerId, snapshot);
                }
            } finally {
                Bukkit.getScheduler().runTask(plugin, () -> restoringPlayers.remove(playerId));
            }
        });
    }

    private void restoreArena(ActiveRound round) {
        List<Map.Entry<BlockKey, BlockData>> blocks = new ArrayList<>(round.originalBlocks.entrySet());
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Map.Entry<BlockKey, BlockData> entry = blocks.get(i);
            BlockKey key = entry.getKey();
            if (!round.world.getUID().equals(key.worldId)) {
                continue;
            }
            round.world.getBlockAt(key.x, key.y, key.z).setBlockData(entry.getValue(), false);
        }

        if (plugin.getConfig().getBoolean("bedwars.cleanup-new-entities", true)) {
            for (Entity entity : new ArrayList<>(round.world.getEntities())) {
                if (!(entity instanceof Player) && !round.initialEntities.contains(entity.getUniqueId())) {
                    entity.remove();
                }
            }
        }
    }

    private void captureBedBlocks(ActiveRound round, Set<BlockKey> bedBlocks) {
        for (BlockKey key : bedBlocks) {
            Block block = round.world.getBlockAt(key.x, key.y, key.z);
            round.originalBlocks.putIfAbsent(key, block.getBlockData().clone());
        }
    }

    private void announceBedDestroyed(Player breaker, Team destroyedTeam) {
        String destroyedPlayer = destroyedTeam == Team.FIRST
                ? activeRound.match.getCreatorName()
                : activeRound.match.getOpponentName();
        String message = PREFIX + "§cIl letto di §f" + destroyedPlayer + " §cè stato distrutto da §f"
                + breaker.getName() + "§c! La prossima morte sarà definitiva.";
        messageOnline(activeRound.match.getCreator(), message);
        messageOnline(activeRound.match.getOpponent(), message);
        Player first = Bukkit.getPlayer(activeRound.match.getCreator());
        Player opponent = Bukkit.getPlayer(activeRound.match.getOpponent());
        playSound(first, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.3f);
        playSound(opponent, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.3f);
    }

    private Set<BlockKey> resolveConfiguredOrNearbyBed(Location configured, Location spawn, int radius) {
        if (configured != null && configured.getWorld() != null) {
            Set<BlockKey> configuredBed = resolveBed(configured.getBlock());
            if (!configuredBed.isEmpty()) {
                return configuredBed;
            }
        }
        Block nearest = findNearestBed(spawn, radius);
        return nearest == null ? Set.of() : resolveBed(nearest);
    }

    private Block findNearestBed(Location origin, int radius) {
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        Block nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        World world = origin.getWorld();
        int verticalRadius = Math.min(radius, 6);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(
                            origin.getBlockX() + x,
                            origin.getBlockY() + y,
                            origin.getBlockZ() + z
                    );
                    if (!(block.getBlockData() instanceof Bed)) {
                        continue;
                    }
                    double distance = block.getLocation().distanceSquared(origin);
                    if (distance < nearestDistance) {
                        nearest = block;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private Set<BlockKey> resolveBed(Block block) {
        if (!(block.getBlockData() instanceof Bed bedData)) {
            return Set.of();
        }
        Set<BlockKey> result = new HashSet<>();
        result.add(BlockKey.of(block));

        BlockFace otherDirection = bedData.getPart() == Bed.Part.FOOT
                ? bedData.getFacing()
                : bedData.getFacing().getOppositeFace();
        Block other = block.getRelative(otherDirection);
        if (other.getBlockData() instanceof Bed) {
            result.add(BlockKey.of(other));
        }
        return result;
    }

    private void setBedFromTarget(Player player, String path, String teamName) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || !(target.getBlockData() instanceof Bed)) {
            player.sendMessage(PREFIX + "§cGuarda un letto entro 6 blocchi e riprova.");
            return;
        }
        setLocation(path, target.getLocation());
        player.sendMessage(PREFIX + "§aLetto " + teamName + " impostato.");
    }

    private void setLocation(String path, Location location) {
        plugin.getConfig().set(path, location);
        plugin.saveConfig();
    }

    private boolean isArenaReady(CommandSender sender) {
        Location first = getFirstPosition();
        Location opponent = getOpponentPosition();
        if (first == null || opponent == null || first.getWorld() == null || opponent.getWorld() == null) {
            sender.sendMessage(PREFIX + "§cArena non configurata. Usa setfirstpos e setopponentpos.");
            return false;
        }
        if (!first.getWorld().getUID().equals(opponent.getWorld().getUID())) {
            sender.sendMessage(PREFIX + "§cLe posizioni devono essere nello stesso mondo.");
            return false;
        }
        return true;
    }

    private void resetWaitingChallenge(BedfightCoinflip challenge) {
        challenge.setOpponent(null, null);
        challenge.setState(BedfightCoinflip.State.WAITING);
    }

    private void refundStakes(BedfightCoinflip match) {
        SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(match.getCreator()), match.getAmount());
        if (match.getOpponent() != null) {
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(match.getOpponent()), match.getAmount());
        }
    }

    private boolean isBusyInOtherMode(UUID playerId) {
        if (plugin.getGameManager().getCoinflip(playerId) != null) {
            return true;
        }
        PillarMatch pillar = plugin.getGameManager().getActivePillarMatch();
        return pillar != null && (playerId.equals(pillar.getCreator())
                || (pillar.getOpponent() != null && playerId.equals(pillar.getOpponent())));
    }

    private BedfightCoinflip findWaitingByName(String name) {
        for (BedfightCoinflip challenge : waitingByCreator.values()) {
            if (challenge.getCreatorName().equalsIgnoreCase(name)) {
                return challenge;
            }
        }
        return null;
    }

    private void purgeExpiredChallenges() {
        long expirySeconds = plugin.getConfig().getLong("bedwars.challenge-expire-seconds", 300L);
        if (expirySeconds <= 0) {
            return;
        }
        long oldestAllowed = System.currentTimeMillis() - expirySeconds * 1000L;
        List<UUID> expired = waitingByCreator.values().stream()
                .filter(challenge -> challenge.getCreatedAt() < oldestAllowed)
                .map(BedfightCoinflip::getCreator)
                .toList();
        for (UUID creatorId : expired) {
            waitingByCreator.remove(creatorId);
            restoreWaitingCreator(creatorId);
            messageOnline(creatorId, PREFIX + "§eLa sfida è scaduta.");
        }
    }

    private Team teamOf(UUID playerId) {
        if (activeRound != null && activeRound.match.getCreator().equals(playerId)) {
            return Team.FIRST;
        }
        return Team.OPPONENT;
    }

    private int getBedSearchRadius() {
        return Math.max(1, plugin.getConfig().getInt("bedwars.bed-search-radius", 12));
    }

    private boolean isBedAlive(ActiveRound round, Team team) {
        return team == Team.FIRST ? round.match.isFirstBedAlive() : round.match.isOpponentBedAlive();
    }

    private long getRespawnDelayTicks() {
        return Math.max(1L, plugin.getConfig().getLong("bedwars.respawn-delay-ticks", 60L));
    }

    private void applySpawnProtection(ActiveRound round, Player player) {
        long duration = Math.max(0L, plugin.getConfig().getLong("bedwars.spawn-protection-ticks", 60L));
        round.spawnProtectedUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration * 50L);
        if (duration > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeRound == round && player.isOnline()
                        && !round.respawning.contains(player.getUniqueId())) {
                    removeSpawnProtection(round, player, false);
                }
            }, duration);
        }
    }

    private void removeSpawnProtection(ActiveRound round, Player player, boolean causedByAttack) {
        if (round.spawnProtectedUntil.remove(player.getUniqueId()) == null) {
            return;
        }
        player.sendActionBar(causedByAttack
                ? "§cProtezione spawn disattivata: hai attaccato"
                : "§aProtezione spawn terminata");
        playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, causedByAttack ? 0.8f : 1.4f);
    }

    private int getMaxBuildHeight() {
        // Current key plus backwards-compatible aliases used by older overlays/configs.
        if (plugin.getConfig().contains("bedwars.max-build-height")) {
            return plugin.getConfig().getInt("bedwars.max-build-height");
        }
        if (plugin.getConfig().contains("bedwars.max-height")) {
            return plugin.getConfig().getInt("bedwars.max-height");
        }
        if (plugin.getConfig().contains("bedfight.max-build-height")) {
            return plugin.getConfig().getInt("bedfight.max-build-height");
        }
        return 100;
    }

    private boolean hasSpawnProtection(ActiveRound round, UUID playerId) {
        Long until = round.spawnProtectedUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            round.spawnProtectedUntil.remove(playerId);
            return false;
        }
        return true;
    }


    private void refundPreStartQuitBets(ActiveRound round, UUID quitterId) {
        if (round.bets.isEmpty()) {
            return;
        }
        for (SpectatorBet bet : round.bets.values()) {
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(bet.bettorId()), bet.amount());
            if (bet.selectedPlayer().equals(quitterId)) {
                messageOnline(bet.bettorId(), PREFIX + "§eLa tua scommessa su §f"
                        + round.match.getName(quitterId)
                        + " §eè stata annullata e rimborsata perché il giocatore ha abbandonato prima del VIA: §f §e"
                        + formatMoney(bet.amount()) + "§e.");
            } else {
                messageOnline(bet.bettorId(), PREFIX + "§eRound non disputato: scommessa restituita §f §e"
                        + formatMoney(bet.amount()) + "§e.");
            }
        }
        round.bets.clear();
    }

    private void settleBets(ActiveRound round, UUID winnerId) {
        if (round.bets.isEmpty()) {
            return;
        }
        double totalPool = round.bets.values().stream().mapToDouble(SpectatorBet::amount).sum();
        double winningPool = round.bets.values().stream()
                .filter(bet -> bet.selectedPlayer().equals(winnerId))
                .mapToDouble(SpectatorBet::amount).sum();
        if (winningPool <= 0.0) {
            refundBets(round);
            return;
        }
        for (SpectatorBet bet : round.bets.values()) {
            if (!bet.selectedPlayer().equals(winnerId)) {
                messageOnline(bet.bettorId(), PREFIX + "§cHai perso la scommessa su §f"
                        + round.match.getName(bet.selectedPlayer()) + "§c.");
                continue;
            }
            double payout = totalPool * (bet.amount() / winningPool);
            EconomyResponse response = SunnyCoinflip.getEconomy().depositPlayer(
                    Bukkit.getOfflinePlayer(bet.bettorId()), payout
            );
            messageOnline(bet.bettorId(), response.transactionSuccess()
                    ? PREFIX + "§aScommessa vinta! Incasso: §f §e" + formatMoney(payout)
                    : PREFIX + "§cPagamento scommessa fallito; contatta un amministratore.");
        }
        round.bets.clear();
    }

    private void refundBets(ActiveRound round) {
        for (SpectatorBet bet : round.bets.values()) {
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(bet.bettorId()), bet.amount());
            messageOnline(bet.bettorId(), PREFIX + "§eScommessa rimborsata: §f §e"
                    + formatMoney(bet.amount()) + "§e.");
        }
        round.bets.clear();
    }

    private void playRoundEndSounds(ActiveRound round, UUID winnerId, UUID loserId) {
        playSound(Bukkit.getPlayer(winnerId), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        if (loserId != null) {
            playSound(Bukkit.getPlayer(loserId), Sound.ENTITY_WITHER_DEATH, 0.6f, 1.4f);
        }
        for (SpectatorBet bet : round.bets.values()) {
            Player bettor = Bukkit.getPlayer(bet.bettorId());
            playSound(bettor, bet.selectedPlayer().equals(winnerId)
                    ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        }
    }

    private void playSound(Player player, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void cancelCountdown(ActiveRound round) {
        if (round.countdownTask != null) {
            round.countdownTask.cancel();
            round.countdownTask = null;
        }
    }

    private void cancelRoundClock(ActiveRound round) {
        if (round.roundClockTask != null) {
            round.roundClockTask.cancel();
            round.roundClockTask = null;
        }
    }

    private void clearPotionEffects(Player player) {
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
    }

    private boolean isSameWorld(Location location, World world) {
        return location != null && location.getWorld() != null
                && location.getWorld().getUID().equals(world.getUID());
    }

    private String bedStatus(boolean alive) {
        return alive ? "§aIntegro" : "§cDistrutto";
    }

    private String formatMoney(double amount) {
        if (amount == Math.rint(amount)) {
            return String.format(Locale.US, "%,.0f", amount);
        }
        return String.format(Locale.US, "%,.2f", amount);
    }

    private void messageOnline(UUID playerId, String message) {
        if (playerId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(message);
        }
    }

    public enum BreakResult {
        ALLOW,
        DENY,
        BED,
        BREAKABLE_ARENA_BLOCK
    }

    private enum Team {
        FIRST("Blu"),
        OPPONENT("Rossa");

        private final String displayName;

        Team(String displayName) {
            this.displayName = displayName;
        }
    }

    private record LastHit(UUID attackerId, long timestampMillis) { }

    private static final class ActiveRound {
        private final BedfightCoinflip match;
        private final World world;
        private final Location firstSpawn;
        private final Location opponentSpawn;
        private final Set<BlockKey> firstBed;
        private final Set<BlockKey> opponentBed;
        private final Map<BlockKey, BlockData> originalBlocks = new LinkedHashMap<>();
        private final Set<UUID> initialEntities = new HashSet<>();
        private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
        private final Map<UUID, SpectatorBet> bets = new LinkedHashMap<>();
        private final Map<UUID, PlayerSnapshot> spectators = new LinkedHashMap<>();
        private final Set<UUID> respawning = new HashSet<>();
        private final Map<UUID, Long> spawnProtectedUntil = new HashMap<>();
        private BukkitTask countdownTask;
        private BukkitTask roundClockTask;
        private int elapsedSeconds;
        private boolean bedsAutoDestroyed;
        private boolean deathmatchAnnounced;
        private boolean playing;
        private boolean finishing;

        private ActiveRound(BedfightCoinflip match, World world, Location firstSpawn,
                            Location opponentSpawn, Set<BlockKey> firstBed, Set<BlockKey> opponentBed) {
            this.match = match;
            this.world = world;
            this.firstSpawn = firstSpawn;
            this.opponentSpawn = opponentSpawn;
            this.firstBed = new HashSet<>(firstBed);
            this.opponentBed = new HashSet<>(opponentBed);
        }
    }

    private record SpectatorBet(UUID bettorId, String bettorName, UUID selectedPlayer, double amount) {
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private static final class PlayerSnapshot {
        private final Location location;
        private final ItemStack[] storage;
        private final ItemStack[] armor;
        private final ItemStack offHand;
        private final GameMode gameMode;
        private final Collection<PotionEffect> potionEffects;
        private final double health;
        private final int food;
        private final float saturation;
        private final float exhaustion;
        private final int level;
        private final float exp;
        private final int totalExp;
        private final boolean allowFlight;
        private final boolean flying;
        private final int fireTicks;

        private PlayerSnapshot(Player player) {
            PlayerInventory inventory = player.getInventory();
            this.location = player.getLocation().clone();
            this.storage = cloneItems(inventory.getStorageContents());
            this.armor = cloneItems(inventory.getArmorContents());
            this.offHand = cloneItem(inventory.getItemInOffHand());
            this.gameMode = player.getGameMode();
            this.potionEffects = new ArrayList<>(player.getActivePotionEffects());
            this.health = player.getHealth();
            this.food = player.getFoodLevel();
            this.saturation = player.getSaturation();
            this.exhaustion = player.getExhaustion();
            this.level = player.getLevel();
            this.exp = player.getExp();
            this.totalExp = player.getTotalExperience();
            this.allowFlight = player.getAllowFlight();
            this.flying = player.isFlying();
            this.fireTicks = player.getFireTicks();
        }

        private static PlayerSnapshot capture(Player player) {
            return new PlayerSnapshot(player);
        }

        private void applyBaseState(Player player) {
            player.closeInventory();
            player.setGameMode(gameMode);
            player.setAllowFlight(allowFlight);
            player.setFlying(allowFlight && flying);
            player.setInvulnerable(false);
            player.setFireTicks(fireTicks);
            player.setFallDistance(0.0f);
            player.setFoodLevel(food);
            player.setSaturation(saturation);
            player.setExhaustion(exhaustion);
            player.setLevel(level);
            player.setExp(exp);
            player.setTotalExperience(totalExp);
            for (PotionEffect current : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(current.getType());
            }
            for (PotionEffect effect : potionEffects) {
                player.addPotionEffect(effect);
            }
            player.teleport(location);
            player.setHealth(Math.min(health, player.getMaxHealth()));
        }

        private void applyInventory(Player player) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setStorageContents(cloneItems(storage));
            inventory.setArmorContents(cloneItems(armor));
            inventory.setItemInOffHand(cloneItem(offHand));
            player.updateInventory();
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            ItemStack[] result = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                result[i] = cloneItem(items[i]);
            }
            return result;
        }

        private static ItemStack cloneItem(ItemStack item) {
            return item == null ? null : item.clone();
        }
    }
}
