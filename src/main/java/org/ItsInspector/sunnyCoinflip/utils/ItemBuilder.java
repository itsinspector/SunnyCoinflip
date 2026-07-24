package org.ItsInspector.sunnyCoinflip.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {
    private ItemBuilder() {}

    public static String translate(String text) {
        if (text == null) return null;
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static ItemStack createItem(Material material, String name, String... lore) {
        return createItem(material, name, false, lore);
    }

    public static ItemStack createItem(Material material, String name, boolean enchanted, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(translate(name));
        if (lore.length > 0) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) lines.add(translate(line));
            meta.setLore(lines);
        }
        if (enchanted) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createSkull(OfflinePlayer owner, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;
        meta.setOwningPlayer(owner);
        meta.setDisplayName(translate(name));
        if (lore.length > 0) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) lines.add(translate(line));
            meta.setLore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }
}
