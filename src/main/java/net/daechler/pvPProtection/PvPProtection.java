package net.daechler.pvPProtection;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.UUID;

public class PvPProtection extends JavaPlugin implements Listener {
    private Connection connection;
    private String tableName = "player_categories";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDatabase();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        closeDatabase();
    }

    private void setupDatabase() {
        FileConfiguration config = getConfig();
        String url = "jdbc:mysql://" + config.getString("mysql.host") + ":" + config.getString("mysql.port") + "/" + config.getString("mysql.database");
        String user = config.getString("mysql.username");
        String password = config.getString("mysql.password");

        try {
            connection = DriverManager.getConnection(url, user, password);
            Statement statement = connection.createStatement();
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tableName + " (uuid VARCHAR(36) PRIMARY KEY, category INT)");
        } catch (SQLException e) {
            getLogger().severe("Could not connect to MySQL database: " + e.getMessage());
        }
    }

    private void closeDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            getLogger().severe("Error closing MySQL connection: " + e.getMessage());
        }
    }

    private int determineCategory(Player player) {
        ItemStack[] armor = player.getEquipment().getArmorContents();
        int category = 1;

        for (ItemStack piece : armor) {
            if (piece != null) {
                Material type = piece.getType();
                if (type.name().contains("NETHERITE")) {
                    category = 4;
                    break;
                } else if (type.name().contains("DIAMOND")) {
                    category = Math.max(category, 3);
                } else if (type.name().contains("IRON") || type.name().contains("CHAINMAIL")) {
                    category = Math.max(category, 2);
                }
            }
        }
        return category;
    }

    private int getPlayerCategory(Player player) {
        UUID uuid = player.getUniqueId();
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT category FROM " + tableName + " WHERE uuid=?");
            statement.setString(1, uuid.toString());
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("category");
            }
        } catch (SQLException e) {
            getLogger().severe("Error fetching player category: " + e.getMessage());
        }
        return 1;
    }

    private void updatePlayerCategory(Player player) {
        int newCategory = determineCategory(player);
        UUID uuid = player.getUniqueId();
        int currentCategory = getPlayerCategory(player);
        if (newCategory > currentCategory) {
            try {
                PreparedStatement statement = connection.prepareStatement("INSERT INTO " + tableName + " (uuid, category) VALUES (?, ?) ON DUPLICATE KEY UPDATE category=?");
                statement.setString(1, uuid.toString());
                statement.setInt(2, newCategory);
                statement.setInt(3, newCategory);
                statement.executeUpdate();
            } catch (SQLException e) {
                getLogger().severe("Error updating player category: " + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();

            int attackerCategory = getPlayerCategory(attacker);
            int victimCategory = getPlayerCategory(victim);

            if (attackerCategory != victimCategory) {
                event.setCancelled(true);
                attacker.sendMessage("§cYou can only fight players in your armor category!");
            }
        }
    }

    @EventHandler
    public void onArmorChange(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            Bukkit.getScheduler().runTaskLater(this, () -> updatePlayerCategory(player), 1L);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> updatePlayerCategory(player), 1L);
    }
}