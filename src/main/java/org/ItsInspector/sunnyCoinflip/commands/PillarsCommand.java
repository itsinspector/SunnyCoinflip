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
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (args.length == 3 && args[0].equalsIgnoreCase("scommetti")) {
            PillarMatch match = SunnyCoinflip.getInstance().getGameManager().getActivePillarMatch();
            if (match == null) {
                player.sendMessage("§cNon c'è nessuna partita di Pillars attiva!");
                return true;
            }

            if (match.isPlaying()) {
                player.sendMessage("§cLa partita è già iniziata, non puoi più scommettere!");
                return true;
            }

            if (player.getUniqueId().equals(match.getCreator()) || (match.getOpponent() != null && player.getUniqueId().equals(match.getOpponent()))) {
                player.sendMessage("§cNon puoi scommettere se sei un partecipante!");
                return true;
            }
            
            if (SunnyCoinflip.getInstance().getGameManager().isPillarWorld(player.getWorld())) {
                 player.sendMessage("§cNon puoi scommettere se sei nel mondo dei Pillars!");
                 return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || (!target.getUniqueId().equals(match.getCreator()) && (match.getOpponent() == null || !target.getUniqueId().equals(match.getOpponent())))) {
                player.sendMessage("§cIl giocatore specificato non è un partecipante del match attivo!");
                return true;
            }

            double amount;
            try {
                amount = NumberParser.parseNumber(args[2]);
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c" + e.getMessage());
                return true;
            }

            if (amount <= 0) {
                player.sendMessage("§cImporto deve essere maggiore di 0!");
                return true;
            }

            if (SunnyCoinflip.getEconomy().getBalance(player) < amount) {
                player.sendMessage("§cNon hai abbastanza soldi!");
                return true;
            }

            if (target.getUniqueId().equals(match.getCreator())) {
                if (match.getOpponentBets().containsKey(player.getUniqueId())) {
                    player.sendMessage("§cHai già scommesso sull'altro partecipante! Puoi scommettere solo su una persona a round.");
                    return true;
                }
                match.getCreatorBets().put(player.getUniqueId(), match.getCreatorBets().getOrDefault(player.getUniqueId(), 0.0) + amount);
            } else {
                if (match.getCreatorBets().containsKey(player.getUniqueId())) {
                    player.sendMessage("§cHai già scommesso sull'altro partecipante! Puoi scommettere solo su una persona a round.");
                    return true;
                }
                match.getOpponentBets().put(player.getUniqueId(), match.getOpponentBets().getOrDefault(player.getUniqueId(), 0.0) + amount);
            }

            SunnyCoinflip.getEconomy().withdrawPlayer(player, amount);
            player.sendMessage("§aHai scommesso §f\uE0D8 §e" + amount + "§a su §f" + target.getName() + "§a!");
            return true;
        }

        player.sendMessage("§cUtilizzo: /pillars scommetti (player) (somma)");
        return true;
    }
}
