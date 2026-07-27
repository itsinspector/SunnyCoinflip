package org.ItsInspector.sunnyCoinflip.managers;

import net.milkbowl.vault.economy.Economy;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.BedfightCoinflip;
import org.ItsInspector.sunnyCoinflip.utils.NumberParser;
import org.bukkit.Bukkit;
import org.bukkit.Color;
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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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
 * Gestisce la modalità BedFight del coinflip.
 *
 * Include gestione squadre e kit, protezione dell'arena, eliminazioni senza morte
 * reale, respawn persistente in spectator e deathmatch progressivo in due fasi.
 */
public final class BedfightManager {
    public enum BreakResult { ALLOW, DENY, OWN_BED, ENEMY_BED }

    private static final String PREFIX = "";
    private static final String BEDFIGHT_WORLD = "bedfight";
    private static final double HALF_HEART = 1.0;

    private final SunnyCoinflip plugin;
    private final Map<UUID, BedfightCoinflip> waitingByCreator = new LinkedHashMap<>();
    private final Map<UUID, PlayerSnapshot> waitingSnapshots = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> pendingRestores = new HashMap<>();
    private final Set<UUID> awaitingCreateAmount = new HashSet<>();
    private final Map<UUID, LastHit> lastHits = new HashMap<>();
    private ActiveRound activeRound;

    public BedfightManager(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("bedfight.enabled", true);
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

    public boolean isBlockProtectionActive(World world) {
        return isProtectedBedfightRound(activeRound, world);
    }

    public void handleSimpleCommand(Player player) {
        if (!isEnabled()) {
            player.sendMessage(PREFIX + "§cLa modalità BedFight è attualmente disabilitata.");
            return;
        }
        if (activeRound != null) {
            if (activeRound.playing && !isActiveParticipant(player.getUniqueId())) {
                startSpectating(player);
            } else {
                player.sendMessage("§cL'arena è attualmente occupata.");
            }
            return;
        }
        if (waitingByCreator.containsKey(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§eHai già una sfida in attesa. §7Usa §f/cf bedfight cancel §7per annullarla.");
            return;
        }
        if (!waitingByCreator.isEmpty()) {
            BedfightCoinflip waiting = waitingByCreator.values().iterator().next();
            acceptChallenge(player, waiting.getCreatorName());
            return;
        }
        awaitingCreateAmount.add(player.getUniqueId());
        player.sendMessage(PREFIX + "§eInserisci in chat l'importo della sfida oppure scrivi §fcancel§e.");
    }

    public boolean isAwaitingCreateAmount(UUID playerId) {
        return awaitingCreateAmount.contains(playerId);
    }

    public void handleCreateAmountChat(Player player, String message) {
        if (!awaitingCreateAmount.remove(player.getUniqueId())) return;
        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("annulla")) {
            player.sendMessage(PREFIX + "§eCreazione della sfida annullata.");
            return;
        }
        try {
            createChallenge(player, NumberParser.parseNumber(message));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(PREFIX + "§c" + exception.getMessage());
        }
    }

    public void createChallenge(Player creator, double amount) {
        if (!isEnabled()) {
            creator.sendMessage(PREFIX + "§cLa modalità BedFight è attualmente disabilitata.");
            return;
        }
        if (!isArenaConfigured()) {
            creator.sendMessage(PREFIX + "§cLa configurazione dell'arena BedFight non è completa.");
            return;
        }
        if (activeRound != null || !waitingByCreator.isEmpty()) {
            creator.sendMessage(PREFIX + "§cL'arena è attualmente occupata da un'altra partita.");
            return;
        }
        if (isParticipant(creator.getUniqueId())) {
            creator.sendMessage(PREFIX + "§cSei già coinvolto in una sfida o in una partita.");
            return;
        }
        if (amount <= 0 || amount > plugin.getGameManager().getMaxAmount()) {
            creator.sendMessage(PREFIX + "§cImporto non valido. §7Importo massimo: §f"
                    + money(plugin.getGameManager().getMaxAmount()) + "§7.");
            return;
        }
        if (!economy().has(creator, amount)) {
            creator.sendMessage(PREFIX + "§cSaldo insufficiente per creare questa sfida.");
            return;
        }

        PlayerSnapshot snapshot = PlayerSnapshot.capture(creator);
        waitingSnapshots.put(creator.getUniqueId(), snapshot);
        BedfightCoinflip match = new BedfightCoinflip(creator.getUniqueId(), creator.getName(), amount);
        waitingByCreator.put(creator.getUniqueId(), match);

        prepareQueuePlayer(creator, getFirstPosition());
        creator.sendMessage(PREFIX + "§aSfida creata con successo per §r§f\uE0D8 §e" + money(amount) + "§a.");
        creator.sendMessage(PREFIX + "§7In attesa di un avversario. Usa §f/cf bedfight cancel §7per annullare.");

        long expireTicks = Math.max(20L, plugin.getConfig().getLong("bedfight.challenge-expire-seconds", 300L) * 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BedfightCoinflip stillWaiting = waitingByCreator.get(creator.getUniqueId());
            if (stillWaiting != match) return;
            waitingByCreator.remove(creator.getUniqueId());
            Player online = Bukkit.getPlayer(creator.getUniqueId());
            if (online != null) {
                restoreWaitingPlayer(online);
                online.sendMessage(PREFIX + "§eLa sfida è scaduta perché non è stata accettata in tempo.");
            } else {
                waitingSnapshots.remove(creator.getUniqueId());
            }
        }, expireTicks);
    }

