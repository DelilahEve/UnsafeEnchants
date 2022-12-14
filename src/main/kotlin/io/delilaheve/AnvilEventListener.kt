package io.delilaheve

import io.delilaheve.util.ConfigOptions
import io.delilaheve.util.EnchantmentUtil.combineWith
import io.delilaheve.util.EnchantmentUtil.hasConflicts
import io.delilaheve.util.ItemUtil.canMergeWith
import io.delilaheve.util.ItemUtil.findEnchantments
import io.delilaheve.util.ItemUtil.isBook
import io.delilaheve.util.ItemUtil.repairCost
import io.delilaheve.util.ItemUtil.repairFrom
import io.delilaheve.util.ItemUtil.setEnchantmentsUnsafe
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority.HIGHEST
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.inventory.AnvilInventory
import org.bukkit.inventory.InventoryView.Property.REPAIR_COST
import org.bukkit.permissions.Permission
import kotlin.math.min

/**
 * Listener for anvil events
 */
class AnvilEventListener : Listener {

    companion object {
        // Anvil's output slot
        private const val ANVIL_OUTPUT_SLOT = 2
    }

    // Permission node required for the plugin to take over enchantment combination
    private val requirePermission: Permission
        get() = Permission(UnsafeEnchants.unsafePermission)

    /**
     * Event handler logic for when an anvil contains items to be combined
     */
    @EventHandler(priority = HIGHEST)
    fun anvilCombineCheck(event: PrepareAnvilEvent) {
        val inventory = event.inventory
        val first = inventory.getItem(0) ?: return
        val second = inventory.getItem(1) ?: return
        if (first.canMergeWith(second)) {
            val newEnchants = first.findEnchantments()
                .combineWith(second.findEnchantments())
            val resultItem = first.clone()
            resultItem.itemMeta?.let {
                it.setDisplayName(inventory.renameText)
                resultItem.itemMeta = it
            }
            resultItem.setEnchantmentsUnsafe(newEnchants)
            if (!first.isBook() && !second.isBook()) {
                // we only need to be concerned with repair when neither item is a book
                resultItem.repairFrom(first, second)
            }
            var repairCost = first.repairCost + second.repairCost
            if (ConfigOptions.limitRepairCost) {
                repairCost = min(repairCost, ConfigOptions.limitRepairValue)
            }
            event.result = resultItem
            /* Because Minecraft likes to have the final say in the repair cost displayed
             * we need to wait for the event to end before overriding it, this ensures that
             * we have the final say in the process. */
            UnsafeEnchants.instance
                .server
                .scheduler
                .runTask(UnsafeEnchants.instance, Runnable {
                    if (ConfigOptions.removeRepairLimit) {
                        inventory.maximumRepairCost = Int.MAX_VALUE
                    }
                    inventory.repairCost = repairCost
                    event.view.setProperty(REPAIR_COST, repairCost)
                })
        }
    }

    /**
     * Event handler logic for when a player is trying to pull an item out of the anvil
     */
    @EventHandler(ignoreCancelled = true)
    fun anvilExtractionCheck(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.inventory as? AnvilInventory ?: return
        val output = inventory.getItem(ANVIL_OUTPUT_SLOT) ?: return
        if (output.findEnchantments().hasConflicts() && !player.hasPermission(requirePermission)) { return }
        if (event.rawSlot != ANVIL_OUTPUT_SLOT) { return }
        event.result = Event.Result.ALLOW
    }

}
