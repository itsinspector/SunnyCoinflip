package org.ItsInspector.sunnyCoinflip.managers;

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
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.EconomyResponse;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.BedfightCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.NumberParser;
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
import org.bukkit.block.data.type.Bed.Part;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

public final class BedfightManager {
    private static final String PREFIX = "§8[§bBedWars§8] §r";
    private final SunnyCoinflip plugin;
    private final Map<UUID, BedfightCoinflip> waitingByCreator = new LinkedHashMap();
    private final Map<UUID, PlayerSnapshot> waitingSnapshots = new HashMap();
    private final Set<UUID> awaitingCreateAmount = new HashSet();
    private final Map<UUID, PlayerSnapshot> pendingRestores = new HashMap();
    private final Set<UUID> restoringPlayers = new HashSet();
    private final Map<UUID, LastHit> lastHits = new HashMap();
    private volatile ActiveRound activeRound;

    public BedfightManager(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return this.plugin.getConfig().getBoolean("bedwars.enabled", true);
    }

    public boolean isParticipant(UUID playerId) {
        return this.waitingByCreator.containsKey(playerId) || this.isActiveParticipant(playerId);
    }

    public boolean isActiveParticipant(UUID playerId) {
        return this.activeRound != null && this.activeRound.match.includes(playerId);
    }

    public BedfightCoinflip getActiveMatch(UUID playerId) {
        return this.isActiveParticipant(playerId) ? this.activeRound.match : null;
    }

    public boolean isPlaying() {
        ActiveRound round = this.activeRound;
        return round != null && round.playing && !round.finishing;
    }

    public boolean isAvailable() {
        return this.isEnabled() && this.activeRound == null && this.isArenaConfigured();
    }

    public boolean isArenaConfigured() {
        Location first = this.getFirstPosition();
        Location opponent = this.getOpponentPosition();
        Location firstBed = this.getFirstBedPosition();
        Location opponentBed = this.getOpponentBedPosition();
        if (first != null && opponent != null && firstBed != null && opponentBed != null && first.getWorld() != null && opponent.getWorld() != null && firstBed.getWorld() != null && opponentBed.getWorld() != null) {
            UUID worldId = first.getWorld().getUID();
            if (worldId.equals(opponent.getWorld().getUID()) && worldId.equals(firstBed.getWorld().getUID()) && worldId.equals(opponentBed.getWorld().getUID())) {
                return firstBed.getBlockX() != opponentBed.getBlockX() || firstBed.getBlockY() != opponentBed.getBlockY() || firstBed.getBlockZ() != opponentBed.getBlockZ();
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean isArenaWorld(World world) {
        if (world == null) {
            return false;
        } else {
            Location first = this.getFirstPosition();
            Location opponent = this.getOpponentPosition();
            return this.isSameWorld(first, world) || this.isSameWorld(opponent, world);
        }
    }

    public void handleSimpleCommand(Player player) {
        this.purgeExpiredChallenges();
        if (!this.isEnabled()) {
            player.sendMessage("§8[§bBedWars§8] §r§cLa modalità è disabilitata.");
        } else {
            ActiveRound round = this.activeRound;
            if (round != null) {
                if (round.match.includes(player.getUniqueId())) {
                    player.sendMessage("§8[§bBedWars§8] §r§eSei già dentro questa partita.");
                } else {
                    this.startSpectating(player);
                }
            } else {
                BedfightCoinflip waiting = (BedfightCoinflip)this.waitingByCreator.values().stream().findFirst().orElse((Object)null);
                if (waiting != null) {
                    if (waiting.getCreator().equals(player.getUniqueId())) {
                        player.sendMessage("§8[§bBedWars§8] §r§eStai già aspettando un opponent.");
                    } else {
                        this.acceptChallenge(player, waiting.getCreatorName());
                    }
                } else if (this.isArenaReady(player)) {
                    this.awaitingCreateAmount.add(player.getUniqueId());
                    player.sendMessage("§8[§bBedWars§8] §r§eScrivi in chat la somma da scommettere, oppure §ccancel §eper annullare.");
                }
            }
        }
    }

    public boolean isAwaitingCreateAmount(UUID playerId) {
        return this.awaitingCreateAmount.contains(playerId);
    }

    public void handleCreateAmountChat(Player player, String message) {
        if (this.awaitingCreateAmount.remove(player.getUniqueId())) {
            if (!message.equalsIgnoreCase("cancel") && !message.equalsIgnoreCase("annulla")) {
                try {
                    double amount = NumberParser.parseNumber(message);
                    this.createChallenge(player, amount);
                } catch (IllegalArgumentException exception) {
                    player.sendMessage("§8[§bBedWars§8] §r§c" + exception.getMessage());
                    player.sendMessage("§8[§bBedWars§8] §r§7Usa di nuovo §e/cf bedwars §7per riprovare.");
                }

            } else {
                player.sendMessage("§8[§bBedWars§8] §r§7Creazione annullata.");
            }
        }
    }

    public void startSpectating(Player player) {
        ActiveRound round = this.activeRound;
        if (round != null && !round.finishing) {
            if (round.match.includes(player.getUniqueId())) {
                player.sendMessage("§8[§bBedWars§8] §r§cSei un partecipante della partita.");
            } else if (round.spectators.containsKey(player.getUniqueId())) {
                player.teleport(this.getSpectatorLocation(round));
            } else {
                round.spectators.put(player.getUniqueId(), BedfightManager.PlayerSnapshot.capture(player));
                player.closeInventory();
                player.teleport(this.getSpectatorLocation(round));
                this.forceSpectatorMode(player, round);
                String var10001 = round.match.getCreatorName();
                player.sendMessage("§8[§bBedWars§8] §r§aStai spectando §f" + var10001 + " §7vs §f" + round.match.getOpponentName() + "§a.");
            }
        } else {
            player.sendMessage("§8[§bBedWars§8] §r§cNon c'è una partita da spectare.");
        }
    }

    private void forceSpectatorMode(Player player, ActiveRound expectedRound) {
        this.applySpectatorMode(player);
        long[] retryDelays = new long[]{1L, 3L, 10L};

        for(long delay : retryDelays) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (player.isOnline() && this.activeRound == expectedRound && !expectedRound.finishing) {
                    boolean isMatchPlayer = expectedRound.match.includes(player.getUniqueId());
                    boolean shouldSpectate = expectedRound.spectators.containsKey(player.getUniqueId()) || expectedRound.respawning.contains(player.getUniqueId());
                    if (isMatchPlayer || expectedRound.spectators.containsKey(player.getUniqueId())) {
                        if (shouldSpectate && player.getGameMode() != GameMode.SPECTATOR) {
                            this.applySpectatorMode(player);
                        }

                    }
                }
            }, delay);
        }

    }

