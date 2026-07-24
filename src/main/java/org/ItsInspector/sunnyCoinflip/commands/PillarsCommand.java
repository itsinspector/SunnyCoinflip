package org.ItsInspector.sunnyCoinflip.commands;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.NumberParser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PillarsCommand implements CommandExecutor {
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length != 3 || !args[0].equalsIgnoreCase("scommetti")) {
            player.sendMessage("§cUtilizzo: /pillars scommetti (player) (somma)");
            return true;
        }
        PillarMatch match = SunnyCoinflip.getInstance().getGameManager().getActivePillarMatch();
        if (match == null || match.isPlaying()) {
            player.sendMessage("§cNon puoi scommettere in questo momento.");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || (!target.getUniqueId().equals(match.getCreator()) && !target.getUniqueId().equals(match.getOpponent()))) {
            player.sendMessage("§cIl giocatore non partecipa al match.");
            return true;
        }
        double amount;
        try { amount = NumberParser.parseNumber(args[2]); }
        catch (IllegalArgumentException exception) { player.sendMessage("§c" + exception.getMessage()); return true; }
        if (amount <= 0 || SunnyCoinflip.getEconomy().getBalance(player) < amount) {
            player.sendMessage("§cImporto non valido o saldo insufficiente.");
            return true;
        }
        SunnyCoinflip.getEconomy().withdrawPlayer(player, amount);
        if (target.getUniqueId().equals(match.getCreator())) match.getCreatorBets().merge(player.getUniqueId(), amount, Double::sum);
        else match.getOpponentBets().merge(player.getUniqueId(), amount, Double::sum);
        player.sendMessage("§aScommessa registrata su §f" + target.getName() + "§a.");
        return true;
    }
}