    public void listChallenges(Player player) {
        if (waitingByCreator.isEmpty()) {
            player.sendMessage(PREFIX + "§eNon sono presenti sfide BedFight disponibili.");
            return;
        }
        player.sendMessage("§8§m────────§r §b§lSFIDE BEDFIGHT DISPONIBILI §8§m────────");
        for (BedfightCoinflip match : waitingByCreator.values()) {
            player.sendMessage("§e- §f" + match.getCreatorName() + " §7• §a" + money(match.getAmount())
                    + " §7• §f/cf bedfight accept " + match.getCreatorName());
        }
    }

    public void acceptChallenge(Player opponent, String creatorName) {
        if (activeRound != null) {
            opponent.sendMessage(PREFIX + "§cL'arena è attualmente occupata.");
            return;
        }
        BedfightCoinflip match = findWaitingByName(creatorName);
        if (match == null) {
            opponent.sendMessage(PREFIX + "§cNessuna sfida disponibile creata da §f" + creatorName + "§c.");
            return;
        }
        if (match.getCreator().equals(opponent.getUniqueId())) {
            opponent.sendMessage(PREFIX + "§cNon puoi accettare una sfida creata da te.");
            return;
        }
        if (isParticipant(opponent.getUniqueId())) {
            opponent.sendMessage(PREFIX + "§cSei già coinvolto in una sfida o in una partita.");
            return;
        }

        Player creator = Bukkit.getPlayer(match.getCreator());
        if (creator == null || !creator.isOnline()) {
            waitingByCreator.remove(match.getCreator());
            waitingSnapshots.remove(match.getCreator());
            opponent.sendMessage(PREFIX + "§cIl creatore della sfida non è più online.");
            return;
        }
        if (!economy().has(creator, match.getAmount()) || !economy().has(opponent, match.getAmount())) {
            opponent.sendMessage(PREFIX + "§cLa partita non può iniziare: saldo insufficiente.");
            creator.sendMessage(PREFIX + "§cLa partita non può iniziare: uno dei partecipanti non dispone del saldo richiesto.");
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
        creator.sendMessage(PREFIX + "§aAvversario trovato: §f" + opponent.getName() + "§a.");
        opponent.sendMessage(PREFIX + "§aHai accettato la sfida di §f" + creator.getName() + "§a.");
        playSound(creator, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        playSound(opponent, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        startCountdown(round);
    }

    public void cancelWaiting(Player player) {
        BedfightCoinflip removed = waitingByCreator.remove(player.getUniqueId());
        awaitingCreateAmount.remove(player.getUniqueId());
        if (removed == null) {
            player.sendMessage(PREFIX + "§eNon hai alcuna sfida BedFight in attesa.");
            return;
        }
        restoreWaitingPlayer(player);
        player.sendMessage(PREFIX + "§eCoinflip BedFight annullato.");
    }

    public void placeBet(Player bettor, String targetName, double amount) {
        ActiveRound round = activeRound;
        if (round == null || round.playing || round.finishing) {
            bettor.sendMessage(PREFIX + "§cLe scommesse sono disponibili esclusivamente durante il conto alla rovescia.");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !round.match.includes(target.getUniqueId())) {
            bettor.sendMessage(PREFIX + "§cIl giocatore indicato non partecipa a questa partita.");
            return;
        }
        if (round.match.includes(bettor.getUniqueId())) {
            bettor.sendMessage(PREFIX + "§cI partecipanti non possono scommettere sulla propria partita.");
            return;
        }
        if (amount <= 0 || !economy().has(bettor, amount)) {
            bettor.sendMessage(PREFIX + "§cImporto non valido oppure saldo insufficiente.");
            return;
        }
        economy().withdrawPlayer(bettor, amount);
        round.bets.computeIfAbsent(target.getUniqueId(), ignored -> new HashMap<>())
                .merge(bettor.getUniqueId(), amount, Double::sum);
        bettor.sendMessage(PREFIX + "§aScommessa registrata: §f" + money(amount)
                + " §asu §f" + target.getName() + "§a.");
    }

    public void startSpectating(Player player) {
        ActiveRound round = activeRound;
        if (round == null || !round.playing || round.finishing) {
            player.sendMessage(PREFIX + "§cNon è presente alcuna partita BedFight da osservare.");
            return;
        }
        if (round.match.includes(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§cSei già un partecipante della partita.");
            return;
        }
        round.spectators.putIfAbsent(player.getUniqueId(), PlayerSnapshot.capture(player));
        Location destination = midpoint(round.firstSpawn, round.opponentSpawn).add(0, 5, 0);

        // Teletrasporto prima della modalità: plugin di gestione mondi possono cambiare il gamemode.
        player.teleport(destination);

        // FIX 1: modalità spectator mantenuta ogni tick, quindi funziona anche senza OP.
        forceSpectatorMode(player, round);
        player.sendMessage(PREFIX + "§aModalità spettatore attivata. §7Ora stai osservando la partita.");
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

    private void forceRespawnSpectatorMode(Player player, ActiveRound expectedRound) {
        BukkitTask previousTask = expectedRound.respawnSpectatorTasks.remove(player.getUniqueId());
        cancel(previousTask);
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
        expectedRound.respawnSpectatorTasks.put(player.getUniqueId(), task[0]);
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
        player.setSaturation(0.0f);
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
        player.setSaturation(0.0f);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setNoDamageTicks(0);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        applyLegacyCombat(player);
        giveKit(player, isFirstTeam(player.getUniqueId()));

        // FIX 3: vale sia per First sia per Opponent, dopo il cambio mondo/teletrasporto.
        restoreFullHealthAfterEntry(player);
    }

    private void restoreFullHealthAfterEntry(Player player) {
        restoreFullHealth(player);
        UUID playerId = player.getUniqueId();
        ActiveRound round = activeRound;
        if (round != null && round.playing) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && isParticipant(playerId)) restoreFullHealth(player);
        });
    }

    private void restoreFullHealth(Player player) {
        if (!player.isOnline() || player.isDead()) return;
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maximum = attribute == null ? 20.0 : attribute.getValue();
        player.setHealth(Math.max(1.0, maximum));
    }

    private void giveKit(Player player, boolean firstTeam) {
        PlayerInventory inventory = player.getInventory();
        Color armorColor = firstTeam ? Color.BLUE : Color.RED;
        Material wool = firstTeam ? Material.BLUE_WOOL : Material.RED_WOOL;

        inventory.setHelmet(coloredLeather(Material.LEATHER_HELMET, armorColor));
        inventory.setChestplate(coloredLeather(Material.LEATHER_CHESTPLATE, armorColor));
        inventory.setLeggings(coloredLeather(Material.LEATHER_LEGGINGS, armorColor));
        inventory.setBoots(coloredLeather(Material.LEATHER_BOOTS, armorColor));

        inventory.setItem(0, unbreakable(new ItemStack(Material.WOODEN_SWORD)));
        inventory.setItem(1, unbreakable(new ItemStack(Material.SHEARS)));

        ItemStack pickaxe = new ItemStack(Material.WOODEN_PICKAXE);
        pickaxe.addEnchantment(Enchantment.EFFICIENCY, 1);
        inventory.setItem(2, unbreakable(pickaxe));

        ItemStack axe = new ItemStack(Material.WOODEN_AXE);
        axe.addEnchantment(Enchantment.EFFICIENCY, 1);
        inventory.setItem(3, unbreakable(axe));

        inventory.setItem(4, new ItemStack(wool, 64));
        inventory.setItemInOffHand(new ItemStack(wool, 64));
        player.updateInventory();
    }

    private ItemStack coloredLeather(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack unbreakable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyLegacyCombat(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.setBaseValue(1024.0);
        player.setCooldown(Material.WOODEN_SWORD, 0);
        player.setCooldown(Material.WOODEN_AXE, 0);
        player.setCooldown(Material.WOODEN_PICKAXE, 0);
        player.setCooldown(Material.SHEARS, 0);
    }

    private void startCountdown(ActiveRound round) {
        int configured = Math.max(1, plugin.getConfig().getInt("bedfight.countdown", 5));
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
            first.sendTitle("§e§l" + seconds[0], "§7Preparati al combattimento", 0, 18, 2);
            opponent.sendTitle("§e§l" + seconds[0], "§7Preparati al combattimento", 0, 18, 2);
            float pitch = Math.min(2.0f, 0.9f + (configured - seconds[0]) * 0.12f);
            playSound(first, Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, pitch);
            playSound(opponent, Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, pitch);
            seconds[0]--;
        }, 0L, 20L);
    }

    private void beginPlaying(ActiveRound round) {
        if (activeRound != round || round.finishing) return;
        round.playing = true;
        round.match.setState(BedfightCoinflip.State.ACTIVE);
        for (UUID id : List.of(round.match.getCreator(), round.match.getOpponent())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                applyLegacyCombat(player);
                restoreFullHealthAfterEntry(player);
                player.sendTitle("§a§lVIA!", "§fDistruggi il letto avversario e conquista la vittoria", 0, 35, 10);
                playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.35f);
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f), 8L);
            }
        }
        scheduleDeathmatch(round);
    }

    private void scheduleDeathmatch(ActiveRound round) {
        long bedPhaseSeconds = Math.max(1L,
                plugin.getConfig().getLong("bedfight.beds-auto-destroy-seconds", 300L));
        long damagePhaseDelaySeconds = Math.max(1L,
                plugin.getConfig().getLong("bedfight.deathmatch-damage-delay-seconds", 180L));

        round.bedDestructionTask = Bukkit.getScheduler().runTaskLater(
                plugin, () -> startBedDestructionPhase(round), bedPhaseSeconds * 20L);
        round.deathmatchStartTask = Bukkit.getScheduler().runTaskLater(
                plugin, () -> startDamageDeathmatch(round),
                (bedPhaseSeconds + damagePhaseDelaySeconds) * 20L);
    }

    private void startBedDestructionPhase(ActiveRound round) {
        if (activeRound != round || round.finishing || !round.playing) return;
        round.match.setFirstBedAlive(false);
        round.match.setOpponentBedAlive(false);
        removeBedBlocks(round.firstBed);
        removeBedBlocks(round.opponentBed);

        broadcastRound(round,
                "§6§lDEATHMATCH I §8» §eEntrambi i letti sono stati distrutti. Da questo momento non sono più disponibili respawn.");
        showRoundTitle(round, "§6§lDEATHMATCH", "§cI letti sono stati distrutti");
        playRoundSound(round, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> playRoundSound(round, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.15f), 12L);
    }

    private void startDamageDeathmatch(ActiveRound round) {
        if (activeRound != round || round.finishing || !round.playing) return;
        if (round.match.isFirstBedAlive() || round.match.isOpponentBedAlive()) {
            startBedDestructionPhase(round);
        }

        round.deathmatchDamage = Math.max(0.5,
                plugin.getConfig().getDouble("bedfight.deathmatch-starting-damage", 1.0));
        broadcastRound(round,
                "§4§lDEATHMATCH II §8» §cIl danno globale è iniziato e aumenterà ogni secondo.");
        showRoundTitle(round, "§4§lDEATHMATCH II", "§cIl danno aumenta ogni secondo");
        playRoundSound(round, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.75f);

        round.deathmatchDamageTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeRound != round || round.finishing || !round.playing) {
                cancel(round.deathmatchDamageTask);
                return;
            }

            double damage = round.deathmatchDamage;
            for (UUID playerId : List.of(round.match.getCreator(), round.match.getOpponent())) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || round.respawning.contains(playerId)) continue;

                if (player.getHealth() - damage <= HALF_HEART) {
                    eliminateOrRespawn(player, "deathmatch");
                    if (activeRound != round || round.finishing) return;
                } else {
                    player.setHealth(Math.max(HALF_HEART, player.getHealth() - damage));
                    player.sendActionBar("§4§lDEATHMATCH §8• §c-" + formatHealth(damage)
                            + " HP §8• §7Il danno continua ad aumentare");
                    playSound(player, Sound.ENTITY_BLAZE_HURT, 0.75f, 0.8f);
                }
            }

            double increase = Math.max(0.0,
                    plugin.getConfig().getDouble("bedfight.deathmatch-damage-increase", 1.0));
            round.deathmatchDamage += increase;
        }, 0L, 20L);
    }

    private void removeBedBlocks(Set<BlockKey> bed) {
        for (BlockKey key : bed) {
            Block block = key.getBlock();
            if (block != null) block.setType(Material.AIR, false);
        }
    }

    public boolean canMoveDuringCountdown(Player player, Location from, Location to) {
        if (waitingByCreator.containsKey(player.getUniqueId())) {
            return from == null || to == null || sameCoordinates(from, to);
        }
        ActiveRound round = activeRound;
        if (round == null || round.playing || round.finishing || !round.match.includes(player.getUniqueId())) return true;
        if (from == null || to == null) return true;
        return sameCoordinates(from, to);
    }

    public void recordLastDamager(Player victim, Player attacker) {
        if (victim == null || attacker == null) return;
        ActiveRound round = activeRound;
        if (round == null || !round.playing || round.finishing
                || !round.match.includes(victim.getUniqueId())
                || !round.match.includes(attacker.getUniqueId())
                || round.respawning.contains(victim.getUniqueId())) {
            return;
        }
        lastHits.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    public boolean canDamage(Player attacker, Player victim) {
        ActiveRound round = activeRound;
        if (round == null) return true;
        boolean attackerInRound = attacker != null && round.match.includes(attacker.getUniqueId());
        boolean victimInRound = victim != null && round.match.includes(victim.getUniqueId());
        if (!attackerInRound && !victimInRound) return true;
        if (!round.playing || round.finishing || !attackerInRound || !victimInRound) return false;
        if (round.respawning.contains(attacker.getUniqueId()) || round.respawning.contains(victim.getUniqueId())) return false;
        recordLastDamager(victim, attacker);
        return true;
    }

    public void applyLegacyMeleeHit(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (!isLegacyCombatPair(attacker, victim)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        // L'attributo elimina l'indicatore di ricarica; il danno viene comunque
        // imposto qui per evitare che altri plugin o il calcolo moderno lo riducano.
        applyLegacyCombat(attacker);
        double damage = switch (attacker.getInventory().getItemInMainHand().getType()) {
            case WOODEN_SWORD -> 5.0;
            case WOODEN_AXE -> 4.0;
            case WOODEN_PICKAXE -> 3.0;
            default -> 1.0;
        };
        if (isLegacyCritical(attacker)) damage *= 1.5;
        event.setDamage(damage);
    }

    public void applyLegacyKnockback(
            EntityKnockbackByEntityEvent event,
            Player attacker,
            Player victim) {
        if (!isLegacyCombatPair(attacker, victim)) return;

        Vector direction = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector())
                .setY(0.0);
        double horizontalLength = Math.hypot(direction.getX(), direction.getZ());
        if (horizontalLength < 1.0E-6) {
            direction = event.getFinalKnockback().clone().setY(0.0);
            horizontalLength = Math.hypot(direction.getX(), direction.getZ());
        }

        boolean sprintHit = attacker.isSprinting();
        double horizontalStrength = sprintHit
                ? Math.max(0.0, plugin.getConfig().getDouble(
                "bedfight.combat.sprint-horizontal-knockback", 0.50))
                : Math.max(0.0, plugin.getConfig().getDouble(
                "bedfight.combat.horizontal-knockback", 0.40));
        double horizontalFriction = Math.max(0.0, Math.min(1.0,
                plugin.getConfig().getDouble("bedfight.combat.horizontal-friction", 0.50)));
        Vector previousVelocity = victim.getVelocity();
        Vector knockback = previousVelocity.clone().multiply(horizontalFriction);
        if (horizontalLength >= 1.0E-6) {
            knockback.setX(knockback.getX()
                    + direction.getX() / horizontalLength * horizontalStrength);
            knockback.setZ(knockback.getZ()
                    + direction.getZ() / horizontalLength * horizontalStrength);
        }

        double verticalBoost = Math.max(0.0, plugin.getConfig().getDouble(
                victim.isOnGround()
                        ? "bedfight.combat.vertical-knockback"
                        : "bedfight.combat.airborne-vertical-knockback",
                victim.isOnGround() ? 0.40 : 0.34));
        double verticalFriction = Math.max(0.0, Math.min(1.0,
                plugin.getConfig().getDouble("bedfight.combat.vertical-friction", 0.50)));
        double verticalLimit = Math.max(0.0,
                plugin.getConfig().getDouble("bedfight.combat.vertical-limit", 0.40));
        knockback.setY(Math.min(
                verticalLimit,
                Math.max(0.0, previousVelocity.getY() * verticalFriction + verticalBoost)));
        event.setFinalKnockback(knockback);
        if (sprintHit) attacker.setSprinting(false);
    }

    private boolean isLegacyCombatPair(Player attacker, Player victim) {
        if (attacker == null || victim == null) return false;
        ActiveRound round = activeRound;
        return round != null
                && round.playing
                && !round.finishing
                && round.match.includes(attacker.getUniqueId())
                && round.match.includes(victim.getUniqueId())
                && !round.respawning.contains(attacker.getUniqueId())
                && !round.respawning.contains(victim.getUniqueId());
    }

    private boolean isLegacyCritical(Player attacker) {
        return attacker.getFallDistance() > 0.0f
                && !attacker.isOnGround()
                && !attacker.isInsideVehicle()
                && !attacker.isInWater()
                && !attacker.isSprinting();
    }

    public boolean canTakeDamage(Player player) {
        ActiveRound round = activeRound;
        if (round == null || !round.match.includes(player.getUniqueId())) return true;
        if (!round.playing || round.finishing || round.respawning.contains(player.getUniqueId())) return false;
        return true;
    }

    public boolean handlePotentialElimination(
            Player player,
            double finalDamage,
            EntityDamageEvent.DamageCause cause) {
        if (!isActiveParticipant(player.getUniqueId()) || !isPlaying()) return false;
        if (player.getHealth() - finalDamage > HALF_HEART) return false;
        eliminateOrRespawn(player, "combattimento", cause);
        return true;
    }

    public void handleDeath(Player player) {
        if (isActiveParticipant(player.getUniqueId())) {
            EntityDamageEvent lastDamage = player.getLastDamageCause();
            eliminateOrRespawn(player, "morte", lastDamage == null ? null : lastDamage.getCause());
        }
    }

    public void handleVoidLevel(Player player, Location destination) {
        if (!isActiveParticipant(player.getUniqueId()) || !isPlaying()) return;
        Location checked = destination == null ? player.getLocation() : destination;
        if (checked.getY() <= plugin.getConfig().getDouble("bedfight.void-y", 64.0)) {
            eliminateOrRespawn(player, "void", EntityDamageEvent.DamageCause.VOID);
        }
    }

    private void eliminateOrRespawn(Player player, String reason) {
        eliminateOrRespawn(player, reason, null);
    }

    private void eliminateOrRespawn(
            Player player,
            String reason,
            EntityDamageEvent.DamageCause cause) {
        ActiveRound round = activeRound;
        if (round == null || round.finishing || round.respawning.contains(player.getUniqueId())) return;
        String deathMessage = buildRoundDeathMessage(round, player, reason, cause);
        lastHits.remove(player.getUniqueId());
        if (deathMessage != null) broadcastRound(round, deathMessage);

        boolean bedAlive = isOwnBedAlive(round, player.getUniqueId());
        round.respawning.add(player.getUniqueId());
        player.setHealth(Math.max(1.0, maxHealth(player)));
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        forceRespawnSpectatorMode(player, round);
        playSound(player, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.65f);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.55f), 8L);

        if (!bedAlive) {
            player.sendTitle("§c§lELIMINATO", "§fIl tuo letto è stato distrutto", 0, 25, 5);
            UUID winner = round.match.getOtherParticipant(player.getUniqueId());
            BukkitTask task = Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> {
                        round.respawnCountdownTasks.remove(player.getUniqueId());
                        if (activeRound == round && !round.finishing) {
                            finishRound(winner, false, null);
                        }
                    },
                    20L);
            round.respawnCountdownTasks.put(player.getUniqueId(), task);
            return;
        }

        startRespawnCountdown(player.getUniqueId(), round);
    }

    private void startRespawnCountdown(UUID playerId, ActiveRound expectedRound) {
        long configuredTicks = Math.max(
                20L,
                plugin.getConfig().getLong("bedfight.respawn-delay-ticks", 60L));
        final int[] remaining = {(int) Math.max(1L, (configuredTicks + 19L) / 20L)};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeRound != expectedRound || expectedRound.finishing
                    || !expectedRound.respawning.contains(playerId)) {
                cancel(task[0]);
                expectedRound.respawnCountdownTasks.remove(playerId);
                return;
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                cancel(task[0]);
                expectedRound.respawnCountdownTasks.remove(playerId);
                finishRound(
                        expectedRound.match.getOtherParticipant(playerId),
                        false,
                        "§e" + expectedRound.match.getName(playerId) + " ha lasciato l'arena.");
                return;
            }

            if (remaining[0] <= 0) {
                cancel(task[0]);
                expectedRound.respawnCountdownTasks.remove(playerId);
                respawnParticipant(playerId, expectedRound);
                return;
            }

            player.sendTitle(
                    "§c§lMORTE",
                    "§fRespawn tra §c" + remaining[0] + "§f...",
                    0,
                    22,
                    0);
            float pitch = Math.max(0.5f, Math.min(2.0f, 0.8f + (3 - remaining[0]) * 0.15f));
            playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.75f, pitch);
            remaining[0]--;
        }, 0L, 20L);
    }

    private void respawnParticipant(UUID playerId, ActiveRound expectedRound) {
        if (activeRound != expectedRound || expectedRound.finishing) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            UUID winner = expectedRound.match.getOtherParticipant(playerId);
            finishRound(
                    winner,
                    false,
                    "§e" + expectedRound.match.getName(playerId) + " ha lasciato l'arena.");
            return;
        }
        BukkitTask spectatorTask = expectedRound.respawnSpectatorTasks.remove(playerId);
        cancel(spectatorTask);
        expectedRound.respawning.remove(playerId);
        Location location = getRespawnLocation(playerId);
        prepareFighter(player, location);
        player.sendTitle("§a§lRESPAWN", "", 0, 25, 8);
        playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f);
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
        UUID id = player.getUniqueId();
        if (waitingByCreator.containsKey(id) && !isBedfightWorld(player.getWorld())) {
            waitingByCreator.remove(id);
            awaitingCreateAmount.remove(id);
            restoreWaitingPlayer(player);
            player.sendMessage(PREFIX + "§eCoinflip BedFight annullato.");
            return;
        }

        ActiveRound round = activeRound;
        if (round == null) return;
        if (round.match.includes(id) && !isRoundWorld(player.getWorld())) {
            String leaveMessage = "§e" + player.getName() + " ha lasciato l'arena.";
            if (round.playing) {
                finishRound(
                        round.match.getOtherParticipant(id),
                        false,
                        leaveMessage);
            } else {
                finishRound(null, true, leaveMessage);
            }
            return;
        }
        if (round.spectators.containsKey(id) && !isRoundWorld(player.getWorld())) {
            stopSpectating(player, true);
        }
    }

    public void handleJoin(Player player) {
        UUID id = player.getUniqueId();
        PlayerSnapshot pending = pendingRestores.remove(id);
        if (pending != null) {
            Bukkit.getScheduler().runTask(plugin, () -> restoreSnapshotReliably(id, pending));
            return;
        }
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
            PlayerSnapshot snapshot = waitingSnapshots.remove(id);
            if (snapshot != null) pendingRestores.put(id, snapshot);
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
            String leaveMessage = "§e" + player.getName() + " ha lasciato l'arena.";
            if (round.playing) {
                finishRound(round.match.getOtherParticipant(id), false, leaveMessage);
            } else {
                finishRound(null, true, leaveMessage);
            }
        }
    }

    public boolean handleBlockPlace(Player player, Block block, BlockData replacedData) {
        ActiveRound round = activeRound;
        if (!isProtectedBedfightRound(round, block.getWorld())) return true;
        if (!round.match.includes(player.getUniqueId())) return false;

        int maxHeight = plugin.getConfig().getInt("bedfight.max-build-height", 90);
        if (block.getY() > maxHeight) {
            player.sendMessage(PREFIX + "§cLimite di costruzione raggiunto. §7Non puoi piazzare blocchi sopra Y="
                    + maxHeight + ".");
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            return false;
        }

        BlockKey key = BlockKey.of(block);
        BlockData original = replacedData == null ? Material.AIR.createBlockData() : replacedData.clone();
        round.originalBlocks.putIfAbsent(key, original);
        return true;
    }

    public BreakResult handleBlockBreak(Player player, Block block) {
        ActiveRound round = activeRound;
        if (!isProtectedBedfightRound(round, block.getWorld())) return BreakResult.ALLOW;
        if (!round.match.includes(player.getUniqueId())) return BreakResult.DENY;

        Set<BlockKey> ownBed = ownBedKeys(round, player.getUniqueId());
        Set<BlockKey> enemyBed = enemyBedKeys(round, player.getUniqueId());
        BlockKey key = BlockKey.of(block);
        if (ownBed.contains(key)) {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.65f);
            return BreakResult.OWN_BED;
        }
        if (enemyBed.contains(key)) {
            UUID victimId = round.match.getOtherParticipant(player.getUniqueId());
            if (round.match.getCreator().equals(player.getUniqueId())) {
                round.match.setOpponentBedAlive(false);
            } else {
                round.match.setFirstBedAlive(false);
            }
            for (BlockKey bedKey : enemyBed) {
                Block bedBlock = bedKey.getBlock();
                if (bedBlock != null) bedBlock.setType(Material.AIR, false);
            }
            Player victim = Bukkit.getPlayer(victimId);
            if (victim != null) {
                victim.sendTitle("§c§lLETTO DISTRUTTO", "§fNon puoi più respawnare", 5, 50, 15);
                playSound(victim, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.25f);
            }
            player.sendActionBar("§aLetto avversario distrutto");
            playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.35f);
            return BreakResult.ENEMY_BED;
        }
        if (!isAllowedBreakMaterial(block.getType())) {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return BreakResult.DENY;
        }
        round.originalBlocks.putIfAbsent(key, block.getBlockData().clone());
        return BreakResult.ALLOW;
    }

    public boolean isUndroppableKitItem(ItemStack item) {
        if (item == null) return false;
        return switch (item.getType()) {
            case WOODEN_SWORD, SHEARS, WOODEN_PICKAXE, WOODEN_AXE,
                 LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS -> true;
            default -> false;
        };
    }

    public void showStatus(CommandSender sender) {
        sender.sendMessage("§6SunnyCoinflip BedFight status:");
        sender.sendMessage("§7- enabled: §f" + isEnabled());
        sender.sendMessage("§7- configured: §f" + isArenaConfigured());
        sender.sendMessage("§7- waiting: §f" + waitingByCreator.size());
        sender.sendMessage("§7- active: §f" + (activeRound != null));
        sender.sendMessage("§7- playing: §f" + isPlaying());
    }

    public void abortByAdmin(CommandSender sender) {
        if (activeRound != null) {
            finishRound(null, true, "§cRound interrotto da un amministratore.");
            sender.sendMessage("§aRound BedFight interrotto e importi rimborsati.");
            return;
        }
        for (UUID id : new ArrayList<>(waitingByCreator.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) restoreWaitingPlayer(player);
        }
        waitingByCreator.clear();
        waitingSnapshots.clear();
        sender.sendMessage("§aQueue BedFight svuotate.");
    }

    public void setFirstPosition(Player player) {
        saveLocation("bedfight.first-position", player.getLocation());
        player.sendMessage("§aPosizione First BedFight impostata.");
    }

    public void setOpponentPosition(Player player) {
        saveLocation("bedfight.opponent-position", player.getLocation());
        player.sendMessage("§aPosizione Opponent BedFight impostata.");
    }

    public void setFirstBed(Player player) {
        Block block = targetBed(player);
        if (block == null) {
            player.sendMessage("§cGuarda un letto entro 8 blocchi.");
            return;
        }
        saveLocation("bedfight.first-bed", block.getLocation());
        player.sendMessage("§aLetto First BedFight impostato.");
    }

    public void setOpponentBed(Player player) {
        Block block = targetBed(player);
        if (block == null) {
            player.sendMessage("§cGuarda un letto entro 8 blocchi.");
            return;
        }
        saveLocation("bedfight.opponent-bed", block.getLocation());
        player.sendMessage("§aLetto Opponent BedFight impostato.");
    }

    public Location getFirstPosition() {
        return plugin.getConfig().getLocation("bedfight.first-position");
    }

    public Location getOpponentPosition() {
        return plugin.getConfig().getLocation("bedfight.opponent-position");
    }

    public Location getFirstBedLocation() {
        return plugin.getConfig().getLocation("bedfight.first-bed");
    }

    public Location getOpponentBedLocation() {
        return plugin.getConfig().getLocation("bedfight.opponent-bed");
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
        cancel(round.bedDestructionTask);
        cancel(round.deathmatchStartTask);
        cancel(round.deathmatchDamageTask);
        for (BukkitTask task : round.spectatorTasks.values()) cancel(task);
        for (BukkitTask task : round.respawnSpectatorTasks.values()) cancel(task);
        for (BukkitTask task : round.respawnCountdownTasks.values()) cancel(task);

        if (reason != null) broadcastRound(round, reason);
        if (refund || winnerId == null) {
            economy().depositPlayer(Bukkit.getOfflinePlayer(round.match.getCreator()), round.match.getAmount());
            economy().depositPlayer(Bukkit.getOfflinePlayer(round.match.getOpponent()), round.match.getAmount());
            refundBets(round);
        } else {
            double prize = round.match.getAmount() * 2.0;
            economy().depositPlayer(Bukkit.getOfflinePlayer(winnerId), prize);
            Player winner = Bukkit.getPlayer(winnerId);
            if (winner != null) {
                winner.sendTitle("§6§lVITTORIA", "§fPremio: §r§f\uE0D8 §e" + money(prize), 5, 60, 15);
                playSound(winner, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> playSound(winner, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f), 10L);
            }
            payBets(round, winnerId);
        }

        restoreArena(round);

        // Disattiva il round prima dei teletrasporti di ripristino: gli eventi di cambio mondo
        // non devono riportare i giocatori dentro l'arena appena terminata.
        activeRound = null;

        for (Map.Entry<UUID, PlayerSnapshot> entry : round.snapshots.entrySet()) {
            if (entry.getValue() == null) continue;
            restoreSnapshotReliably(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, PlayerSnapshot> entry : round.spectators.entrySet()) {
            restoreSnapshotReliably(entry.getKey(), entry.getValue());
        }
        lastHits.clear();
    }

    private void restoreSnapshotReliably(UUID playerId, PlayerSnapshot snapshot) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            pendingRestores.put(playerId, snapshot);
            return;
        }

        snapshot.restore(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (canApplyDelayedRestore(playerId, player)) snapshot.restore(player);
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (canApplyDelayedRestore(playerId, player)) snapshot.restorePosition(player);
        }, 10L);
    }

    private boolean canApplyDelayedRestore(UUID playerId, Player player) {
        if (!player.isOnline() || waitingByCreator.containsKey(playerId)) return false;
        ActiveRound current = activeRound;
        return current == null
                || (!current.match.includes(playerId) && !current.spectators.containsKey(playerId));
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
        UUID playerId = player.getUniqueId();
        PlayerSnapshot snapshot = waitingSnapshots.remove(playerId);
        if (snapshot == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> restoreSnapshotReliably(playerId, snapshot));
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

    private String buildRoundDeathMessage(
            ActiveRound round,
            Player player,
            String reason,
            EntityDamageEvent.DamageCause cause) {
        if (!isBedfightWorld(player.getWorld())) return null;

        LastHit hit = lastHits.get(player.getUniqueId());
        boolean recentHit = hit != null && System.currentTimeMillis() - hit.at() <= 10_000L;
        String attackerName = recentHit ? round.match.getName(hit.attacker()) : null;

        if ("void".equals(reason)) {
            if (attackerName != null) {
                return "§c☠ §f" + player.getName() + " §7è stato spinto nel vuoto da §f"
                        + attackerName + "§7.";
            }
            return "§c☠ §f" + player.getName() + " §7è caduto nel vuoto.";
        }
        if ("deathmatch".equals(reason)) {
            return "§c☠ §f" + player.getName() + " §7è stato eliminato dal deathmatch.";
        }
        if (attackerName != null) {
            return "§c☠ §f" + player.getName() + " §7è stato ucciso da §f" + attackerName + "§7.";
        }
        if (cause == EntityDamageEvent.DamageCause.FALL) {
            return "§c☠ §f" + player.getName() + " §7è morto per una caduta.";
        }
        if (cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            return "§c☠ §f" + player.getName() + " §7è morto tra le fiamme.";
        }
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return "§c☠ §f" + player.getName() + " §7è esploso.";
        }
        if (cause == EntityDamageEvent.DamageCause.DROWNING) {
            return "§c☠ §f" + player.getName() + " §7è annegato.";
        }
        if (cause == EntityDamageEvent.DamageCause.SUFFOCATION) {
            return "§c☠ §f" + player.getName() + " §7è soffocato.";
        }
        return "§c☠ §f" + player.getName() + " §7è stato eliminato.";
    }

    private boolean isBedfightWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(BEDFIGHT_WORLD);
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

    private boolean isAllowedBreakMaterial(Material material) {
        return material == Material.END_STONE
                || material == Material.OAK_PLANKS
                || material == Material.OAK_LOG
                || material == Material.OAK_WOOD
                || material.name().endsWith("_WOOL");
    }

    private boolean isProtectedBedfightRound(ActiveRound round, World world) {
        return round != null
                && round.playing
                && !round.finishing
                && world != null
                && world.getName().equalsIgnoreCase(BEDFIGHT_WORLD);
    }

    private boolean isFirstTeam(UUID playerId) {
        ActiveRound round = activeRound;
        return round != null && round.match.getCreator().equals(playerId);
    }

    private void broadcastRound(ActiveRound round, String message) {
        Set<UUID> recipients = new HashSet<>();
        recipients.add(round.match.getCreator());
        recipients.add(round.match.getOpponent());
        recipients.addAll(round.spectators.keySet());
        for (UUID id : recipients) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(PREFIX + message);
        }
    }

    private void showRoundTitle(ActiveRound round, String title, String subtitle) {
        for (UUID id : List.of(round.match.getCreator(), round.match.getOpponent())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendTitle(title, subtitle, 5, 50, 15);
        }
    }

    private void playRoundSound(ActiveRound round, Sound sound, float volume, float pitch) {
        Set<UUID> recipients = new HashSet<>();
        recipients.add(round.match.getCreator());
        recipients.add(round.match.getOpponent());
        recipients.addAll(round.spectators.keySet());
        for (UUID id : recipients) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) playSound(player, sound, volume, pitch);
        }
    }

    private void playSound(Player player, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private String formatHealth(double value) {
        return String.format(Locale.US, "%.1f", value);
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
        private final Map<UUID, BukkitTask> respawnSpectatorTasks = new HashMap<>();
        private final Map<UUID, BukkitTask> respawnCountdownTasks = new HashMap<>();
        private final Set<UUID> respawning = new HashSet<>();
        private final Map<UUID, Map<UUID, Double>> bets = new HashMap<>();
        private BukkitTask countdownTask;
        private BukkitTask bedDestructionTask;
        private BukkitTask deathmatchStartTask;
        private BukkitTask deathmatchDamageTask;
        private double deathmatchDamage;
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
        private final Double attackSpeed;

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
            AttributeInstance speed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            attackSpeed = speed == null ? null : speed.getBaseValue();
        }

        private static PlayerSnapshot capture(Player player) {
            return new PlayerSnapshot(player);
        }

        private void restore(Player player) {
            restorePosition(player);
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
            AttributeInstance speed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (speed != null && attackSpeed != null) speed.setBaseValue(attackSpeed);
        }

        private void restorePosition(Player player) {
            if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
            if (location.getWorld() != null) player.teleport(location.clone());
            player.setGameMode(gameMode);
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
