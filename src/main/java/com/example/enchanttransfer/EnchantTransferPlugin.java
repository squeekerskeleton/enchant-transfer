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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class EnchantTransferPlugin extends JavaPlugin implements Listener {

    private Logger log;

    // -----------------------------------------------------------------------
    // CONFIGURATION
    // -----------------------------------------------------------------------

    // The output material your merge plugin produces
    private static final Material RESULT_MATERIAL = Material.ELYTRA;

    // The custom names your merge plugin gives the result.
    // Add or remove names here to match yours exactly (case-sensitive).
    private static final String[] RESULT_NAMES = {
        "Diamond Infused Elytra",
        "Netherite Infused Elytra",
        "Iron Infused Elytra",
        "Golden Infused Elytra",
        "Chainmail Infused Elytra"
    };

    // -----------------------------------------------------------------------

    @Override
    public void onEnable() {
        log = getLogger();
        getServer().getPluginManager().registerEvents(this, this);
        log.info("EnchantTransfer enabled — watching for anvil merges.");
    }

    @Override
    public void onDisable() {
        log.info("EnchantTransfer disabled.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();

        // Must be an elytra result
        if (result == null || result.getType() != RESULT_MATERIAL) return;

        // Must have one of our custom names
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null || !resultMeta.hasDisplayName()) return;
        if (!isInfusedElytra(resultMeta.getDisplayName())) return;

        AnvilInventory inv = event.getInventory();
        ItemStack leftSlot  = inv.getItem(0); // first item placed in anvil
        ItemStack rightSlot = inv.getItem(1); // second item placed in anvil

        // Collect enchantments from both slots
        Map<Enchantment, Integer> collected = new HashMap<>();
        collectFrom(leftSlot,  collected);
        collectFrom(rightSlot, collected);

        if (collected.isEmpty()) return;

        // Apply enchantments to result
        ItemStack modified = result.clone();
        ItemMeta  meta     = modified.getItemMeta();
        if (meta == null) return;

        // unsafe=true bypasses vanilla level caps
        collected.forEach((ench, level) -> meta.addEnchant(ench, level, true));
        modified.setItemMeta(meta);
        event.setResult(modified);

        log.info("EnchantTransfer: applied " + collected.size()
            + " enchantment(s) to " + resultMeta.getDisplayName());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private boolean isInfusedElytra(String displayName) {
        for (String name : RESULT_NAMES) {
            if (name.equals(displayName)) return true;
        }
        return false;
    }

    private void collectFrom(ItemStack item, Map<Enchantment, Integer> into) {
        if (item == null) return;
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            // Keep highest level if same enchant appears on both items
            into.merge(entry.getKey(), entry.getValue(), Math::max);
        }
    }
}
