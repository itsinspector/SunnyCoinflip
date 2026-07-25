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
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        SunnyCoinflip plugin = SunnyCoinflip.getInstance();
        if (args.length <= 0 || !this.isBedfightAlias(args[0])) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Solo i giocatori possono usare questo comando.");
                return true;
            } else {
                Player player = (Player)sender;
                if (args.length == 1 && args[0].equalsIgnoreCase("pillars")) {
                    PillarMatch match = plugin.getGameManager().getActivePillarMatch();
                    if (match != null && match.isPlaying()) {
                        PillarSpectatorManager.get(plugin).startSpectating(player);
                        return true;
                    } else {
                        if (!plugin.getGameManager().isRestrictedWorld(player.getWorld())) {
                            plugin.getInventoryListener().handlePillarsSelection(player);
                        }

                        return true;
                    }
                } else if (plugin.getGameManager().isRestrictedWorld(player.getWorld())) {
                    player.sendMessage("§cNon puoi usare questo comando!");
                    return true;
                } else {
                    if (args.length > 0) {
                        if (args[0].equalsIgnoreCase("classici")) {
                            if (args.length > 2 && args[1].equalsIgnoreCase("create")) {
                                plugin.getChatListener().createCoinflipDirect(player, this.joinArgs(args, 2));
                            } else {
                                plugin.getInventoryListener().openNormalCoinflipMenu(player, 0);
                            }

                            return true;
                        }

                        if (args[0].equalsIgnoreCase("pillars")) {
                            if (args.length > 2 && args[1].equalsIgnoreCase("create")) {
                                plugin.getChatListener().createPillarDirect(player, this.joinArgs(args, 2));
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

                    player.sendMessage("§cUtilizzo: /cf classici, /cf pillars, /cf bedfight o /cf info");
                    return true;
                }
            }
        } else {
            return this.handleBedfight(sender, args, plugin);
        }
    }

    private boolean handleBedfight(CommandSender sender, String[] args, SunnyCoinflip plugin) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("status")) {
            plugin.getBedfightManager().showStatus(sender);
            return true;
        } else if (args.length >= 2 && args[1].equalsIgnoreCase("abort")) {
            if (!this.isAdmin(sender)) {
                sender.sendMessage("§cNon hai il permesso sunnycoinflip.admin.");
                return true;
            } else {
                plugin.getBedfightManager().abortByAdmin(sender);
                return true;
            }
        } else if (sender instanceof Player) {
            Player player = (Player)sender;
            if (args.length >= 2 && this.isSetupSubcommand(args[1])) {
                if (!this.isAdmin(player)) {
                    player.sendMessage("§cNon hai il permesso sunnycoinflip.admin.");
                    return true;
                } else {
                    return this.handleSetup(player, args[1], plugin);
                }
            } else if (plugin.getGameManager().isRestrictedWorld(player.getWorld())) {
                player.sendMessage("§cNon puoi usare questo comando nel mondo Pillars.");
                return true;
            } else if (args.length == 1) {
                plugin.getBedfightManager().handleSimpleCommand(player);
                return true;
            } else if (args[1].equalsIgnoreCase("list")) {
                plugin.getBedfightManager().listChallenges(player);
                this.sendBedfightUsage(player);
                return true;
            } else if (args[1].equalsIgnoreCase("create")) {
                if (args.length < 3) {
                    player.sendMessage("§cUtilizzo: /cf bedfight create <somma>");
                    return true;
                } else {
                    try {
                        double amount = NumberParser.parseNumber(this.joinArgs(args, 2));
                        plugin.getBedfightManager().createChallenge(player, amount);
                    } catch (IllegalArgumentException exception) {
                        player.sendMessage("§c" + exception.getMessage());
                    }

                    return true;
                }
            } else if (args[1].equalsIgnoreCase("accept")) {
                if (args.length < 3) {
                    player.sendMessage("§cUtilizzo: /cf bedfight accept <creatore>");
                    return true;
                } else {
                    plugin.getBedfightManager().acceptChallenge(player, args[2]);
                    return true;
                }
            } else if (args[1].equalsIgnoreCase("cancel")) {
                plugin.getBedfightManager().cancelWaiting(player);
                return true;
            } else if (!args[1].equalsIgnoreCase("bet") && !args[1].equalsIgnoreCase("scommetti")) {
                plugin.getBedfightManager().acceptChallenge(player, args[1]);
                return true;
            } else if (args.length < 4) {
                player.sendMessage("§cUtilizzo: /cf bedfight bet <giocatore|first|opponent> <somma>");
                return true;
            } else {
                try {
                    double amount = NumberParser.parseNumber(this.joinArgs(args, 3));
                    plugin.getBedfightManager().placeBet(player, args[2], amount);
                } catch (IllegalArgumentException exception) {
                    player.sendMessage("§c" + exception.getMessage());
                }

                return true;
            }
        } else {
            sender.sendMessage("§cUso console: /cf bedfight status oppure /cf bedfight abort");
            return true;
        }
    }

    private boolean handleSetup(Player player, String subcommand, SunnyCoinflip plugin) {
        if (subcommand.equalsIgnoreCase("setfirstpos")) {
            plugin.getBedfightManager().setFirstPosition(player);
            return true;
        } else if (subcommand.equalsIgnoreCase("setopponentpos")) {
            plugin.getBedfightManager().setOpponentPosition(player);
            return true;
        } else if (subcommand.equalsIgnoreCase("setfirstbed")) {
            plugin.getBedfightManager().setFirstBed(player);
            return true;
        } else if (subcommand.equalsIgnoreCase("setopponentbed")) {
            plugin.getBedfightManager().setOpponentBed(player);
            return true;
        } else {
            return true;
        }
    }

    private boolean isSetupSubcommand(String value) {
        return value.equalsIgnoreCase("setfirstpos") || value.equalsIgnoreCase("setopponentpos") || value.equalsIgnoreCase("setfirstbed") || value.equalsIgnoreCase("setopponentbed");
    }

    private boolean isAdmin(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("sunnycoinflip.admin");
    }

    private boolean isBedfightAlias(String value) {
        return value.equalsIgnoreCase("bedfight")
                || value.equalsIgnoreCase("bw")
                || value.equalsIgnoreCase("bf");
    }

    private void sendBedfightUsage(Player player) {
        player.sendMessage("§7Comandi BedFight: §e/cf bedfight create <somma>§7, §e/cf bedfight accept <creatore>§7, §e/cf bedfight cancel§7, §e/cf bedfight status§7, §e/cf bedfight bet <giocatore> <somma>§7.");
        if (player.hasPermission("sunnycoinflip.admin")) {
            player.sendMessage("§7Setup: §e/cf bedfight setfirstpos§7, §esetopponentpos§7, §esetfirstbed§7, §esetopponentbed§7, §eabort§7.");
        }

    }

    private String joinArgs(String[] args, int startIndex) {
        StringBuilder joined = new StringBuilder();

        for(int i = startIndex; i < args.length; ++i) {
            if (i > startIndex) {
                joined.append(' ');
            }

            joined.append(args[i]);
        }

        return joined.toString();
    }
}
