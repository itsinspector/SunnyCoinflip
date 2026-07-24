package org.ItsInspector.sunnyCoinflip.commands;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.managers.PillarSpectatorManager;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.NumberParser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoinflipCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        SunnyCoinflip plugin = SunnyCoinflip.getInstance();

        // /cf bedwars è il comando principale. /cf bedfight resta un alias compatibile.
        if (args.length > 0 && (args[0].equalsIgnoreCase("bedwars")
                || args[0].equalsIgnoreCase("bedfight"))) {
            return handleBedwars(sender, args, plugin);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo i giocatori possono usare questo comando.");
            return true;
        }

        /* Permette agli spettatori di entrare nei Pillars prima del blocco mondo. */
        if (args.length == 1 && args[0].equalsIgnoreCase("pillars")) {
            PillarMatch match = plugin.getGameManager().getActivePillarMatch();
            if (match != null && match.isPlaying()) {
                PillarSpectatorManager.get(plugin).startSpectating(player);
                return true;
            }
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
                    plugin.getChatListener().createCoinflipDirect(player, joinArgs(args, 2));
                } else {
                    plugin.getInventoryListener().openNormalCoinflipMenu(player, 0);
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("pillars")) {
                if (args.length > 2 && args[1].equalsIgnoreCase("create")) {
                    plugin.getChatListener().createPillarDirect(player, joinArgs(args, 2));
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

        player.sendMessage("§cUtilizzo: /cf classici, /cf pillars, /cf bedwars o /cf info");
        return true;
    }

    private boolean handleBedwars(CommandSender sender, String[] args, SunnyCoinflip plugin) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("status")) {
            plugin.getBedfightManager().showStatus(sender);
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("abort")) {
            if (!isAdmin(sender)) {
                sender.sendMessage("§cNon hai il permesso sunnycoinflip.admin.");
                return true;
            }
            plugin.getBedfightManager().abortByAdmin(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cUso console: /cf bedwars status oppure /cf bedwars abort");
            return true;
        }

        if (args.length >= 2 && isSetupSubcommand(args[1])) {
            if (!isAdmin(player)) {
                player.sendMessage("§cNon hai il permesso sunnycoinflip.admin.");
                return true;
            }
            return handleSetup(player, args[1], plugin);
        }

        if (plugin.getGameManager().isRestrictedWorld(player.getWorld())) {
            player.sendMessage("§cNon puoi usare questo comando nel mondo Pillars.");
            return true;
        }

        if (args.length == 1) {
            plugin.getBedfightManager().handleSimpleCommand(player);
            return true;
        }

        if (args[1].equalsIgnoreCase("list")) {
            plugin.getBedfightManager().listChallenges(player);
            sendBedwarsUsage(player);
            return true;
        }

        if (args[1].equalsIgnoreCase("create")) {
            if (args.length < 3) {
                player.sendMessage("§cUtilizzo: /cf bedwars create <somma>");
                return true;
            }
            try {
                double amount = NumberParser.parseNumber(joinArgs(args, 2));
                plugin.getBedfightManager().createChallenge(player, amount);
            } catch (IllegalArgumentException exception) {
                player.sendMessage("§c" + exception.getMessage());
            }
            return true;
        }

        if (args[1].equalsIgnoreCase("accept")) {
            if (args.length < 3) {
                player.sendMessage("§cUtilizzo: /cf bedwars accept <creatore>");
                return true;
            }
            plugin.getBedfightManager().acceptChallenge(player, args[2]);
            return true;
        }

        if (args[1].equalsIgnoreCase("cancel")) {
            plugin.getBedfightManager().cancelWaiting(player);
            return true;
        }

        if (args[1].equalsIgnoreCase("bet") || args[1].equalsIgnoreCase("scommetti")) {
            if (args.length < 4) {
                player.sendMessage("§cUtilizzo: /cf bedwars bet <giocatore|first|opponent> <somma>");
                return true;
            }
            try {
                double amount = NumberParser.parseNumber(joinArgs(args, 3));
                plugin.getBedfightManager().placeBet(player, args[2], amount);
            } catch (IllegalArgumentException exception) {
                player.sendMessage("§c" + exception.getMessage());
            }
            return true;
        }

        // Scorciatoia: /cf bedwars <creatore>
        plugin.getBedfightManager().acceptChallenge(player, args[1]);
        return true;
    }

    private boolean handleSetup(Player player, String subcommand, SunnyCoinflip plugin) {
        if (subcommand.equalsIgnoreCase("setfirstpos")) {
            plugin.getBedfightManager().setFirstPosition(player);
            return true;
        }
        if (subcommand.equalsIgnoreCase("setopponentpos")) {
            plugin.getBedfightManager().setOpponentPosition(player);
            return true;
        }
        if (subcommand.equalsIgnoreCase("setfirstbed")) {
            plugin.getBedfightManager().setFirstBed(player);
            return true;
        }
        if (subcommand.equalsIgnoreCase("setopponentbed")) {
            plugin.getBedfightManager().setOpponentBed(player);
            return true;
        }
        return true;
    }

    private boolean isSetupSubcommand(String value) {
        return value.equalsIgnoreCase("setfirstpos")
                || value.equalsIgnoreCase("setopponentpos")
                || value.equalsIgnoreCase("setfirstbed")
                || value.equalsIgnoreCase("setopponentbed");
    }

    private boolean isAdmin(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("sunnycoinflip.admin");
    }

    private void sendBedwarsUsage(Player player) {
        player.sendMessage("§7Comandi BedWars: §e/cf bedwars create <somma>§7, "
                + "§e/cf bedwars accept <creatore>§7, §e/cf bedwars cancel§7, §e/cf bedwars status§7, "
                + "§e/cf bedwars bet <giocatore> <somma>§7.");
        if (player.hasPermission("sunnycoinflip.admin")) {
            player.sendMessage("§7Setup: §e/cf bedwars setfirstpos§7, §esetopponentpos§7, "
                    + "§esetfirstbed§7, §esetopponentbed§7, §eabort§7.");
        }
    }

    private String joinArgs(String[] args, int startIndex) {
        StringBuilder joined = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                joined.append(' ');
            }
            joined.append(args[i]);
        }
        return joined.toString();
    }
}
