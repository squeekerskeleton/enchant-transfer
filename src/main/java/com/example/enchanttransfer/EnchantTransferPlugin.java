package com.example.enchanttransfer;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class EnchantTransferPlugin extends JavaPlugin implements Listener {

    private Logger log;

    @Override
    public void onEnable() {
        log = getLogger();
        getServer().getPluginManager().registerEvents(this, this);
        log.info("EnchantTransfer enabled.");
    }

    @Override
    public void onDisable() {
        log.info("EnchantTransfer disabled.");
    }

    // Run at LOWEST so we fire first, then also schedule a delayed re-apply
    // so that if the merge plugin overwrites the result after us, we fix it
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack leftSlot  = inv.getItem(0);
        ItemStack rightSlot = inv.getItem(1);

        // Collect enchantments from BOTH input slots regardless of material
        Map<Enchantment, Integer> collected = new HashMap<>();
        collectFrom(leftSlot,  collected);
        collectFrom(rightSlot, collected);

        if (collected.isEmpty()) return;

        // Apply immediately
        applyToResult(event.getResult(), collected, inv);

        // Also schedule a 1-tick delayed apply as a safety net in case
        // another plugin (the merge plugin) overwrites the result after us
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack result = inv.getResult();
                if (result == null || result.getType() == Material.AIR) return;
                applyToResult(result, collected, inv);
            }
        }.runTaskLater(this, 1L);
    }

    // Also listen at MONITOR priority to catch result set by other plugins
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onAnvilPrepareMonitor(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) return;

        ItemStack leftSlot  = inv.getItem(0);
        ItemStack rightSlot = inv.getItem(1);

        Map<Enchantment, Integer> collected = new HashMap<>();
        collectFrom(leftSlot,  collected);
        collectFrom(rightSlot, collected);

        if (collected.isEmpty()) return;

        applyToResult(result, collected, inv);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void applyToResult(ItemStack result, Map<Enchantment, Integer> enchants, AnvilInventory inv) {
        if (result == null || result.getType() == Material.AIR) return;

        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            int existing = meta.getEnchantLevel(entry.getKey());
            if (entry.getValue() > existing) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
                changed = true;
            }
        }

        if (changed) {
            result.setItemMeta(meta);
            inv.setResult(result);
            log.info("EnchantTransfer: applied enchantments to " + result.getType().name()
                + (meta.hasDisplayName() ? " (" + meta.getDisplayName() + ")" : ""));
        }
    }

    private void collectFrom(ItemStack item, Map<Enchantment, Integer> into) {
        if (item == null || item.getType() == Material.AIR) return;
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            into.merge(entry.getKey(), entry.getValue(), Math::max);
        }
    }
}
