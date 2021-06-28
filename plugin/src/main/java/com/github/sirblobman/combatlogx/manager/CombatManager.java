package com.github.sirblobman.combatlogx.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.PluginManager;

import com.github.sirblobman.api.configuration.ConfigurationManager;
import com.github.sirblobman.api.language.LanguageManager;
import com.github.sirblobman.api.language.Replacer;
import com.github.sirblobman.api.nms.EntityHandler;
import com.github.sirblobman.api.nms.MultiVersionHandler;
import com.github.sirblobman.api.utility.Validate;
import com.github.sirblobman.combatlogx.CombatPlugin;
import com.github.sirblobman.combatlogx.api.ICombatManager;
import com.github.sirblobman.combatlogx.api.ITimerManager;
import com.github.sirblobman.combatlogx.api.event.PlayerPreTagEvent;
import com.github.sirblobman.combatlogx.api.event.PlayerPunishEvent;
import com.github.sirblobman.combatlogx.api.event.PlayerReTagEvent;
import com.github.sirblobman.combatlogx.api.event.PlayerTagEvent;
import com.github.sirblobman.combatlogx.api.event.PlayerUntagEvent;
import com.github.sirblobman.combatlogx.api.object.TagReason;
import com.github.sirblobman.combatlogx.api.object.TagType;
import com.github.sirblobman.combatlogx.api.object.TimerType;
import com.github.sirblobman.combatlogx.api.object.TimerUpdater;
import com.github.sirblobman.combatlogx.api.object.UntagReason;
import com.github.sirblobman.combatlogx.api.utility.CommandHelper;
import com.github.sirblobman.combatlogx.listener.ListenerDeath;

public final class CombatManager implements ICombatManager {
    private final CombatPlugin plugin;
    private final Map<UUID, Long> combatMap;
    private final Map<UUID, LivingEntity> enemyMap;
    public CombatManager(CombatPlugin plugin) {
        this.plugin = Validate.notNull(plugin, "plugin must not be null!");
        this.combatMap = new HashMap<>();
        this.enemyMap = new HashMap<>();
    }

    @Override
    public boolean tag(Player player, LivingEntity enemy, TagType tagType, TagReason tagReason) {
        int timerSeconds = getMaxTimerSeconds(player);
        long timerMillis = (timerSeconds * 1_000L);

        long systemMillis = System.currentTimeMillis();
        long endMillis = (systemMillis + timerMillis);
        return tag(player, enemy, tagType, tagReason, endMillis);
    }

    @Override
    public boolean tag(Player player, LivingEntity enemy, TagType tagType, TagReason tagReason,
                       long customEndMillis) {
        Validate.notNull(player, "player must not be null!");
        Validate.notNull(tagType, "tagType must not be null!");
        Validate.notNull(tagReason, "tagReason must not be null!");
        if(player.hasMetadata("NPC")) return false;

        if(failsPreTagEvent(player, enemy, tagType, tagReason)) {
            this.plugin.printDebug("The PlayerPreTagEvent was cancelled.");
            return false;
        }

        boolean alreadyInCombat = isInCombat(player);
        this.plugin.printDebug("Previous Combat Status: " + alreadyInCombat);
        PluginManager pluginManager = Bukkit.getPluginManager();

        if(alreadyInCombat) {
            PlayerReTagEvent event = new PlayerReTagEvent(player, enemy, tagType, tagReason, customEndMillis);
            pluginManager.callEvent(event);
            if(event.isCancelled()) return false;
            customEndMillis = event.getEndTime();
        } else {
            PlayerTagEvent event = new PlayerTagEvent(player, enemy, tagType, tagReason, customEndMillis);
            pluginManager.callEvent(event);
            customEndMillis = event.getEndTime();
            sendTagMessage(player, enemy, tagType, tagReason);
        }

        UUID uuid = player.getUniqueId();
        this.combatMap.put(uuid, customEndMillis);
        if(enemy != null) this.enemyMap.put(uuid, enemy);

        String playerName = player.getName();
        this.plugin.printDebug("Successfully put player '" + playerName + "' into combat.");
        return true;
    }

