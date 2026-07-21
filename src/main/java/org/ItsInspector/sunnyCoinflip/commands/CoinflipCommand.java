package org.ItsInspector.sunnyCoinflip.commands;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.managers.PillarSpectatorManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoinflipCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo i giocatori possono usare questo comando.");
            return true;
        }

        SunnyCoinflip plugin = SunnyCoinflip.getInstance();

        /*
         * Questo controllo va fatto prima del blocco dei mondi ristretti:
         * permette a /cf pillars di gestire correttamente uno spettatore già attivo.
         */
        if (args.length == 1 && args[0].equalsIgnoreCase("pillars")) {
            org.ItsInspector.sunnyCoinflip.models.PillarMatch match =
                    plugin.getGameManager().getActivePillarMatch();

            if (match != null && match.isPlaying()) {
                PillarSpectatorManager.get(plugin).startSpectating(player);
                return true;
            }

            // Se non c'è un round iniziato, mantiene il comportamento originale e apre il menu.
            if (!plugin.getGameManager().isRestrictedWorld(player.getWorld())) {
                plugin.getInventoryListener().handlePillarsSelection(player);
            }
            return true;
        }

        if (plugin.getGameManager().isRestrictedWorld(player.getWorld())) {
            player.sendMessage("§cNon puoi usare questo comando!");
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("classici")) {
                if (args.length > 2 && args[1].equalsIgnoreCase("create")) {
                    StringBuilder amountStr = new StringBuilder();
                    for (int i = 2; i < args.length; i++) {
                        if (i > 2) amountStr.append(' ');
                        amountStr.append(args[i]);
                    }
                    plugin.getChatListener().createCoinflipDirect(player, amountStr.toString());
                } else {
                    plugin.getInventoryListener().openNormalCoinflipMenu(player, 0);
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("pillars")) {
                if (args.length > 2 && args[1].equalsIgnoreCase("create")) {
                    StringBuilder amountStr = new StringBuilder();
                    for (int i = 2; i < args.length; i++) {
                        if (i > 2) amountStr.append(' ');
                        amountStr.append(args[i]);
                    }
                    plugin.getChatListener().createPillarDirect(player, amountStr.toString());
                } else {
                    plugin.getInventoryListener().handlePillarsSelection(player);
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("info")) {
                player.sendMessage("§7Made with §c❤ §7by §f§lItsInspector§a...");
                return true;
            }
        }

        player.sendMessage("§cUtilizzo: /cf classici [create <somma>], /cf pillars [create <somma>] o /cf info");
        return true;
    }
}
