package org.ItsInspector.sunnyCoinflip.commands;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.managers.PillarSpectatorManager;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.NumberParser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CoinflipCommand implements CommandExecutor {
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        SunnyCoinflip plugin = SunnyCoinflip.getInstance();
        if (args.length > 0 && (args[0].equalsIgnoreCase("bedwars") || args[0].equalsIgnoreCase("bedfight"))) {
            return handleBedwars(sender, args, plugin);
        }
        if (!(sender instanceof Player player)) return true;
        if (args.length == 1 && args[0].equalsIgnoreCase("pillars")) {
            PillarMatch match = plugin.getGameManager().getActivePillarMatch();
            if (match != null && match.isPlaying()) PillarSpectatorManager.get(plugin).startSpectating(player);
            else plugin.getInventoryListener().handlePillarsSelection(player);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("classici") && args[1].equalsIgnoreCase("create")) {
            plugin.getChatListener().createCoinflipDirect(player, join(args, 2));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("pillars") && args[1].equalsIgnoreCase("create")) {
            plugin.getChatListener().createPillarDirect(player, join(args, 2));
            return true;
        }
        plugin.getInventoryListener().openNormalCoinflipMenu(player, 0);
        return true;
    }

    private boolean handleBedwars(CommandSender sender, String[] args, SunnyCoinflip plugin) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("status")) { plugin.getBedfightManager().showStatus(sender); return true; }
        if (args.length >= 2 && args[1].equalsIgnoreCase("abort")) {
            if (!isAdmin(sender)) sender.sendMessage("§cNon hai il permesso sunnycoinflip.admin.");
            else plugin.getBedfightManager().abortByAdmin(sender);
            return true;
        }
        if (!(sender instanceof Player player)) return true;
        if (args.length >= 2 && isSetup(args[1])) {
            if (!isAdmin(player)) { player.sendMessage("§cNon hai il permesso sunnycoinflip.admin."); return true; }
            switch (args[1].toLowerCase()) {
                case "setfirstpos" -> plugin.getBedfightManager().setFirstPosition(player);
                case "setopponentpos" -> plugin.getBedfightManager().setOpponentPosition(player);
                case "setfirstbed" -> plugin.getBedfightManager().setFirstBed(player);
                case "setopponentbed" -> plugin.getBedfightManager().setOpponentBed(player);
            }
            return true;
        }
        if (args.length == 1) { plugin.getBedfightManager().handleSimpleCommand(player); return true; }
        switch (args[1].toLowerCase()) {
            case "list" -> plugin.getBedfightManager().listChallenges(player);
            case "cancel" -> plugin.getBedfightManager().cancelWaiting(player);
            case "create" -> {
                if (args.length < 3) player.sendMessage("§cUtilizzo: /cf bedwars create <somma>");
                else try { plugin.getBedfightManager().createChallenge(player, NumberParser.parseNumber(join(args, 2))); }
                catch (IllegalArgumentException exception) { player.sendMessage("§c" + exception.getMessage()); }
            }
            case "accept" -> {
                if (args.length < 3) player.sendMessage("§cUtilizzo: /cf bedwars accept <player>");
                else plugin.getBedfightManager().acceptChallenge(player, args[2]);
            }
            case "bet", "scommetti" -> {
                if (args.length < 4) player.sendMessage("§cUtilizzo: /cf bedwars bet <player> <somma>");
                else try { plugin.getBedfightManager().placeBet(player, args[2], NumberParser.parseNumber(join(args, 3))); }
                catch (IllegalArgumentException exception) { player.sendMessage("§c" + exception.getMessage()); }
            }
            default -> plugin.getBedfightManager().acceptChallenge(player, args[1]);
        }
        return true;
    }

    private boolean isSetup(String value) {
        return value.equalsIgnoreCase("setfirstpos") || value.equalsIgnoreCase("setopponentpos") ||
                value.equalsIgnoreCase("setfirstbed") || value.equalsIgnoreCase("setopponentbed");
    }
    private boolean isAdmin(CommandSender sender) { return !(sender instanceof Player) || sender.hasPermission("sunnycoinflip.admin"); }
    private String join(String[] args, int start) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < args.length; i++) { if (i > start) result.append(' '); result.append(args[i]); }
        return result.toString();
    }
}