    @Override
    public void untag(Player player, UntagReason untagReason) {
        Validate.notNull(player, "player must not be null!");
        Validate.notNull(untagReason, "untagReason must not be null!");
        if(!isInCombat(player)) return;

        UUID uuid = player.getUniqueId();
        this.combatMap.remove(uuid);

        ITimerManager timerManager = this.plugin.getTimerManager();
        Set<TimerUpdater> timerUpdaterSet = timerManager.getTimerUpdaters();
        for(TimerUpdater task : timerUpdaterSet) {
            task.remove(player);
        }

        LivingEntity previousEnemy = enemyMap.remove(uuid);
        PlayerUntagEvent event = new PlayerUntagEvent(player, untagReason, previousEnemy);
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.callEvent(event);
    }

    @Override
    public boolean isInCombat(Player player) {
        Validate.notNull(player, "player must not be null!");
        UUID uuid = player.getUniqueId();
        return this.combatMap.containsKey(uuid);
    }

    @Override
    public List<Player> getPlayersInCombat() {
        List<Player> playerList = new ArrayList<>();
        Set<UUID> keySet = new HashSet<>(this.combatMap.keySet());
        for(UUID uuid : keySet) {
            Player player = Bukkit.getPlayer(uuid);
            if(player == null) {
                this.combatMap.remove(uuid);
                continue;
            }
            playerList.add(player);
        }
        return playerList;
    }

    @Override
    public LivingEntity getEnemy(Player player) {
        Validate.notNull(player, "player must not be null!");
        UUID uuid = player.getUniqueId();
        return this.enemyMap.getOrDefault(uuid, null);
    }

    @Override
    public OfflinePlayer getByEnemy(LivingEntity enemy) {
        Validate.notNull(enemy, "enemy must not be null!");
        if(!this.enemyMap.containsValue(enemy)) return null;

        Set<Entry<UUID, LivingEntity>> entrySet = this.enemyMap.entrySet();
        for(Entry<UUID, LivingEntity> entry : entrySet) {
            LivingEntity value = entry.getValue();
            if(!enemy.equals(value)) continue;

            UUID uuid = entry.getKey();
            return Bukkit.getOfflinePlayer(uuid);
        }

        return null;
    }

    @Override
    public long getTimerLeftMillis(Player player) {
        Validate.notNull(player, "player must not be null!");
        if(!isInCombat(player)) return -1L;

        UUID uuid = player.getUniqueId();
        long endMillis = this.combatMap.get(uuid);
        long systemMillis = System.currentTimeMillis();
        return (endMillis - systemMillis);
    }

    @Override
    public int getTimerLeftSeconds(Player player) {
        double millisLeft = getTimerLeftMillis(player);
        double secondsLeft = (millisLeft / 1_000.0D);
        return (int) Math.ceil(secondsLeft);
    }

    @Override
    public int getMaxTimerSeconds(Player player) {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String timerTypeString = configuration.getString("timer.type");

        TimerType timerType = TimerType.parse(timerTypeString);
        return (timerType == TimerType.PERMISSION ? getPermissionTimerSeconds(player) : getGlobalTimerSeconds());
    }

    @Override
    public boolean punish(Player player, UntagReason punishReason, LivingEntity previousEnemy) {
        PlayerPunishEvent event = new PlayerPunishEvent(player, punishReason, previousEnemy);
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.callEvent(event);
        if(event.isCancelled()) return false;

        checkKill(player);
        runPunishCommands(player, previousEnemy);
        return true;
    }

    @Override
    public String replaceVariables(Player player, LivingEntity enemy, String string) {
        String playerName = player.getName();
        String enemyName = getEntityName(player, enemy);
        String newString = string.replace("{player}", playerName).replace("{enemy}", enemyName);
        return replaceMVdW(player, replacePAPI(player, newString));
    }

    private int getGlobalTimerSeconds() {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        return configuration.getInt("timer.default-timer", 10);
    }

