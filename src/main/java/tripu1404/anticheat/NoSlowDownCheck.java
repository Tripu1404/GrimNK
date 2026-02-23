package tripu1404.anticheat.checks;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoSlowDownCheck implements Listener {

    // Guardamos el UUID y el Tick en el que empezó a usar el objeto
    private final Map<UUID, Integer> usingItemTicks = new HashMap<>();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Ignoramos si solo movió la cámara
        if (event.getFrom().distanceSquared(event.getTo()) < 0.0001) {
            return; 
        }

        boolean isUsingItem = player.getDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_ACTION);

        if (isUsingItem) {
            int currentTick = Server.getInstance().getTick();
            
            // Si es la primera vez que lo detectamos usando el ítem, guardamos el tick actual
            usingItemTicks.putIfAbsent(uuid, currentTick);

            int startTick = usingItemTicks.get(uuid);

            // Verificamos si ya pasaron 5 ticks desde que empezó la acción
            if (currentTick - startTick > 5) {
                double deltaX = event.getTo().getX() - event.getFrom().getX();
                double deltaZ = event.getTo().getZ() - event.getFrom().getZ();
                double speedSquared = (deltaX * deltaX) + (deltaZ * deltaZ);

                // Aplicamos el límite de velocidad original
                if (speedSquared > 0.04) {
                    event.setCancelled(true);
                }
            }
        } else {
            // Si ya no está usando el ítem, lo eliminamos de la lista para reiniciar el contador
            usingItemTicks.remove(uuid);
        }
    }
}
