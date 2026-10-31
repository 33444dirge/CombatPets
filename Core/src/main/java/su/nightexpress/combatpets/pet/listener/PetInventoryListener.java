package su.nightexpress.combatpets.pet.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.pet.PetManager;
import su.nightexpress.combatpets.pet.impl.PetInstance;
import su.nightexpress.nightcore.manager.AbstractListener;

/** Only real pet storage, never unrelated containers or menu preview items. */
public final class PetInventoryListener extends AbstractListener<PetsPlugin> {
    private final PetManager manager;

    public PetInventoryListener(PetsPlugin plugin, PetManager manager) {
        super(plugin);
        this.manager = manager;
    }

    private boolean unavailable(Inventory inventory) {
        return inventory.getHolder(false) instanceof PetInstance pet
            && (this.manager.isShuttingDown() || pet.isRemoved()
                || !Bukkit.isOwnedByCurrentRegion(pet.getEntity()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (this.unavailable(event.getInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trackOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof PetInstance pet && event.getPlayer() instanceof Player player) {
            pet.trackInventoryViewer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof PetInstance pet && event.getPlayer() instanceof Player player) {
            if (pet.isOwner(player)) pet.saveInventoryContents();
            pet.forgetInventoryViewer(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        // Includes bottom inventory, shift transfer, number keys and collect-to-cursor.
        if (this.unavailable(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (this.unavailable(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void saveAfterClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof PetInstance pet
            && event.getWhoClicked() instanceof Player player) pet.queueInventorySave(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void saveAfterDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof PetInstance pet
            && event.getWhoClicked() instanceof Player player) pet.queueInventorySave(player);
    }
}
