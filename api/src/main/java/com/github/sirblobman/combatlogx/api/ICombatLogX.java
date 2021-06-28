package com.github.sirblobman.combatlogx.api;

import java.util.Locale;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.sirblobman.api.configuration.ConfigurationManager;
import com.github.sirblobman.api.configuration.IResourceHolder;
import com.github.sirblobman.api.configuration.PlayerDataManager;
import com.github.sirblobman.api.language.LanguageManager;
import com.github.sirblobman.api.language.Replacer;
import com.github.sirblobman.api.nms.MultiVersionHandler;
import com.github.sirblobman.combatlogx.api.expansion.ExpansionManager;
import com.github.sirblobman.combatlogx.api.listener.IDeathListener;

public interface ICombatLogX extends IResourceHolder {
    JavaPlugin getPlugin();
    ClassLoader getPluginClassLoader();

    YamlConfiguration getConfig(String fileName);
    void reloadConfig(String fileName);
    void saveConfig(String fileName);
    void saveDefaultConfig(String fileName);

    YamlConfiguration getData(OfflinePlayer player);
    void saveData(OfflinePlayer player);

    MultiVersionHandler getMultiVersionHandler();
    ConfigurationManager getConfigurationManager();
    PlayerDataManager getPlayerDataManager();
    LanguageManager getLanguageManager();

    ExpansionManager getExpansionManager();
    ICombatManager getCombatManager();
    ITimerManager getTimerManager();
    IDeathListener getDeathListener();

    String getMessageWithPrefix(CommandSender sender, String key, Replacer replacer, boolean color);
    void sendMessageWithPrefix(CommandSender sender, String key, Replacer replacer, boolean color);
    void sendMessage(CommandSender sender, String... messageArray);

    void printDebug(String... messageArray);
}
