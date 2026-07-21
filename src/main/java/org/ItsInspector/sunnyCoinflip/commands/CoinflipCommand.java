package org.ItsInspector.sunnyCoinflip.commands;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class CoinflipCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Solo i giocatori possono usare questo comando.");
            return true;
        }

        Player player = (Player) sender;

        if (SunnyCoinflip.getInstance().getGameManager().isRestrictedWorld(player.getWorld())) {
            player.sendMessage("§cNon puoi usare questo comando!");
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("classici")) {
                if (args.length > 1 && args[1].equalsIgnoreCase("create") && args.length > 2) {
                    StringBuilder amountStr = new StringBuilder();
                    for (int i = 2; i < args.length; i++) {
                        if (i > 2) amountStr.append(" ");
                        amountStr.append(args[i]);
                    }
                    SunnyCoinflip.getInstance().getChatListener().createCoinflipDirect(player, amountStr.toString());
                } else {
                    SunnyCoinflip.getInstance().getInventoryListener().openNormalCoinflipMenu(player, 0);
                }
                return true;
            } else if (args[0].equalsIgnoreCase("pillars")) {
                if (args.length > 1 && args[1].equalsIgnoreCase("create") && args.length > 2) {
                    StringBuilder amountStr = new StringBuilder();
                    for (int i = 2; i < args.length; i++) {
                        if (i > 2) amountStr.append(" ");
                        amountStr.append(args[i]);
                    }
                    SunnyCoinflip.getInstance().getChatListener().createPillarDirect(player, amountStr.toString());
                } else {
                    SunnyCoinflip.getInstance().getInventoryListener().handlePillarsSelection(player);
                }
                return true;
            } else if (args[0].equalsIgnoreCase("info")) {
                player.sendMessage("&7Made with &c❤&7 by &f&lItsInspector&a...");
                return true;
            }
        }

        player.sendMessage("§cUtilizzo: /cf classici [create <somma>], /cf pillars [create <somma>] o /cf info");
        return true;
    }
}