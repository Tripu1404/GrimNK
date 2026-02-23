package tripu1404.anticheat.checks;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;

public class NoSlowDownCheck implements Listener {

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Ignoramos si solo movió la cámara
        if (event.getFrom().distanceSquared(event.getTo()) < 0.0001) {
            return; 
        }

        boolean isUsingItem = player.getDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_ACTION);

        if (isUsingItem) {
            double deltaX = event.getTo().getX() - event.getFrom().getX();
            double deltaZ = event.getTo().getZ() - event.getFrom().getZ();
            double speedSquared = (deltaX * deltaX) + (deltaZ * deltaZ);

            // Umbral de velocidad permitida mientras se come/apunta
            if (speedSquared > 0.04) {
                event.setCancelled(true);
            }
        }
    }
}
