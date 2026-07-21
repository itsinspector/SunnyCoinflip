package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashSet;
import java.util.Set;

public class CommandBlockListener implements Listener {

    private static final Set<String> BLOCKED_COMMANDS = new HashSet<>();

    static {
        BLOCKED_COMMANDS.add("shop");
        BLOCKED_COMMANDS.add("mercato");
        BLOCKED_COMMANDS.add("sellgui");
        BLOCKED_COMMANDS.add("sellall");
        BLOCKED_COMMANDS.add("sell");
        BLOCKED_COMMANDS.add("kit");
        BLOCKED_COMMANDS.add("auctionhouse");
        BLOCKED_COMMANDS.add("ah");
        BLOCKED_COMMANDS.add("betterrtp");
        BLOCKED_COMMANDS.add("land");
        BLOCKED_COMMANDS.add("claim");
        BLOCKED_COMMANDS.add("order");
        BLOCKED_COMMANDS.add("orders");
        BLOCKED_COMMANDS.add("trade");
        BLOCKED_COMMANDS.add("trades");
        BLOCKED_COMMANDS.add("sethome");
        BLOCKED_COMMANDS.add("home");
        BLOCKED_COMMANDS.add("tpa");
        BLOCKED_COMMANDS.add("tpahere");
        BLOCKED_COMMANDS.add("tpaccept");
        BLOCKED_COMMANDS.add("tprefuse");
        BLOCKED_COMMANDS.add("vendi");
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        
        if (!SunnyCoinflip.getInstance().getGameManager().isPillarWorld(player.getWorld())) {
            return;
        }

        String message = event.getMessage();
        if (message.startsWith("/")) {
            message = message.substring(1);
        }

        String commandName = message.split(" ")[0].toLowerCase();

        if (BLOCKED_COMMANDS.contains(commandName)) {
            event.setCancelled(true);
            player.sendMessage("§cNon puoi usare questo comando nel mondo dei Pillars!");
        }
    }
}
