package tripu1404.anticheat;

import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import tripu1404.anticheat.checks.AntiPhaseCheck;
import tripu1404.anticheat.checks.FlightCheck;
import tripu1404.anticheat.checks.InventoryMoveCheck;
import tripu1404.anticheat.checks.NoSlowDownCheck;

import java.io.File;
import java.util.LinkedHashMap;

public class Main extends PluginBase {

    private static Main instance;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Crear y cargar la configuración por defecto
        this.loadSettings();

        // 2. Registro de los módulos de detección
        this.registerChecks();

        this.getLogger().info("§l§a[TripuAC]§r §fAntiCheat activado y protegiendo el servidor.");
    }

    /**
     * Registra los eventos de cada clase de detección basándose en la configuración.
     */
    private void registerChecks() {
        Config config = this.getConfig();

        // Check de Movimiento en Inventario
        if (config.getBoolean("checks.inventory-move", true)) {
            this.getServer().getPluginManager().registerEvents(new InventoryMoveCheck(), this);
        }

        // Check de NoSlow (Comida/Arco)
        if (config.getBoolean("checks.no-slow-down", true)) {
            this.getServer().getPluginManager().registerEvents(new NoSlowDownCheck(), this);
        }

        // Check de Vuelo y Elytras (Incluye Riptide y Slime)
        if (config.getBoolean("checks.flight", true)) {
            this.getServer().getPluginManager().registerEvents(new FlightCheck(), this);
        }

        // Check de Anti-Phase (Atravesar bloques)
        if (config.getBoolean("checks.anti-phase", true)) {
            this.getServer().getPluginManager().registerEvents(new AntiPhaseCheck(), this);
        }
    }

    /**
     * Genera un archivo config.yml limpio con las opciones de cada check.
     */
    private void loadSettings() {
        this.getDataFolder().mkdirs();
        File configFile = new File(this.getDataFolder(), "config.yml");
        
        if (!configFile.exists()) {
            LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
            settings.put("checks.inventory-move", true);
            settings.put("checks.no-slow-down", true);
            settings.put("checks.flight", true);
            settings.put("checks.anti-phase", true);
            
            Config config = new Config(configFile, Config.YAML);
            config.setAll(settings);
            config.save();
        }
    }

    public static Main getInstance() {
        return instance;
    }
}
