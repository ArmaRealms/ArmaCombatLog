package combatlogx.expansion.compatibility.citizens.object;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.github.sirblobman.combatlogx.api.ICombatLogX;
import com.github.sirblobman.combatlogx.api.manager.ICombatManager;
import com.github.sirblobman.combatlogx.api.object.TagInformation;

import combatlogx.expansion.compatibility.citizens.CitizensExpansion;
import combatlogx.expansion.compatibility.citizens.configuration.CitizensConfiguration;
import combatlogx.expansion.compatibility.citizens.manager.CombatNpcManager;
import net.citizensnpcs.api.npc.NPC;

public final class CombatNPC extends BukkitRunnable {
    private final CitizensExpansion expansion;
    private final NPC originalNPC;
    private final UUID ownerId;

    private UUID enemyId;
    private long survivalTicks;

    public CombatNPC(@NotNull CitizensExpansion expansion, @NotNull NPC originalNPC, @NotNull OfflinePlayer owner) {
        this.expansion = expansion;
        this.originalNPC = originalNPC;
        this.ownerId = owner.getUniqueId();
    }

    @Override
    public void run() {
        this.survivalTicks--;
        if (this.survivalTicks > 0) {
            return;
        }

        CitizensExpansion expansion = getExpansion();
        CitizensConfiguration configuration = expansion.getCitizensConfiguration();

        if (configuration.isStayUntilEnemyEscapes() && this.enemyId != null) {
            Player player = Bukkit.getPlayer(this.enemyId);
            if (player != null) {
                ICombatManager combatManager = expansion.getPlugin().getCombatManager();
                TagInformation tagInformation = combatManager.getTagInformation(player);
                if (tagInformation != null) {
                    long timeLeftMillis = tagInformation.getMillisLeftCombined();
                    this.survivalTicks = (timeLeftMillis / 50L) + 1;
                    return;
                }
            }
        }

        CombatNpcManager combatNpcManager = expansion.getCombatNpcManager();
        combatNpcManager.remove(this);
    }

    public void start() {
        resetSurvivalTime();

        ICombatLogX combatLogX = getCombatLogX();
        JavaPlugin plugin = combatLogX.getPlugin();
        runTaskTimerAsynchronously(plugin, 1L, 1L);
    }

    public @NotNull NPC getOriginalNPC() {
        return this.originalNPC;
    }

    public @NotNull UUID getOwnerId() {
        return this.ownerId;
    }

    public @Nullable OfflinePlayer getOfflineOwner() {
        UUID ownerId = getOwnerId();
        return Bukkit.getOfflinePlayer(ownerId);
    }

    public void resetSurvivalTime() {
        CitizensExpansion expansion = getExpansion();
        CitizensConfiguration configuration = expansion.getCitizensConfiguration();
        long survivalSeconds = configuration.getSurvivalTime();
        this.survivalTicks = (survivalSeconds * 20L);
    }

    public void setEnemy(@NotNull Player enemy) {
        this.enemyId = enemy.getUniqueId();
    }

    private @NotNull CitizensExpansion getExpansion() {
        return this.expansion;
    }

    private @NotNull ICombatLogX getCombatLogX() {
        CitizensExpansion expansion = getExpansion();
        return expansion.getPlugin();
    }
}
