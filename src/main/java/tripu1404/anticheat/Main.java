package tripu1404.anticheat;

import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import tripu1404.anticheat.checks.FlightCheck;
import tripu1404.anticheat.checks.InventoryMoveCheck;
import tripu1404.anticheat.checks.NoSlowDownCheck;

public class Main extends PluginBase {

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        Config config = this.getConfig();

        if (config.getBoolean("checks.inventory-move", true)) {
            this.getServer().getPluginManager().registerEvents(new InventoryMoveCheck(), this);
        }

        if (config.getBoolean("checks.no-slow-down", true)) {
            this.getServer().getPluginManager().registerEvents(new NoSlowDownCheck(), this);
        }

        // Registramos el nuevo check de Fly/Glide
        if (config.getBoolean("checks.flight-block", true)) {
            this.getServer().getPluginManager().registerEvents(new FlightCheck(), this);
        }
        
        this.getLogger().info("§aAntiCheat activado correctamente.");
    }
}
