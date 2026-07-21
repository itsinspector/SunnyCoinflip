package org.ItsInspector.sunnyCoinflip.commands;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PillarSetupCommands implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        
        if (!player.hasPermission("sunnycoinflip.admin")) {
            player.sendMessage("§cNon hai i permessi per usare questo comando.");
            return true;
        }

        if (label.equalsIgnoreCase("setpillarsfirst")) {
            SunnyCoinflip.getInstance().getGameManager().setPillarFirst(player.getLocation());
            player.sendMessage("§aPosizione del primo pillar impostata!");
        } else if (label.equalsIgnoreCase("setpillarsopponent")) {
            SunnyCoinflip.getInstance().getGameManager().setPillarOpponent(player.getLocation());
            player.sendMessage("§aPosizione dello sfidante impostata!");
        }

        return true;
    }
}