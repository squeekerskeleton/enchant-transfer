package com.example.enchanttransfer;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class EnchantTransferPlugin extends JavaPlugin implements Listener {

    private Logger log;

    // -----------------------------------------------------------------------
    // CONFIGURATION — edit these to match your server setup
    // -----------------------------------------------------------------------

    /**
     * The output material that should receive transferred enchantments.
     * Your elytra+chestplate merge outputs an ELYTRA item, so leave as-is.
     */
    private static final Material RESULT_MATERIAL = Material.ELYTRA;

    /**
     * Ingredient materials whose enchantments will be transferred.
     * Add every chestplate tier your recipe accepts as an ingredient.
     */
    private static final Set<Material> SOURCE_MATERIALS = EnumSet.of(
        Material.ELYTRA,
        Material.LEATHER_CHESTPLATE,
        Material.CHAINMAIL_CHESTPLATE,
        Material.IRON_CHESTPLATE,
        Material.GOLDEN_CHESTPLATE,
        Material.DIAMOND_CHESTPLATE,
        Material.NETHERITE_CHESTPLATE
    );

    // -----------------------------------------------------------------------

    @Override
    public void onEnable() {
        log = getLogger();
        getServer().getPluginManager().registerEvents(this, this);
        log.info("EnchantTransfer enabled — watching for " + RESULT_MATERIAL.name() + " crafts.");
    }

    @Override
    public void onDisable() {
        log.info("EnchantTransfer disabled.");
    }

    // -----------------------------------------------------------------------
    // PrepareItemCraftEvent — fires when the grid is filled, sets result slot
    // CustomRecipes triggers this. We modify the result here so the player
    // immediately sees the enchanted item in the output slot.
    // -----------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPrepare(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != RESULT_MATERIAL) return;

        Map<Enchantment, Integer> collected = collectEnchantments(event.getInventory().getMatrix());
        if (collected.isEmpty()) return;

        event.getInventory().setResult(applyEnchantments(result.clone(), collected));
    }

    // -----------------------------------------------------------------------
    // CraftItemEvent — fires when the player actually takes the result.
    // Acts as a safety net in case PrepareItemCraftEvent was re-overridden
    // by CustomRecipes after our handler already ran.
    // -----------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() != RESULT_MATERIAL) return;

        Map<Enchantment, Integer> collected = collectEnchantments(event.getInventory().getMatrix());
        if (collected.isEmpty()) return;

        // Only re-apply if PrepareItemCraftEvent didn't already set them
        ItemMeta existingMeta = result.getItemMeta();
        boolean alreadyApplied = existingMeta != null &&
            collected.entrySet().stream()
                .allMatch(e -> existingMeta.getEnchantLevel(e.getKey()) >= e.getValue());

        if (!alreadyApplied) {
            event.setCurrentItem(applyEnchantments(result.clone(), collected));
            log.info("EnchantTransfer (fallback): applied " + collected.size()
                + " enchantment(s) for " + event.getWhoClicked().getName());
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<Enchantment, Integer> collectEnchantments(ItemStack[] matrix) {
        Map<Enchantment, Integer> result = new HashMap<>();
        for (ItemStack slot : matrix) {
            if (slot == null || !SOURCE_MATERIALS.contains(slot.getType())) continue;
            for (Map.Entry<Enchantment, Integer> entry : slot.getEnchantments().entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), Math::max);
            }
        }
        return result;
    }

    private ItemStack applyEnchantments(ItemStack item, Map<Enchantment, Integer> enchants) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            log.warning("Result item has no ItemMeta — cannot apply enchantments.");
            return item;
        }
        // unsafe=true bypasses vanilla level caps
        enchants.forEach((ench, level) -> meta.addEnchant(ench, level, true));
        item.setItemMeta(meta);
        return item;
    }
}