    private int getPermissionTimerSeconds(Player player) {
        Set<PermissionAttachmentInfo> permissionAttachmentInfoSet = player.getEffectivePermissions();
        Set<String> permissionSet = permissionAttachmentInfoSet.stream()
                .filter(PermissionAttachmentInfo::getValue)
                .map(PermissionAttachmentInfo::getPermission)
                .filter(permission -> permission.startsWith("combatlogx.timer."))
                .map(permission -> permission.substring("combatlogx.timer.".length()))
                .collect(Collectors.toSet());
        if(permissionSet.isEmpty()) return getGlobalTimerSeconds();

        int lowestTimer = Integer.MAX_VALUE;
        boolean foundValue = false;
        for(String permission : permissionSet) {
            try {
                int value = Integer.parseInt(permission);
                lowestTimer = Math.min(lowestTimer, value);
                foundValue = true;
            } catch(NumberFormatException ignored) {}
        }

        return (foundValue ? lowestTimer : getGlobalTimerSeconds());
    }

    private String getEntityName(Player player, LivingEntity entity) {
        if(entity == null) {
            LanguageManager languageManager = this.plugin.getLanguageManager();
            return languageManager.getMessage(player, "placeholder.unknown-enemy", null, true);
        }

        MultiVersionHandler multiVersionHandler = this.plugin.getMultiVersionHandler();
        EntityHandler entityHandler = multiVersionHandler.getEntityHandler();
        return entityHandler.getName(entity);
    }

    private String replacePAPI(Player player, String string) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        if(!pluginManager.isPluginEnabled("PlaceholderAPI")) return string;
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, string);
    }

    private String replaceMVdW(Player player, String string) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        if(!pluginManager.isPluginEnabled("MVdWPlaceholderAPI")) return string;
        return be.maximvdw.placeholderapi.PlaceholderAPI.replacePlaceholders(player, string);
    }

    private void checkKill(Player player) {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("punish.yml");
        String killOptionString = configuration.getString("kill-time");
        if(killOptionString == null) killOptionString = "QUIT";

        if(killOptionString.equals("QUIT")) {
            ListenerDeath listenerDeath = this.plugin.getDeathListener();
            listenerDeath.add(player);
            player.setHealth(0.0D);
        }

        if(killOptionString.equals("JOIN")) {
            YamlConfiguration playerData = this.plugin.getData(player);
            playerData.set("kill-on-join", true);
            this.plugin.saveData(player);
        }
    }

    private void runPunishCommands(Player player, LivingEntity previousEnemy) {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("commands.yml");
        List<String> punishCommandList = configuration.getStringList("punish-command-list");
        for(String punishCommand : punishCommandList) {
            String replacedCommand = replaceVariables(player, previousEnemy, punishCommand);
            if(replacedCommand.startsWith("[PLAYER]")) {
                String command = replacedCommand.substring("[PLAYER]".length());
                CommandHelper.runAsPlayer(this.plugin, player, command);
                continue;
            }

            if(replacedCommand.startsWith("[OP]")) {
                String command = replacedCommand.substring("[OP]".length());
                CommandHelper.runAsOperator(this.plugin, player, command);
                continue;
            }

            CommandHelper.runAsConsole(this.plugin, replacedCommand);
        }
    }

    private boolean failsPreTagEvent(Player player, LivingEntity enemy, TagType tagType, TagReason tagReason) {
        PlayerPreTagEvent event = new PlayerPreTagEvent(player, enemy, tagType, tagReason);
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.callEvent(event);
        return event.isCancelled();
    }

    private void sendTagMessage(Player player, LivingEntity enemy, TagType tagType, TagReason tagReason) {
        if(tagType == TagType.UNKNOWN || tagReason == TagReason.UNKNOWN) {
            this.plugin.sendMessageWithPrefix(player, "tagged.unknown", null, true);
            return;
        }

        String enemyName = getEntityName(player, enemy);
        String enemyType = (enemy == null ? EntityType.UNKNOWN.name() : enemy.getType().name());
        String tagReasonString = tagReason.name().toLowerCase();
        String tagTypeString = tagType.name().toLowerCase();

        String languagePath = ("tagged." + tagReasonString + "." + tagTypeString);
        Replacer replacer = message -> message.replace("{enemy}", enemyName)
                .replace("{mob_type}", enemyType);
        this.plugin.sendMessageWithPrefix(player, languagePath, replacer, true);
    }
}
