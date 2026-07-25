package org.ItsInspector.sunnyCoinflip.commands;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.NumberParser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PillarsCommand implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        } else if (args.length == 3 && args[0].equalsIgnoreCase("scommetti")) {
            PillarMatch match = SunnyCoinflip.getInstance().getGameManager().getActivePillarMatch();
            if (match == null) {
                player.sendMessage("§cNon c'è nessuna partita di Pillars attiva!");
                return true;
            } else if (match.isPlaying()) {
                player.sendMessage("§cLa partita è già iniziata, non puoi più scommettere!");
                return true;
            } else if (!player.getUniqueId().equals(match.getCreator()) && (match.getOpponent() == null || !player.getUniqueId().equals(match.getOpponent()))) {
                if (SunnyCoinflip.getInstance().getGameManager().isPillarWorld(player.getWorld())) {
                    player.sendMessage("§cNon puoi scommettere se sei nel mondo dei Pillars!");
                    return true;
                } else {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null && (target.getUniqueId().equals(match.getCreator()) || match.getOpponent() != null && target.getUniqueId().equals(match.getOpponent()))) {
                        double amount;
                        try {
                            amount = NumberParser.parseNumber(args[2]);
                        } catch (IllegalArgumentException e) {
                            player.sendMessage("§c" + e.getMessage());
                            return true;
                        }

                        if (amount <= (double)0.0F) {
                            player.sendMessage("§cImporto deve essere maggiore di 0!");
                            return true;
                        } else if (SunnyCoinflip.getEconomy().getBalance(player) < amount) {
                            player.sendMessage("§cNon hai abbastanza soldi!");
                            return true;
                        } else {
                            if (target.getUniqueId().equals(match.getCreator())) {
                                if (match.getOpponentBets().containsKey(player.getUniqueId())) {
                                    player.sendMessage("§cHai già scommesso sull'altro partecipante! Puoi scommettere solo su una persona a round.");
                                    return true;
                                }

                                match.getCreatorBets().put(player.getUniqueId(), (Double)match.getCreatorBets().getOrDefault(player.getUniqueId(), (double)0.0F) + amount);
                            } else {
                                if (match.getCreatorBets().containsKey(player.getUniqueId())) {
                                    player.sendMessage("§cHai già scommesso sull'altro partecipante! Puoi scommettere solo su una persona a round.");
                                    return true;
                                }

                                match.getOpponentBets().put(player.getUniqueId(), (Double)match.getOpponentBets().getOrDefault(player.getUniqueId(), (double)0.0F) + amount);
                            }

                            SunnyCoinflip.getEconomy().withdrawPlayer(player, amount);
                            player.sendMessage("§aHai scommesso §f\ue0d8 §e" + amount + "§a su §f" + target.getName() + "§a!");
                            return true;
                        }
                    } else {
                        player.sendMessage("§cIl giocatore specificato non è un partecipante del match attivo!");
                        return true;
                    }
                }
            } else {
                player.sendMessage("§cNon puoi scommettere se sei un partecipante!");
                return true;
            }
        } else {
            player.sendMessage("§cUtilizzo: /pillars scommetti (player) (somma)");
            return true;
        }
    }
}
