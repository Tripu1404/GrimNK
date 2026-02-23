package tripu1404.anticheat.checks;

import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class InventoryMoveCheck implements Listener {

    private final Set<UUID> openInventories = new HashSet<>();

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        openInventories.add(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openInventories.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openInventories.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Ignoramos si solo movió la cámara
        if (event.getFrom().distanceSquared(event.getTo()) < 0.0001) {
            return; 
        }

        if (openInventories.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