    private void forceRespawnSpectatorMode(Player player, ActiveRound expectedRound) {
        this.applySpectatorMode(player);
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (player.isOnline() && this.activeRound == expectedRound && !expectedRound.finishing && expectedRound.respawning.contains(player.getUniqueId())) {
                if (player.getGameMode() != GameMode.SPECTATOR || !player.getAllowFlight() || !player.isFlying()) {
                    this.applySpectatorMode(player);
                }

            } else {
                task[0].cancel();
            }
        }, 1L, 1L);
    }

    private void applySpectatorMode(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0.0F);
    }

    private Location getSpectatorLocation(ActiveRound round) {
        Location location = round.firstSpawn.clone().add(round.opponentSpawn).multiply((double)0.5F);
        location.setWorld(round.world);
        location.setY(Math.max(round.firstSpawn.getY(), round.opponentSpawn.getY()) + (double)8.0F);
        return location;
    }

    public Collection<BedfightCoinflip> getWaitingChallenges() {
        this.purgeExpiredChallenges();
        return new ArrayList(this.waitingByCreator.values());
    }

    public void createChallenge(Player creator, double amount) {
        this.purgeExpiredChallenges();
        if (!this.isEnabled()) {
            creator.sendMessage("§8[§bBedWars§8] §r§cLa modalità è disabilitata.");
        } else if (this.isArenaReady(creator)) {
            if (!(amount <= (double)0.0F) && !(amount > this.plugin.getGameManager().getMaxAmount())) {
                if (this.isParticipant(creator.getUniqueId())) {
                    creator.sendMessage("§8[§bBedWars§8] §r§cHai già una sfida BedWars in attesa o in corso.");
                } else if (this.isBusyInOtherMode(creator.getUniqueId())) {
                    creator.sendMessage("§8[§bBedWars§8] §r§cSei già impegnato in un altro coinflip.");
                } else if (SunnyCoinflip.getEconomy().getBalance(creator) < amount) {
                    creator.sendMessage("§8[§bBedWars§8] §r§cNon hai abbastanza soldi.");
                } else {
                    BedfightCoinflip challenge = new BedfightCoinflip(creator.getUniqueId(), creator.getName(), amount);
                    this.waitingByCreator.put(creator.getUniqueId(), challenge);
                    this.waitingSnapshots.put(creator.getUniqueId(), BedfightManager.PlayerSnapshot.capture(creator));
                    Location waitingSpawn = this.getFirstPosition();
                    creator.closeInventory();
                    creator.setGameMode(GameMode.SURVIVAL);
                    creator.setAllowFlight(false);
                    creator.setFlying(false);
                    creator.setInvulnerable(true);
                    this.clearPotionEffects(creator);
                    this.resetCombatState(creator);
                    this.giveKit(creator, BedfightManager.Team.FIRST);
                    if (waitingSpawn != null) {
                        creator.teleport(waitingSpawn);
                    }

                    String var6 = this.formatMoney(amount);
                    creator.sendMessage("§8[§bBedWars§8] §r§aCoinflip creato per §f\ue0d8 §e" + var6 + "§a.");
                    String var10000 = creator.getName();
                    Bukkit.broadcastMessage("§b§lBEDWARS CF! §f" + var10000 + " §7ha creato una sfida da §f\ue0d8 §e" + this.formatMoney(amount) + "§7. §e/cf bedwars accept " + creator.getName());
                }
            } else {
                Locale var10001 = Locale.US;
                Object[] var10003 = new Object[]{this.plugin.getGameManager().getMaxAmount()};
                creator.sendMessage("§8[§bBedWars§8] §r§cL'importo deve essere tra 1 e " + String.format(var10001, "%.0f", var10003) + ".");
            }
        }
    }

    public void listChallenges(CommandSender viewer) {
        this.purgeExpiredChallenges();
        if (this.waitingByCreator.isEmpty()) {
            viewer.sendMessage("§8[§bBedWars§8] §r§7Non ci sono sfide in attesa.");
        } else {
            viewer.sendMessage("§b§lCoinflip BedWars in attesa:");

            for(BedfightCoinflip challenge : this.waitingByCreator.values()) {
                String var10001 = challenge.getCreatorName();
                viewer.sendMessage("§8- §f" + var10001 + " §7• §f\ue0d8 §e" + this.formatMoney(challenge.getAmount()) + " §7• §e/cf bedwars accept " + challenge.getCreatorName());
            }

        }
    }

    public void acceptChallenge(Player opponent, String creatorName) {
        this.purgeExpiredChallenges();
        if (!this.isEnabled()) {
            opponent.sendMessage("§8[§bBedWars§8] §r§cLa modalità è disabilitata.");
        } else if (this.activeRound != null) {
            opponent.sendMessage("§8[§bBedWars§8] §r§cL'arena è già occupata.");
        } else if (this.isArenaReady(opponent)) {
            BedfightCoinflip challenge = this.findWaitingByName(creatorName);
            if (challenge == null) {
                opponent.sendMessage("§8[§bBedWars§8] §r§cNessuna sfida trovata per " + creatorName + ".");
            } else if (challenge.getCreator().equals(opponent.getUniqueId())) {
                opponent.sendMessage("§8[§bBedWars§8] §r§cNon puoi accettare la tua sfida.");
            } else if (this.isParticipant(opponent.getUniqueId())) {
                opponent.sendMessage("§8[§bBedWars§8] §r§cHai già una sfida in attesa o in corso.");
            } else {
                Player creator = Bukkit.getPlayer(challenge.getCreator());
                if (creator != null && creator.isOnline()) {
                    if (!this.isBusyInOtherMode(creator.getUniqueId()) && !this.isBusyInOtherMode(opponent.getUniqueId())) {
                        double amount = challenge.getAmount();
                        if (SunnyCoinflip.getEconomy().getBalance(creator) < amount) {
                            this.waitingByCreator.remove(challenge.getCreator());
                            this.restoreWaitingCreator(challenge.getCreator());
                            creator.sendMessage("§8[§bBedWars§8] §r§cSfida rimossa: saldo insufficiente.");
                            opponent.sendMessage("§8[§bBedWars§8] §r§cIl creatore non ha più abbastanza soldi.");
                        } else if (SunnyCoinflip.getEconomy().getBalance(opponent) < amount) {
                            opponent.sendMessage("§8[§bBedWars§8] §r§cNon hai abbastanza soldi.");
                        } else {
                            Location firstSpawn = this.getFirstPosition();
                            Location opponentSpawn = this.getOpponentPosition();
                            World world = firstSpawn == null ? null : firstSpawn.getWorld();
                            if (world != null && opponentSpawn != null && opponentSpawn.getWorld() != null && world.getUID().equals(opponentSpawn.getWorld().getUID())) {
                                Set<BlockKey> firstBed = this.resolveConfiguredOrNearbyBed(this.getFirstBedPosition(), firstSpawn, this.getBedSearchRadius());
                                Set<BlockKey> opponentBed = this.resolveConfiguredOrNearbyBed(this.getOpponentBedPosition(), opponentSpawn, this.getBedSearchRadius());
                                if (!firstBed.isEmpty() && !opponentBed.isEmpty()) {
                                    Set<BlockKey> overlappingBedBlocks = new HashSet(firstBed);
                                    overlappingBedBlocks.retainAll(opponentBed);
                                    if (!overlappingBedBlocks.isEmpty()) {
                                        opponent.sendMessage("§8[§bBedWars§8] §r§cI letti First e Opponent coincidono. Riconfigurali separatamente.");
                                        creator.sendMessage("§8[§bBedWars§8] §r§cConfigurazione letti non valida: i due letti coincidono.");
                                    } else {
                                        challenge.setOpponent(opponent.getUniqueId(), opponent.getName());
                                        challenge.setState(BedfightCoinflip.State.COUNTDOWN);
                                        EconomyResponse creatorWithdraw = SunnyCoinflip.getEconomy().withdrawPlayer(creator, amount);
                                        if (!creatorWithdraw.transactionSuccess()) {
                                            this.resetWaitingChallenge(challenge);
                                            opponent.sendMessage("§8[§bBedWars§8] §r§cImpossibile prelevare la puntata del creatore.");
                                        } else {
                                            EconomyResponse opponentWithdraw = SunnyCoinflip.getEconomy().withdrawPlayer(opponent, amount);
                                            if (!opponentWithdraw.transactionSuccess()) {
                                                SunnyCoinflip.getEconomy().depositPlayer(creator, amount);
                                                this.resetWaitingChallenge(challenge);
                                                opponent.sendMessage("§8[§bBedWars§8] §r§cImpossibile prelevare la tua puntata.");
                                            } else {
                                                this.waitingByCreator.remove(challenge.getCreator());
                                                ActiveRound round = new ActiveRound(challenge, world, firstSpawn.clone(), opponentSpawn.clone(), firstBed, opponentBed);
                                                PlayerSnapshot creatorSnapshot = (PlayerSnapshot)this.waitingSnapshots.remove(creator.getUniqueId());
                                                round.snapshots.put(creator.getUniqueId(), creatorSnapshot != null ? creatorSnapshot : BedfightManager.PlayerSnapshot.capture(creator));
                                                round.snapshots.put(opponent.getUniqueId(), BedfightManager.PlayerSnapshot.capture(opponent));

                                                for(Entity entity : world.getEntities()) {
                                                    round.initialEntities.add(entity.getUniqueId());
                                                }

                                                this.captureBedBlocks(round, firstBed);
                                                this.captureBedBlocks(round, opponentBed);
                                                this.activeRound = round;
                                                if (this.prepareFighter(creator, firstSpawn, BedfightManager.Team.FIRST) && this.prepareFighter(opponent, opponentSpawn, BedfightManager.Team.OPPONENT)) {
                                                    creator.sendMessage("§8[§bBedWars§8] §r§a" + opponent.getName() + " ha accettato la tua sfida.");
                                                    opponent.sendMessage("§8[§bBedWars§8] §r§aHai accettato la sfida di " + creator.getName() + ".");
                                                    String var10000 = creator.getName();
                                                    Bukkit.broadcastMessage("§b§lBEDWARS CF! §f" + var10000 + " §7vs §f" + opponent.getName() + " §7per §f\ue0d8 §e" + this.formatMoney(amount) + "§7.");
                                                    this.startCountdown(round);
                                                } else {
                                                    this.abortActiveRound("§cAvvio fallito: puntate rimborsate.", true);
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    opponent.sendMessage("§8[§bBedWars§8] §r§cImpossibile trovare entrambi i letti. Usa setfirstbed e setopponentbed.");
                                    creator.sendMessage("§8[§bBedWars§8] §r§cConfigurazione letti incompleta. Avvisa un amministratore.");
                                }
                            } else {
                                opponent.sendMessage("§8[§bBedWars§8] §r§cLe due posizioni dell'arena non sono valide o non sono nello stesso mondo.");
                            }
                        }
                    } else {
                        opponent.sendMessage("§8[§bBedWars§8] §r§cUno dei due giocatori è già impegnato in un altro coinflip.");
                    }
                } else {
                    this.waitingByCreator.remove(challenge.getCreator());
                    this.waitingSnapshots.remove(challenge.getCreator());
                    opponent.sendMessage("§8[§bBedWars§8] §r§cIl creatore non è più online.");
                }
            }
        }
    }

    public void cancelWaiting(Player creator) {
        BedfightCoinflip removed = (BedfightCoinflip)this.waitingByCreator.remove(creator.getUniqueId());
        if (removed == null) {
            creator.sendMessage("§8[§bBedWars§8] §r§cNon hai una sfida in attesa.");
        } else {
            removed.setState(BedfightCoinflip.State.FINISHED);
            this.restoreWaitingCreator(creator.getUniqueId());
            creator.sendMessage("§8[§bBedWars§8] §r§aSfida annullata.");
        }
    }

    public void showStatus(CommandSender sender) {
        if (this.activeRound == null) {
            sender.sendMessage("§8[§bBedWars§8] §r§7Nessun round attivo.");
        } else {
            BedfightCoinflip match = this.activeRound.match;
            String var10001 = match.getCreatorName();
            sender.sendMessage("§8[§bBedWars§8] §r§f" + var10001 + " §7vs §f" + match.getOpponentName());
            var10001 = this.bedStatus(match.isFirstBedAlive());
            sender.sendMessage("§8[§bBedWars§8] §r§9First: " + var10001 + " §8| §cOpponent: " + this.bedStatus(match.isOpponentBedAlive()));
        }
    }

    public void abortByAdmin(CommandSender sender) {
        if (this.activeRound == null) {
            sender.sendMessage("§8[§bBedWars§8] §r§cNessun round attivo.");
        } else {
            this.abortActiveRound("§eRound annullato da un amministratore; puntata rimborsata.", true);
            sender.sendMessage("§8[§bBedWars§8] §r§aRound annullato.");
        }
    }

    public void setFirstPosition(Player player) {
        this.setLocation("bedwars.first-position", player.getLocation());
        player.sendMessage("§8[§bBedWars§8] §r§aPosizione First impostata.");
    }

    public void setOpponentPosition(Player player) {
        this.setLocation("bedwars.opponent-position", player.getLocation());
        player.sendMessage("§8[§bBedWars§8] §r§aPosizione Opponent impostata.");
    }

    public void setFirstBed(Player player) {
        this.setBedFromTarget(player, "bedwars.first-bed", "First");
    }

    public void setOpponentBed(Player player) {
        this.setBedFromTarget(player, "bedwars.opponent-bed", "Opponent");
    }

    public Location getFirstPosition() {
        return this.plugin.getConfig().getLocation("bedwars.first-position");
    }

    public Location getOpponentPosition() {
        return this.plugin.getConfig().getLocation("bedwars.opponent-position");
    }

    public Location getFirstBedPosition() {
        return this.plugin.getConfig().getLocation("bedwars.first-bed");
    }

    public Location getOpponentBedPosition() {
        return this.plugin.getConfig().getLocation("bedwars.opponent-bed");
    }

    public void placeBet(Player bettor, String targetName, double amount) {
        ActiveRound round = this.activeRound;
        if (round != null && !round.finishing) {
            if (round.match.includes(bettor.getUniqueId())) {
                bettor.sendMessage("§8[§bBedWars§8] §r§cI partecipanti non possono scommettere sulla propria partita.");
            } else if (!(amount <= (double)0.0F) && !(amount > this.plugin.getGameManager().getMaxAmount())) {
                if (round.bets.containsKey(bettor.getUniqueId())) {
                    bettor.sendMessage("§8[§bBedWars§8] §r§cHai già piazzato una scommessa in questo round.");
                } else {
                    UUID selected;
                    if (!targetName.equalsIgnoreCase("first") && !targetName.equalsIgnoreCase("blu") && !targetName.equalsIgnoreCase(round.match.getCreatorName())) {
                        if (!targetName.equalsIgnoreCase("opponent") && !targetName.equalsIgnoreCase("rosso") && !targetName.equalsIgnoreCase(round.match.getOpponentName())) {
                            String var9 = round.match.getCreatorName();
                            bettor.sendMessage("§8[§bBedWars§8] §r§cGiocatore non valido. Scegli §f" + var9 + " §co §f" + round.match.getOpponentName() + "§c.");
                            return;
                        }

                        selected = round.match.getOpponent();
                    } else {
                        selected = round.match.getCreator();
                    }

                    if (SunnyCoinflip.getEconomy().getBalance(bettor) < amount) {
                        bettor.sendMessage("§8[§bBedWars§8] §r§cNon hai abbastanza soldi.");
                    } else {
                        EconomyResponse withdraw = SunnyCoinflip.getEconomy().withdrawPlayer(bettor, amount);
                        if (!withdraw.transactionSuccess()) {
                            bettor.sendMessage("§8[§bBedWars§8] §r§cImpossibile prelevare la scommessa.");
                        } else {
                            round.bets.put(bettor.getUniqueId(), new SpectatorBet(bettor.getUniqueId(), bettor.getName(), selected, amount));
                            String var8 = this.formatMoney(amount);
                            bettor.sendMessage("§8[§bBedWars§8] §r§aHai scommesso §f\ue0d8 §e" + var8 + " §asu §f" + round.match.getName(selected) + "§a.");
                        }
                    }
                }
            } else {
                Locale var10001 = Locale.US;
                Object[] var10003 = new Object[]{this.plugin.getGameManager().getMaxAmount()};
                bettor.sendMessage("§8[§bBedWars§8] §r§cL'importo deve essere tra 1 e " + String.format(var10001, "%.0f", var10003) + ".");
            }
        } else {
            bettor.sendMessage("§8[§bBedWars§8] §r§cNon c'è una partita BedWars su cui scommettere.");
        }
    }

    public void handleQuit(Player player) {
        BedfightCoinflip waiting = (BedfightCoinflip)this.waitingByCreator.remove(player.getUniqueId());
        if (waiting != null) {
            waiting.setState(BedfightCoinflip.State.FINISHED);
            this.waitingSnapshots.remove(player.getUniqueId());
        }

        this.awaitingCreateAmount.remove(player.getUniqueId());
        ActiveRound spectatorRound = this.activeRound;
        if (spectatorRound != null) {
            spectatorRound.spectators.remove(player.getUniqueId());
        }

        ActiveRound round = this.activeRound;
        if (this.isActiveParticipant(player.getUniqueId()) && round != null) {
            UUID quitterId = player.getUniqueId();
            UUID winner = round.match.getOtherParticipant(quitterId);
            if (!round.playing) {
                this.refundPreStartQuitBets(round, quitterId);
                this.abortActiveRound("§eRound annullato: un partecipante ha abbandonato prima del VIA.", false);
            } else {
                if (winner != null) {
                    this.finishRound(winner, true);
                } else {
                    this.abortActiveRound("§eRound annullato; puntata rimborsata.", true);
                }

            }
        }
    }

    public void handleDeath(Player player) {
        if (this.isActiveParticipant(player.getUniqueId()) && this.activeRound != null && !this.activeRound.finishing) {
            Team team = this.teamOf(player.getUniqueId());
            boolean bedAlive = this.isBedAlive(this.activeRound, team);
            if (!bedAlive && this.activeRound.playing) {
                UUID winner = this.activeRound.match.getOtherParticipant(player.getUniqueId());
                if (winner != null) {
                    this.finishRound(winner, false);
                }

            } else {
                this.activeRound.respawning.add(player.getUniqueId());
                player.sendMessage("§8[§bBedWars§8] §r§eRientrerai in partita tra 3 secondi...");
                this.playSound(player, Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                    if (player.isOnline() && player.isDead()) {
                        player.spigot().respawn();
                    }

                }, this.getRespawnDelayTicks());
            }
        }
    }

    public boolean handlePotentialElimination(Player player, double finalDamage, EntityDamageEvent.DamageCause cause) {
        ActiveRound round = this.activeRound;
        if (round != null && round.playing && !round.finishing && this.isActiveParticipant(player.getUniqueId()) && !round.respawning.contains(player.getUniqueId())) {
            if (player.getHealth() - Math.max((double)0.0F, finalDamage) > (double)1.0F) {
                return false;
            } else {
                this.announceCustomDeath(player, cause);
                this.eliminateTemporarily(player, "§cSei stato eliminato!");
                return true;
            }
        } else {
            return false;
        }
    }

    public void recordLastDamager(Player victim, Player attacker) {
        if (victim != null && attacker != null && !victim.getUniqueId().equals(attacker.getUniqueId()) && this.isActiveParticipant(victim.getUniqueId()) && this.isActiveParticipant(attacker.getUniqueId())) {
            this.lastHits.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), System.currentTimeMillis()));
        }
    }

    public void handleVoidLevel(Player player, Location destination) {
        ActiveRound round = this.activeRound;
        if (destination != null && round != null && round.playing && !round.finishing && this.isActiveParticipant(player.getUniqueId()) && !round.respawning.contains(player.getUniqueId())) {
            double voidY = this.plugin.getConfig().getDouble("bedwars.void-y", (double)43.0F);
            if (destination.getY() <= voidY) {
                this.announceCustomDeath(player, DamageCause.VOID);
                this.eliminateTemporarily(player, "§cSei caduto nel vuoto!");
            }

        }
    }

    private void eliminateTemporarily(Player player, String title) {
        ActiveRound round = this.activeRound;
        if (round != null && !round.finishing && round.respawning.add(player.getUniqueId())) {
            Team team = this.teamOf(player.getUniqueId());
            if (!this.isBedAlive(round, team)) {
                UUID winner = round.match.getOtherParticipant(player.getUniqueId());
                if (winner != null) {
                    this.finishRound(winner, false);
                }

            } else {
                player.setInvulnerable(true);
                this.resetCombatState(player);
                player.teleport(team == BedfightManager.Team.FIRST ? round.firstSpawn : round.opponentSpawn);
                this.forceRespawnSpectatorMode(player, round);
                this.playSound(player, Sound.ENTITY_VILLAGER_NO, 1.0F, 0.8F);
                this.startRespawnTitleCountdown(round, player, team, title);
            }
        }
    }

    private void startRespawnTitleCountdown(ActiveRound round, Player player, Team team, String deathTitle) {
        long totalTicks = Math.max(20L, this.getRespawnDelayTicks());
        int totalSeconds = Math.max(1, (int)Math.ceil((double)totalTicks / (double)20.0F));
        int[] remaining = new int[]{totalSeconds};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (this.activeRound == round && !round.finishing && player.isOnline() && round.respawning.contains(player.getUniqueId())) {
                if (remaining[0] <= 0) {
                    task[0].cancel();
                    this.respawnAfterElimination(round, player, team);
                } else {
                    player.sendTitle(deathTitle, "§eRespawn tra §f" + remaining[0] + "§e...", 0, 22, 0);
                    this.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F, 1.0F + (float)(totalSeconds - remaining[0]) * 0.15F);
                    int var10002 = remaining[0]--;
                }
            } else {
                task[0].cancel();
            }
        }, 0L, 20L);
    }

    private void respawnAfterElimination(ActiveRound round, Player player, Team team) {
        if (this.activeRound == round && !round.finishing && player.isOnline()) {
            if (!this.isBedAlive(round, team)) {
                UUID winner = round.match.getOtherParticipant(player.getUniqueId());
                if (winner != null) {
                    this.finishRound(winner, false);
                }

            } else {
                round.respawning.remove(player.getUniqueId());
                player.setGameMode(GameMode.SURVIVAL);
                player.setInvulnerable(false);
                this.resetCombatState(player);
                player.teleport(team == BedfightManager.Team.FIRST ? round.firstSpawn : round.opponentSpawn);
                this.giveKit(player, team);
                this.applySpawnProtection(round, player);
                player.sendTitle("§aRESPAWN!", "§73 secondi di protezione", 0, 25, 5);
                this.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.5F);
            }
        }
    }

    public Location getRespawnLocation(UUID playerId) {
        PlayerSnapshot pending = (PlayerSnapshot)this.pendingRestores.get(playerId);
        if (pending != null) {
            return pending.location.clone();
        } else if (this.isActiveParticipant(playerId) && this.activeRound != null) {
            return this.teamOf(playerId) == BedfightManager.Team.FIRST ? this.activeRound.firstSpawn.clone() : this.activeRound.opponentSpawn.clone();
        } else {
            return null;
        }
    }

    public void handleRespawn(Player player) {
        PlayerSnapshot restore = (PlayerSnapshot)this.pendingRestores.remove(player.getUniqueId());
        if (restore != null) {
            Bukkit.getScheduler().runTask(this.plugin, () -> this.applySnapshotSafely(player, restore));
        } else {
            ActiveRound round = this.activeRound;
            if (this.isActiveParticipant(player.getUniqueId()) && round != null && !round.finishing) {
                Team team = this.teamOf(player.getUniqueId());
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (this.activeRound == round && !round.finishing) {
                        round.respawning.remove(player.getUniqueId());
                        player.setGameMode(GameMode.SURVIVAL);
                        player.setInvulnerable(false);
                        this.resetCombatState(player);
                        player.teleport(team == BedfightManager.Team.FIRST ? round.firstSpawn : round.opponentSpawn);
                        this.giveKit(player, team);
                        this.applySpawnProtection(round, player);
                    }
                });
            }
        }
    }

    public void handleChangedWorld(Player player) {
        UUID playerId = player.getUniqueId();
        if (!this.restoringPlayers.contains(playerId)) {
            PlayerSnapshot pending = (PlayerSnapshot)this.pendingRestores.remove(playerId);
            if (pending != null) {
                this.applySnapshotSafely(player, pending);
            } else {
                ActiveRound round = this.activeRound;
                if (this.isActiveParticipant(playerId) && round != null) {
                    if (player.getWorld().getUID().equals(round.world.getUID())) {
                        this.giveKit(player, this.teamOf(playerId));
                    } else {
                        UUID winner = round.match.getOtherParticipant(playerId);
                        if (winner != null) {
                            this.finishRound(winner, true);
                        } else {
                            this.abortActiveRound("§eRound annullato; puntate rimborsate.", true);
                        }

                    }
                }
            }
        }
    }

    public void handleJoin(Player player) {
        PlayerSnapshot pending = (PlayerSnapshot)this.pendingRestores.remove(player.getUniqueId());
        if (pending != null) {
            Bukkit.getScheduler().runTask(this.plugin, () -> this.applySnapshotSafely(player, pending));
        }

    }

    public boolean canMoveDuringCountdown(Player player, Location from, Location to) {
        if (to == null) {
            return true;
        } else {
            boolean waitingFirst = this.waitingByCreator.containsKey(player.getUniqueId());
            boolean countdownParticipant = this.isActiveParticipant(player.getUniqueId()) && this.activeRound != null && !this.activeRound.playing;
            if (!waitingFirst && !countdownParticipant) {
                return true;
            } else {
                return from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ();
            }
        }
    }

    public boolean canLeaveArena(Player player, Location destination) {
        return this.isActiveParticipant(player.getUniqueId()) && this.activeRound != null && destination != null && destination.getWorld() != null ? destination.getWorld().getUID().equals(this.activeRound.world.getUID()) : true;
    }

    public boolean handleBlockPlace(Player player, Block block, BlockData replacedData) {
        if (this.activeRound != null && block.getWorld().getUID().equals(this.activeRound.world.getUID())) {
            if (this.isActiveParticipant(player.getUniqueId()) && this.activeRound.playing) {
                int maxHeight = this.getMaxBuildHeight();
                if (block.getY() > maxHeight) {
                    player.sendMessage("§8[§bBedWars§8] §r§cNon puoi piazzare blocchi sopra Y=" + maxHeight + ".");
                    return false;
                } else {
                    BlockKey key = BedfightManager.BlockKey.of(block);
                    this.activeRound.originalBlocks.putIfAbsent(key, replacedData.clone());
                    return true;
                }
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    public BreakResult handleBlockBreak(Player player, Block block) {
        if (this.activeRound != null && block.getWorld().getUID().equals(this.activeRound.world.getUID())) {
            if (this.isActiveParticipant(player.getUniqueId()) && this.activeRound.playing) {
                BlockKey key = BedfightManager.BlockKey.of(block);
                Team team = this.teamOf(player.getUniqueId());
                Set<BlockKey> ownBed = team == BedfightManager.Team.FIRST ? this.activeRound.firstBed : this.activeRound.opponentBed;
                Set<BlockKey> enemyBed = team == BedfightManager.Team.FIRST ? this.activeRound.opponentBed : this.activeRound.firstBed;
                if (ownBed.contains(key)) {
                    player.sendMessage("§8[§bBedWars§8] §r§cNon puoi rompere il tuo letto.");
                    return BedfightManager.BreakResult.DENY;
                } else if (enemyBed.contains(key)) {
                    this.captureBedBlocks(this.activeRound, enemyBed);
                    if (team == BedfightManager.Team.FIRST) {
                        this.activeRound.match.setOpponentBedAlive(false);
                    } else {
                        this.activeRound.match.setFirstBedAlive(false);
                    }

                    this.announceBedDestroyed(player, team == BedfightManager.Team.FIRST ? BedfightManager.Team.OPPONENT : BedfightManager.Team.FIRST);
                    return BedfightManager.BreakResult.BED;
                } else if (this.isBreakableArenaMaterial(block.getType())) {
                    this.activeRound.originalBlocks.putIfAbsent(key, block.getBlockData().clone());
                    return BedfightManager.BreakResult.BREAKABLE_ARENA_BLOCK;
                } else {
                    player.sendMessage("§8[§bBedWars§8] §r§cPuoi rompere solo end stone, legno, lana e il letto avversario.");
                    return BedfightManager.BreakResult.DENY;
                }
            } else {
                return BedfightManager.BreakResult.DENY;
            }
        } else {
            return BedfightManager.BreakResult.ALLOW;
        }
    }

    private boolean isBreakableArenaMaterial(Material material) {
        if (material != Material.END_STONE && !material.name().endsWith("_WOOL")) {
            String name = material.name();
            return name.endsWith("_PLANKS") || name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE") || name.equals("BAMBOO_BLOCK") || name.equals("STRIPPED_BAMBOO_BLOCK") || name.equals("BAMBOO_MOSAIC");
        } else {
            return true;
        }
    }

    public boolean isRoundWorld(World world) {
        return this.activeRound != null && world != null && world.getUID().equals(this.activeRound.world.getUID());
    }

    public boolean canDamage(Player attacker, Player victim) {
        ActiveRound round = this.activeRound;
        if (round == null) {
            return true;
        } else {
            boolean attackerIn = attacker != null && this.isActiveParticipant(attacker.getUniqueId());
            boolean victimIn = victim != null && this.isActiveParticipant(victim.getUniqueId());
            if (!attackerIn && !victimIn) {
                return true;
            } else if (round.playing && attackerIn && victimIn && !attacker.getUniqueId().equals(victim.getUniqueId())) {
                if (!round.respawning.contains(attacker.getUniqueId()) && !round.respawning.contains(victim.getUniqueId())) {
                    if (this.hasSpawnProtection(round, attacker.getUniqueId())) {
                        this.removeSpawnProtection(round, attacker, true);
                    }

                    return !this.hasSpawnProtection(round, victim.getUniqueId());
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    public boolean canTakeDamage(Player victim) {
        ActiveRound round = this.activeRound;
        if (!this.isActiveParticipant(victim.getUniqueId())) {
            return true;
        } else {
            return round != null && round.playing && !round.respawning.contains(victim.getUniqueId()) && !this.hasSpawnProtection(round, victim.getUniqueId());
        }
    }

    public void handleShutdown() {
        if (this.activeRound != null) {
            this.abortActiveRound("§eServer in arresto; puntata rimborsata.", true);
        }

        for(UUID creatorId : new ArrayList(this.waitingSnapshots.keySet())) {
            this.restoreWaitingCreator(creatorId);
        }

        this.awaitingCreateAmount.clear();
        this.waitingByCreator.clear();
    }

    private void startCountdown(ActiveRound round) {
        int configured = Math.max(0, this.plugin.getConfig().getInt("bedwars.countdown", 5));
        if (configured == 0) {
            this.beginPlaying(round);
        } else {
            int[] seconds = new int[]{configured};
            round.countdownTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
                if (this.activeRound == round && !round.finishing) {
                    Player first = Bukkit.getPlayer(round.match.getCreator());
                    Player opponent = Bukkit.getPlayer(round.match.getOpponent());
                    if (first != null && opponent != null && first.isOnline() && opponent.isOnline()) {
                        if (seconds[0] <= 0) {
                            round.countdownTask.cancel();
                            this.beginPlaying(round);
                        } else {
                            String title = "§b" + seconds[0];
                            first.sendTitle(title, "§7Preparati!", 0, 25, 0);
                            opponent.sendTitle(title, "§7Preparati!", 0, 25, 0);
                            this.playSound(first, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.2F);
                            this.playSound(opponent, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.2F);
                            int var10002 = seconds[0]--;
                        }
                    } else {
                        UUID winner = first != null && first.isOnline() ? first.getUniqueId() : (opponent != null && opponent.isOnline() ? opponent.getUniqueId() : null);
                        if (winner == null) {
                            this.abortActiveRound("§eRound annullato; puntate rimborsate.", true);
                        } else {
                            this.finishRound(winner, true);
                        }

                    }
                } else {
                    if (round.countdownTask != null) {
                        round.countdownTask.cancel();
                    }

                }
            }, 0L, 20L);
        }
    }

    private void beginPlaying(ActiveRound round) {
        if (this.activeRound == round && !round.finishing) {
            round.playing = true;
            round.match.setState(BedfightCoinflip.State.ACTIVE);
            Player first = Bukkit.getPlayer(round.match.getCreator());
            Player opponent = Bukkit.getPlayer(round.match.getOpponent());
            if (first != null) {
                first.setInvulnerable(false);
                this.applySpawnProtection(round, first);
                first.sendTitle("§aVIA!", "§73 secondi di protezione", 0, 30, 10);
                this.playSound(first, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.1F);
            }

            if (opponent != null) {
                opponent.setInvulnerable(false);
                this.applySpawnProtection(round, opponent);
                opponent.sendTitle("§aVIA!", "§73 secondi di protezione", 0, 30, 10);
                this.playSound(opponent, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.1F);
            }

            this.startRoundClock(round);
        }
    }

    private void startRoundClock(ActiveRound round) {
        int bedsDestroyAfter = Math.max(1, this.plugin.getConfig().getInt("bedwars.beds-auto-destroy-seconds", 300));
        int deathmatchAfter = Math.max(bedsDestroyAfter, this.plugin.getConfig().getInt("bedwars.deathmatch-start-seconds", 420));
        double startingDamage = Math.max(0.1, this.plugin.getConfig().getDouble("bedwars.deathmatch-starting-damage", (double)1.0F));
        double damageIncrease = Math.max((double)0.0F, this.plugin.getConfig().getDouble("bedwars.deathmatch-damage-increase", (double)1.0F));
        round.roundClockTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (this.activeRound == round && !round.finishing && round.playing) {
                ++round.elapsedSeconds;
                if (!round.bedsAutoDestroyed && round.elapsedSeconds >= bedsDestroyAfter) {
                    round.bedsAutoDestroyed = true;
                    this.autoDestroyBothBeds(round);
                }

                if (round.elapsedSeconds >= deathmatchAfter) {
                    int deathmatchSecond = round.elapsedSeconds - deathmatchAfter + 1;
                    double damage = startingDamage + (double)(deathmatchSecond - 1) * damageIncrease;
                    this.applyDeathmatchDamage(round, damage, deathmatchSecond);
                }

            } else {
                this.cancelRoundClock(round);
            }
        }, 20L, 20L);
    }

    private void autoDestroyBothBeds(ActiveRound round) {
        round.match.setFirstBedAlive(false);
        round.match.setOpponentBedAlive(false);
        this.removeBedBlocks(round, round.firstBed);
        this.removeBedBlocks(round, round.opponentBed);
        this.forEachRoundViewer(round, (player) -> {
            player.sendTitle("§c§lLETTI DISTRUTTI!", "§7Non potete più respawnare", 5, 45, 10);
            player.sendMessage("§8[§bBedWars§8] §r§cTempo scaduto: entrambi i letti si sono autodistrutti!");
            this.playSound(player, Sound.ENTITY_WITHER_SPAWN, 0.8F, 1.2F);
        });
    }

    private void removeBedBlocks(ActiveRound round, Set<BlockKey> bed) {
        for(BlockKey key : bed) {
            if (round.world.getUID().equals(key.worldId())) {
                round.world.getBlockAt(key.x(), key.y(), key.z()).setType(Material.AIR, false);
            }
        }

    }

    private void applyDeathmatchDamage(ActiveRound round, double damage, int deathmatchSecond) {
        if (!round.deathmatchAnnounced) {
            round.deathmatchAnnounced = true;
            this.forEachRoundViewer(round, (player) -> {
                player.sendTitle("§4§lDEATHMATCH", "§cIl danno aumenta ogni secondo!", 5, 50, 10);
                player.sendMessage("§8[§bBedWars§8] §r§4Deathmatch iniziato: perderete vita ogni secondo.");
                this.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 0.9F);
            });
        }

        this.damageDeathmatchPlayer(round, Bukkit.getPlayer(round.match.getCreator()), damage);
        this.damageDeathmatchPlayer(round, Bukkit.getPlayer(round.match.getOpponent()), damage);
        if (deathmatchSecond % 5 == 0) {
            String hearts = String.format(Locale.US, "%.1f", damage / (double)2.0F);
            this.forEachRoundViewer(round, (player) -> player.sendActionBar("§4DEATHMATCH §8• §cDanno attuale: §f" + hearts + " cuori/s"));
        }

    }

    private void damageDeathmatchPlayer(ActiveRound round, Player player, double damage) {
        if (player != null && player.isOnline() && !round.finishing && !round.respawning.contains(player.getUniqueId()) && player.getGameMode() != GameMode.SPECTATOR) {
            if (player.getHealth() - damage <= (double)1.0F) {
                this.announceCustomDeath(player, DamageCause.CUSTOM);
                this.eliminateTemporarily(player, "§4Eliminato dal Deathmatch!");
            } else {
                player.setHealth(Math.max((double)1.0F, player.getHealth() - damage));
                this.playSound(player, Sound.ENTITY_PLAYER_HURT, 0.7F, 0.7F);
            }
        }
    }

    private void announceCustomDeath(Player victim, EntityDamageEvent.DamageCause cause) {
        if (victim.getWorld().getName().equalsIgnoreCase("bedfight")) {
            Player attacker = null;
            LastHit hit = (LastHit)this.lastHits.get(victim.getUniqueId());
            if (hit != null && System.currentTimeMillis() - hit.timestampMillis() <= 10000L) {
                attacker = Bukkit.getPlayer(hit.attackerId());
            }

            this.lastHits.remove(victim.getUniqueId());
            String message;
            if (attacker != null && attacker.isOnline()) {
                String var6 = victim.getName();
                message = "§c☠ §f" + var6 + " §7è stato ucciso da §f" + attacker.getName() + "§7.";
            } else {
                String var10000;
                switch (cause) {
                    case VOID:
                        var10000 = "§c☠ §f" + victim.getName() + " §7è caduto nel vuoto.";
                        break;
                    case FALL:
                        var10000 = "§c☠ §f" + victim.getName() + " §7si è schiantato.";
                        break;
                    case FIRE:
                    case FIRE_TICK:
                    case LAVA:
                    case HOT_FLOOR:
                        var10000 = "§c☠ §f" + victim.getName() + " §7è finito arrosto.";
                        break;
                    case PROJECTILE:
                        var10000 = "§c☠ §f" + victim.getName() + " §7è stato colpito a distanza.";
                        break;
                    case ENTITY_EXPLOSION:
                    case BLOCK_EXPLOSION:
                        var10000 = "§c☠ §f" + victim.getName() + " §7è esploso.";
                        break;
                    case DROWNING:
                        var10000 = "§c☠ §f" + victim.getName() + " §7non sapeva nuotare.";
                        break;
                    case SUFFOCATION:
                        var10000 = "§c☠ §f" + victim.getName() + " §7è rimasto incastrato nei blocchi.";
                        break;
                    default:
                        var10000 = "§c☠ §f" + victim.getName() + " §7è stato eliminato.";
                }

                message = var10000;
            }

            Bukkit.broadcastMessage(message);
        }
    }

    private void forEachRoundViewer(ActiveRound round, Consumer<Player> action) {
        Player first = Bukkit.getPlayer(round.match.getCreator());
        Player opponent = Bukkit.getPlayer(round.match.getOpponent());
        if (first != null) {
            action.accept(first);
        }

        if (opponent != null) {
            action.accept(opponent);
        }

        for(UUID spectatorId : round.spectators.keySet()) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null) {
                action.accept(spectator);
            }
        }

    }

    private boolean prepareFighter(Player player, Location spawn, Team team) {
        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(true);
        this.clearPotionEffects(player);
        this.resetCombatState(player);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand((ItemStack)null);
        if (!player.teleport(spawn)) {
            return false;
        } else {
            this.giveKit(player, team);
            return true;
        }
    }

    private void resetCombatState(Player player) {
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setFoodLevel(20);
        player.setSaturation(0.0F);
        player.setExhaustion(0.0F);
        player.setHealth(player.getMaxHealth());
        player.setLevel(0);
        player.setExp(0.0F);
        player.setTotalExperience(0);
    }

    private void giveKit(Player player, Team team) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        Color color = team == BedfightManager.Team.FIRST ? Color.BLUE : Color.RED;
        ChatColor chatColor = team == BedfightManager.Team.FIRST ? ChatColor.BLUE : ChatColor.RED;
        Material wool = team == BedfightManager.Team.FIRST ? Material.BLUE_WOOL : Material.RED_WOOL;
        inventory.setHelmet(this.leatherArmor(Material.LEATHER_HELMET, color, String.valueOf(chatColor) + "Elmo BedWars"));
        inventory.setChestplate(this.leatherArmor(Material.LEATHER_CHESTPLATE, color, String.valueOf(chatColor) + "Corazza BedWars"));
        inventory.setLeggings(this.leatherArmor(Material.LEATHER_LEGGINGS, color, String.valueOf(chatColor) + "Gambali BedWars"));
        inventory.setBoots(this.leatherArmor(Material.LEATHER_BOOTS, color, String.valueOf(chatColor) + "Stivali BedWars"));
        inventory.setItem(0, this.unbreakableItem(Material.WOODEN_SWORD, "§fSpada di legno", false));
        inventory.setItem(1, this.unbreakableItem(Material.SHEARS, "§fCesoie", false));
        inventory.setItem(2, this.unbreakableItem(Material.WOODEN_AXE, "§fAscia", true));
        inventory.setItem(3, this.unbreakableItem(Material.WOODEN_PICKAXE, "§fPiccone", true));
        inventory.setItem(4, this.plainStack(wool, 64));
        inventory.setItemInOffHand(this.plainStack(wool, 64));
        inventory.setHeldItemSlot(0);
        player.updateInventory();
    }

    private ItemStack leatherArmor(Material material, Color color, String name) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta)item.getItemMeta();
        meta.setColor(color);
        meta.setDisplayName(name);
        this.applyUnbreakable(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack unbreakableItem(Material material, String name, boolean efficiency) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        this.applyUnbreakable(meta);
        item.setItemMeta(meta);
        if (efficiency) {
            item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 1);
        }

        return item;
    }

    private ItemStack plainStack(Material material, int amount) {
        return new ItemStack(material, amount);
    }

    public boolean isUndroppableKitItem(ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            Material type = item.getType();
            if (type != Material.BLUE_WOOL && type != Material.RED_WOOL) {
                return type == Material.WOODEN_SWORD || type == Material.SHEARS || type == Material.WOODEN_AXE || type == Material.WOODEN_PICKAXE || type == Material.LEATHER_HELMET || type == Material.LEATHER_CHESTPLATE || type == Material.LEATHER_LEGGINGS || type == Material.LEATHER_BOOTS;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private void applyUnbreakable(ItemMeta meta) {
        meta.setUnbreakable(true);
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES});
    }

    private void finishRound(UUID winnerId, boolean forfeit) {
        ActiveRound round = this.activeRound;
        if (round != null && !round.finishing && round.match.includes(winnerId)) {
            round.finishing = true;
            this.cancelCountdown(round);
            this.cancelRoundClock(round);
            this.activeRound = null;
            this.lastHits.clear();
            this.restoreArena(round);
            double prize = round.match.getAmount() * this.plugin.getGameManager().getWinMultiplier();
            OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerId);
            EconomyResponse payout = SunnyCoinflip.getEconomy().depositPlayer(winner, prize);
            boolean paid = payout.transactionSuccess();
            if (!paid) {
                Logger var10000 = this.plugin.getLogger();
                String var10001 = round.match.getName(winnerId);
                var10000.severe("Pagamento BedWars fallito per " + var10001 + ": " + payout.errorMessage + ". Rimborso delle puntate in corso.");
                this.refundStakes(round.match);
            }

            UUID loserId = round.match.getOtherParticipant(winnerId);
            this.playRoundEndSounds(round, winnerId, loserId);
            if (paid) {
                this.settleBets(round, winnerId);
            } else {
                this.refundBets(round);
            }

            this.restoreParticipants(round);
            round.match.setState(BedfightCoinflip.State.FINISHED);
            String winnerName = round.match.getName(winnerId);
            String loserName = loserId == null ? "Sconosciuto" : round.match.getName(loserId);
            this.messageOnline(winnerId, paid ? "§8[§bBedWars§8] §r§aHai vinto contro " + loserName + "! Premio: §f\ue0d8 §e" + this.formatMoney(prize) : "§8[§bBedWars§8] §r§ePagamento non riuscito: entrambe le puntate sono state rimborsate.");
            if (loserId != null) {
                this.messageOnline(loserId, paid ? "§8[§bBedWars§8] §r§cHai perso contro " + winnerName + "." : "§8[§bBedWars§8] §r§ePagamento non riuscito: puntata rimborsata.");
            }

            Bukkit.broadcastMessage("§b§lBEDWARS CF! §f" + winnerName + " §7ha sconfitto §f" + loserName + (paid ? " §7e ha vinto §f\ue0d8 §e" + this.formatMoney(prize) : " §7(puntate rimborsate)") + (forfeit ? " §7per abbandono." : "§7."));
        }
    }

    private void abortActiveRound(String message, boolean refund) {
        ActiveRound round = this.activeRound;
        if (round != null && !round.finishing) {
            round.finishing = true;
            this.cancelCountdown(round);
            this.cancelRoundClock(round);
            this.activeRound = null;
            this.lastHits.clear();
            this.restoreArena(round);
            if (refund) {
                this.refundStakes(round.match);
            }

            this.refundBets(round);
            this.restoreParticipants(round);
            round.match.setState(BedfightCoinflip.State.FINISHED);
            this.messageOnline(round.match.getCreator(), "§8[§bBedWars§8] §r" + message);
            this.messageOnline(round.match.getOpponent(), "§8[§bBedWars§8] §r" + message);
        }
    }

    private void restoreParticipants(ActiveRound round) {
        Map<UUID, PlayerSnapshot> allSnapshots = new LinkedHashMap(round.snapshots);
        allSnapshots.putAll(round.spectators);

        for(Map.Entry<UUID, PlayerSnapshot> entry : allSnapshots.entrySet()) {
            UUID playerId = (UUID)entry.getKey();
            PlayerSnapshot snapshot = (PlayerSnapshot)entry.getValue();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                if (player.isDead()) {
                    this.pendingRestores.put(playerId, snapshot);
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        if (player.isOnline() && player.isDead()) {
                            player.spigot().respawn();
                        }

                    });
                } else {
                    this.applySnapshotSafely(player, snapshot);
                }
            } else {
                this.pendingRestores.put(playerId, snapshot);
            }
        }

    }

    private void restoreWaitingCreator(UUID playerId) {
        PlayerSnapshot snapshot = (PlayerSnapshot)this.waitingSnapshots.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (snapshot != null && player != null && player.isOnline()) {
            this.applySnapshotSafely(player, snapshot);
        } else if (snapshot != null) {
            this.pendingRestores.put(playerId, snapshot);
        }

    }

    private void applySnapshotSafely(Player player, PlayerSnapshot snapshot) {
        UUID playerId = player.getUniqueId();
        this.restoringPlayers.add(playerId);
        snapshot.applyBaseState(player);
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
                if (player.isOnline()) {
                    snapshot.applyInventory(player);
                } else {
                    this.pendingRestores.put(playerId, snapshot);
                }
            } finally {
                Bukkit.getScheduler().runTask(this.plugin, () -> this.restoringPlayers.remove(playerId));
            }

        });
    }

    private void restoreArena(ActiveRound round) {
        List<Map.Entry<BlockKey, BlockData>> blocks = new ArrayList(round.originalBlocks.entrySet());

        for(int i = blocks.size() - 1; i >= 0; --i) {
            Map.Entry<BlockKey, BlockData> entry = (Map.Entry)blocks.get(i);
            BlockKey key = (BlockKey)entry.getKey();
            if (round.world.getUID().equals(key.worldId)) {
                round.world.getBlockAt(key.x, key.y, key.z).setBlockData((BlockData)entry.getValue(), false);
            }
        }

        if (this.plugin.getConfig().getBoolean("bedwars.cleanup-new-entities", true)) {
            for(Entity entity : new ArrayList(round.world.getEntities())) {
                if (!(entity instanceof Player) && !round.initialEntities.contains(entity.getUniqueId())) {
                    entity.remove();
                }
            }
        }

    }

    private void captureBedBlocks(ActiveRound round, Set<BlockKey> bedBlocks) {
        for(BlockKey key : bedBlocks) {
            Block block = round.world.getBlockAt(key.x, key.y, key.z);
            round.originalBlocks.putIfAbsent(key, block.getBlockData().clone());
        }

    }

    private void announceBedDestroyed(Player breaker, Team destroyedTeam) {
        String destroyedPlayer = destroyedTeam == BedfightManager.Team.FIRST ? this.activeRound.match.getCreatorName() : this.activeRound.match.getOpponentName();
        String message = "§8[§bBedWars§8] §r§cIl letto di §f" + destroyedPlayer + " §cè stato distrutto da §f" + breaker.getName() + "§c! La prossima morte sarà definitiva.";
        this.messageOnline(this.activeRound.match.getCreator(), message);
        this.messageOnline(this.activeRound.match.getOpponent(), message);
        Player first = Bukkit.getPlayer(this.activeRound.match.getCreator());
        Player opponent = Bukkit.getPlayer(this.activeRound.match.getOpponent());
        this.playSound(first, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8F, 1.3F);
        this.playSound(opponent, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8F, 1.3F);
    }

    private Set<BlockKey> resolveConfiguredOrNearbyBed(Location configured, Location spawn, int radius) {
        if (configured != null && configured.getWorld() != null) {
            Set<BlockKey> configuredBed = this.resolveBed(configured.getBlock());
            if (!configuredBed.isEmpty()) {
                return configuredBed;
            }
        }

        Block nearest = this.findNearestBed(spawn, radius);
        return nearest == null ? Set.of() : this.resolveBed(nearest);
    }

    private Block findNearestBed(Location origin, int radius) {
        if (origin != null && origin.getWorld() != null) {
            Block nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            World world = origin.getWorld();
            int verticalRadius = Math.min(radius, 6);

            for(int x = -radius; x <= radius; ++x) {
                for(int y = -verticalRadius; y <= verticalRadius; ++y) {
                    for(int z = -radius; z <= radius; ++z) {
                        Block block = world.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
                        if (block.getBlockData() instanceof Bed) {
                            double distance = block.getLocation().distanceSquared(origin);
                            if (distance < nearestDistance) {
                                nearest = block;
                                nearestDistance = distance;
                            }
                        }
                    }
                }
            }

            return nearest;
        } else {
            return null;
        }
    }

    private Set<BlockKey> resolveBed(Block block) {
        BlockData var3 = block.getBlockData();
        if (var3 instanceof Bed bedData) {
            Set<BlockKey> result = new HashSet();
            result.add(BedfightManager.BlockKey.of(block));
            BlockFace otherDirection = bedData.getPart() == Part.FOOT ? bedData.getFacing() : bedData.getFacing().getOppositeFace();
            Block other = block.getRelative(otherDirection);
            if (other.getBlockData() instanceof Bed) {
                result.add(BedfightManager.BlockKey.of(other));
            }

            return result;
        } else {
            return Set.of();
        }
    }

    private void setBedFromTarget(Player player, String path, String teamName) {
        Block target = player.getTargetBlockExact(6);
        if (target != null && target.getBlockData() instanceof Bed) {
            this.setLocation(path, target.getLocation());
            player.sendMessage("§8[§bBedWars§8] §r§aLetto " + teamName + " impostato.");
        } else {
            player.sendMessage("§8[§bBedWars§8] §r§cGuarda un letto entro 6 blocchi e riprova.");
        }
    }

    private void setLocation(String path, Location location) {
        this.plugin.getConfig().set(path, location);
        this.plugin.saveConfig();
    }

    private boolean isArenaReady(CommandSender sender) {
        Location first = this.getFirstPosition();
        Location opponent = this.getOpponentPosition();
        if (first != null && opponent != null && first.getWorld() != null && opponent.getWorld() != null) {
            if (!first.getWorld().getUID().equals(opponent.getWorld().getUID())) {
                sender.sendMessage("§8[§bBedWars§8] §r§cLe posizioni devono essere nello stesso mondo.");
                return false;
            } else {
                return true;
            }
        } else {
            sender.sendMessage("§8[§bBedWars§8] §r§cArena non configurata. Usa setfirstpos e setopponentpos.");
            return false;
        }
    }

    private void resetWaitingChallenge(BedfightCoinflip challenge) {
        challenge.setOpponent((UUID)null, (String)null);
        challenge.setState(BedfightCoinflip.State.WAITING);
    }

    private void refundStakes(BedfightCoinflip match) {
        SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(match.getCreator()), match.getAmount());
        if (match.getOpponent() != null) {
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(match.getOpponent()), match.getAmount());
        }

    }

    private boolean isBusyInOtherMode(UUID playerId) {
        if (this.plugin.getGameManager().getCoinflip(playerId) != null) {
            return true;
        } else {
            PillarMatch pillar = this.plugin.getGameManager().getActivePillarMatch();
            return pillar != null && (playerId.equals(pillar.getCreator()) || pillar.getOpponent() != null && playerId.equals(pillar.getOpponent()));
        }
    }

    private BedfightCoinflip findWaitingByName(String name) {
        for(BedfightCoinflip challenge : this.waitingByCreator.values()) {
            if (challenge.getCreatorName().equalsIgnoreCase(name)) {
                return challenge;
            }
        }

        return null;
    }

    private void purgeExpiredChallenges() {
        long expirySeconds = this.plugin.getConfig().getLong("bedwars.challenge-expire-seconds", 300L);
        if (expirySeconds > 0L) {
            long oldestAllowed = System.currentTimeMillis() - expirySeconds * 1000L;

            for(UUID creatorId : this.waitingByCreator.values().stream().filter((challenge) -> challenge.getCreatedAt() < oldestAllowed).map(BedfightCoinflip::getCreator).toList()) {
                this.waitingByCreator.remove(creatorId);
                this.restoreWaitingCreator(creatorId);
                this.messageOnline(creatorId, "§8[§bBedWars§8] §r§eLa sfida è scaduta.");
            }

        }
    }

    private Team teamOf(UUID playerId) {
        return this.activeRound != null && this.activeRound.match.getCreator().equals(playerId) ? BedfightManager.Team.FIRST : BedfightManager.Team.OPPONENT;
    }

    private int getBedSearchRadius() {
        return Math.max(1, this.plugin.getConfig().getInt("bedwars.bed-search-radius", 12));
    }

    private boolean isBedAlive(ActiveRound round, Team team) {
        return team == BedfightManager.Team.FIRST ? round.match.isFirstBedAlive() : round.match.isOpponentBedAlive();
    }

    private long getRespawnDelayTicks() {
        return Math.max(1L, this.plugin.getConfig().getLong("bedwars.respawn-delay-ticks", 60L));
    }

    private void applySpawnProtection(ActiveRound round, Player player) {
        long duration = Math.max(0L, this.plugin.getConfig().getLong("bedwars.spawn-protection-ticks", 60L));
        round.spawnProtectedUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration * 50L);
        if (duration > 0L) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (this.activeRound == round && player.isOnline() && !round.respawning.contains(player.getUniqueId())) {
                    this.removeSpawnProtection(round, player, false);
                }

            }, duration);
        }

    }

    private void removeSpawnProtection(ActiveRound round, Player player, boolean causedByAttack) {
        if (round.spawnProtectedUntil.remove(player.getUniqueId()) != null) {
            player.sendActionBar(causedByAttack ? "§cProtezione spawn disattivata: hai attaccato" : "§aProtezione spawn terminata");
            this.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, causedByAttack ? 0.8F : 1.4F);
        }
    }

    private int getMaxBuildHeight() {
        if (this.plugin.getConfig().contains("bedwars.max-build-height")) {
            return this.plugin.getConfig().getInt("bedwars.max-build-height");
        } else if (this.plugin.getConfig().contains("bedwars.max-height")) {
            return this.plugin.getConfig().getInt("bedwars.max-height");
        } else {
            return this.plugin.getConfig().contains("bedfight.max-build-height") ? this.plugin.getConfig().getInt("bedfight.max-build-height") : 100;
        }
    }

    private boolean hasSpawnProtection(ActiveRound round, UUID playerId) {
        Long until = (Long)round.spawnProtectedUntil.get(playerId);
        if (until == null) {
            return false;
        } else if (until <= System.currentTimeMillis()) {
            round.spawnProtectedUntil.remove(playerId);
            return false;
        } else {
            return true;
        }
    }

    private void refundPreStartQuitBets(ActiveRound round, UUID quitterId) {
        if (!round.bets.isEmpty()) {
            for(SpectatorBet bet : round.bets.values()) {
                SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(bet.bettorId()), bet.amount());
                if (bet.selectedPlayer().equals(quitterId)) {
                    UUID var10001 = bet.bettorId();
                    String var10002 = round.match.getName(quitterId);
                    this.messageOnline(var10001, "§8[§bBedWars§8] §r§eLa tua scommessa su §f" + var10002 + " §eè stata annullata e rimborsata perché il giocatore ha abbandonato prima del VIA: §f\ue0d8 §e" + this.formatMoney(bet.amount()) + "§e.");
                } else {
                    UUID var5 = bet.bettorId();
                    String var6 = this.formatMoney(bet.amount());
                    this.messageOnline(var5, "§8[§bBedWars§8] §r§eRound non disputato: scommessa restituita §f\ue0d8 §e" + var6 + "§e.");
                }
            }

            round.bets.clear();
        }
    }

    private void settleBets(ActiveRound round, UUID winnerId) {
        if (!round.bets.isEmpty()) {
            double totalPool = round.bets.values().stream().mapToDouble(SpectatorBet::amount).sum();
            double winningPool = round.bets.values().stream().filter((betx) -> betx.selectedPlayer().equals(winnerId)).mapToDouble(SpectatorBet::amount).sum();
            if (winningPool <= (double)0.0F) {
                this.refundBets(round);
            } else {
                for(SpectatorBet bet : round.bets.values()) {
                    if (!bet.selectedPlayer().equals(winnerId)) {
                        UUID var10001 = bet.bettorId();
                        BedfightCoinflip var10002 = round.match;
                        this.messageOnline(var10001, "§8[§bBedWars§8] §r§cHai perso la scommessa su §f" + var10002.getName(bet.selectedPlayer()) + "§c.");
                    } else {
                        double payout = totalPool * (bet.amount() / winningPool);
                        EconomyResponse response = SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(bet.bettorId()), payout);
                        this.messageOnline(bet.bettorId(), response.transactionSuccess() ? "§8[§bBedWars§8] §r§aScommessa vinta! Incasso: §f\ue0d8 §e" + this.formatMoney(payout) : "§8[§bBedWars§8] §r§cPagamento scommessa fallito; contatta un amministratore.");
                    }
                }

                round.bets.clear();
            }
        }
    }

    private void refundBets(ActiveRound round) {
        for(SpectatorBet bet : round.bets.values()) {
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(bet.bettorId()), bet.amount());
            UUID var10001 = bet.bettorId();
            String var10002 = this.formatMoney(bet.amount());
            this.messageOnline(var10001, "§8[§bBedWars§8] §r§eScommessa rimborsata: §f\ue0d8 §e" + var10002 + "§e.");
        }

        round.bets.clear();
    }

    private void playRoundEndSounds(ActiveRound round, UUID winnerId, UUID loserId) {
        this.playSound(Bukkit.getPlayer(winnerId), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        if (loserId != null) {
            this.playSound(Bukkit.getPlayer(loserId), Sound.ENTITY_WITHER_DEATH, 0.6F, 1.4F);
        }

        for(SpectatorBet bet : round.bets.values()) {
            Player bettor = Bukkit.getPlayer(bet.bettorId());
            this.playSound(bettor, bet.selectedPlayer().equals(winnerId) ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO, 0.8F, 1.0F);
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
        for(PotionEffect effect : new ArrayList(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }

    }

    private boolean isSameWorld(Location location, World world) {
        return location != null && location.getWorld() != null && location.getWorld().getUID().equals(world.getUID());
    }

    private String bedStatus(boolean alive) {
        return alive ? "§aIntegro" : "§cDistrutto";
    }

    private String formatMoney(double amount) {
        return amount == Math.rint(amount) ? String.format(Locale.US, "%,.0f", amount) : String.format(Locale.US, "%,.2f", amount);
    }

    private void messageOnline(UUID playerId, String message) {
        if (playerId != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }

        }
    }

    public static enum BreakResult {
        ALLOW,
        DENY,
        BED,
        BREAKABLE_ARENA_BLOCK;

        // $FF: synthetic method
        private static BreakResult[] $values() {
            return new BreakResult[]{ALLOW, DENY, BED, BREAKABLE_ARENA_BLOCK};
        }
    }

    private static enum Team {
        FIRST("Blu"),
        OPPONENT("Rossa");

        private final String displayName;

        private Team(String displayName) {
            this.displayName = displayName;
        }

        // $FF: synthetic method
        private static Team[] $values() {
            return new Team[]{FIRST, OPPONENT};
        }
    }

    private static record LastHit(UUID attackerId, long timestampMillis) {
    }

    private static final class ActiveRound {
        private final BedfightCoinflip match;
        private final World world;
        private final Location firstSpawn;
        private final Location opponentSpawn;
        private final Set<BlockKey> firstBed;
        private final Set<BlockKey> opponentBed;
        private final Map<BlockKey, BlockData> originalBlocks = new LinkedHashMap();
        private final Set<UUID> initialEntities = new HashSet();
        private final Map<UUID, PlayerSnapshot> snapshots = new HashMap();
        private final Map<UUID, SpectatorBet> bets = new LinkedHashMap();
        private final Map<UUID, PlayerSnapshot> spectators = new LinkedHashMap();
        private final Set<UUID> respawning = new HashSet();
        private final Map<UUID, Long> spawnProtectedUntil = new HashMap();
        private BukkitTask countdownTask;
        private BukkitTask roundClockTask;
        private int elapsedSeconds;
        private boolean bedsAutoDestroyed;
        private boolean deathmatchAnnounced;
        private boolean playing;
        private boolean finishing;

        private ActiveRound(BedfightCoinflip match, World world, Location firstSpawn, Location opponentSpawn, Set<BlockKey> firstBed, Set<BlockKey> opponentBed) {
            this.match = match;
            this.world = world;
            this.firstSpawn = firstSpawn;
            this.opponentSpawn = opponentSpawn;
            this.firstBed = new HashSet(firstBed);
            this.opponentBed = new HashSet(opponentBed);
        }
    }

    private static record SpectatorBet(UUID bettorId, String bettorName, UUID selectedPlayer, double amount) {
    }

    private static record BlockKey(UUID worldId, int x, int y, int z) {
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
            this.potionEffects = new ArrayList(player.getActivePotionEffects());
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
            player.setGameMode(this.gameMode);
            player.setAllowFlight(this.allowFlight);
            player.setFlying(this.allowFlight && this.flying);
            player.setInvulnerable(false);
            player.setFireTicks(this.fireTicks);
            player.setFallDistance(0.0F);
            player.setFoodLevel(this.food);
            player.setSaturation(this.saturation);
            player.setExhaustion(this.exhaustion);
            player.setLevel(this.level);
            player.setExp(this.exp);
            player.setTotalExperience(this.totalExp);

            for(PotionEffect current : new ArrayList(player.getActivePotionEffects())) {
                player.removePotionEffect(current.getType());
            }

            for(PotionEffect effect : this.potionEffects) {
                player.addPotionEffect(effect);
            }

            player.teleport(this.location);
            player.setHealth(Math.min(this.health, player.getMaxHealth()));
        }

        private void applyInventory(Player player) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setStorageContents(cloneItems(this.storage));
            inventory.setArmorContents(cloneItems(this.armor));
            inventory.setItemInOffHand(cloneItem(this.offHand));
            player.updateInventory();
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            ItemStack[] result = new ItemStack[items.length];

            for(int i = 0; i < items.length; ++i) {
                result[i] = cloneItem(items[i]);
            }

            return result;
        }

        private static ItemStack cloneItem(ItemStack item) {
            return item == null ? null : item.clone();
        }
    }
}
