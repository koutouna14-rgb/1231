package dev.alias.teamechest;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Talks to justTeams purely through reflection, so this addon needs no justTeams
 * jar to compile and does not touch justTeams' code.
 */
final class JustTeamsBridge {

    private final Plugin jt;
    private final Object teamManager;
    private final Object configManager;
    private final Object featureManager;
    private final Object messageManager;
    private final Object storageManager;

    private final Method getPlayerTeamCached;
    private final Method getPlayerTeamCache;
    private final Method isEnderchestEnabled;
    private final Method isFeatureAllowed;
    private final Method canAffordAndPay;
    private final Method teamIsLocked;
    private final Method teamGetChest;
    private final Method teamHasViewers;
    private final Method teamUnlock;
    private final Method teamGetId;
    private final Method teamGetMember;
    private final Method memberCanUse;

    JustTeamsBridge(Plugin jt) throws ReflectiveOperationException {
        this.jt = jt;
        ClassLoader cl = jt.getClass().getClassLoader();
        Class<?> jtClass = jt.getClass();

        this.teamManager = jtClass.getMethod("getTeamManager").invoke(jt);
        this.configManager = jtClass.getMethod("getConfigManager").invoke(jt);
        this.featureManager = jtClass.getMethod("getFeatureRestrictionManager").invoke(jt);
        this.messageManager = jtClass.getMethod("getMessageManager").invoke(jt);
        this.storageManager = jtClass.getMethod("getStorageManager").invoke(jt);

        this.getPlayerTeamCached = teamManager.getClass().getMethod("getPlayerTeamCached", UUID.class);
        this.getPlayerTeamCache = teamManager.getClass().getMethod("getPlayerTeamCache");
        this.isEnderchestEnabled = configManager.getClass().getMethod("isTeamEnderchestEnabled");
        this.isFeatureAllowed = featureManager.getClass().getMethod("isFeatureAllowed", Player.class, String.class);
        this.canAffordAndPay = featureManager.getClass().getMethod("canAffordAndPay", Player.class, String.class);

        Class<?> teamClass = Class.forName("eu.kotori.justTeams.team.Team", true, cl);
        Class<?> memberClass = Class.forName("eu.kotori.justTeams.team.TeamPlayer", true, cl);
        this.teamIsLocked = teamClass.getMethod("isEnderChestLocked");
        this.teamGetChest = teamClass.getMethod("getEnderChest");
        this.teamHasViewers = teamClass.getMethod("hasEnderChestViewers");
        this.teamUnlock = teamClass.getMethod("unlockEnderChest");
        this.teamGetId = teamClass.getMethod("getId");
        this.teamGetMember = teamClass.getMethod("getMember", UUID.class);
        this.memberCanUse = memberClass.getMethod("canUseEnderChest");
    }

    Object getTeam(UUID uuid) throws ReflectiveOperationException {
        return getPlayerTeamCached.invoke(teamManager, uuid);
    }

    @SuppressWarnings("unchecked")
    Collection<Object> getCachedTeams() throws ReflectiveOperationException {
        Object map = getPlayerTeamCache.invoke(teamManager);
        if (map instanceof Map<?, ?> m) {
            return (Collection<Object>) m.values();
        }
        return Collections.emptyList();
    }

    boolean isEnderchestEnabled() throws ReflectiveOperationException {
        return (boolean) isEnderchestEnabled.invoke(configManager);
    }

    boolean isFeatureAllowed(Player p) throws ReflectiveOperationException {
        return (boolean) isFeatureAllowed.invoke(featureManager, p, "enderchest");
    }

    /** Also charges the player and tells them if they can't afford it, exactly like /team enderchest does. */
    boolean canAffordAndPay(Player p) throws ReflectiveOperationException {
        return (boolean) canAffordAndPay.invoke(featureManager, p, "enderchest");
    }

    boolean isLocked(Object team) throws ReflectiveOperationException {
        return (boolean) teamIsLocked.invoke(team);
    }

    Object getChest(Object team) throws ReflectiveOperationException {
        return teamGetChest.invoke(team);
    }

    boolean hasViewers(Object team) throws ReflectiveOperationException {
        return (boolean) teamHasViewers.invoke(team);
    }

    void unlock(Object team) throws ReflectiveOperationException {
        teamUnlock.invoke(team);
    }

    int getId(Object team) throws ReflectiveOperationException {
        return (int) teamGetId.invoke(team);
    }

    boolean canUseEnderChest(Object team, UUID uuid) throws ReflectiveOperationException {
        Object member = teamGetMember.invoke(team, uuid);
        return member != null && (boolean) memberCanUse.invoke(member);
    }

    /** Releases the cross-server database lock row. Blocking call - run it async. */
    void releaseDbLock(int teamId) throws ReflectiveOperationException {
        Object storage = storageManager.getClass().getMethod("getStorage").invoke(storageManager);
        if (storage == null) {
            return;
        }
        storage.getClass().getMethod("releaseEnderChestLock", int.class).invoke(storage, teamId);
    }

    /** Sends a justTeams message key (e.g. enderchest_opened) to the player. Best effort. */
    void sendMessage(Player p, String key) {
        try {
            for (Method m : messageManager.getClass().getMethods()) {
                if (!m.getName().equals("sendMessage") || m.getParameterCount() != 3 || !m.isVarArgs()) {
                    continue;
                }
                Class<?>[] types = m.getParameterTypes();
                if (!types[0].isAssignableFrom(CommandSender.class) && !types[0].isAssignableFrom(Player.class)) {
                    continue;
                }
                if (types[1] != String.class) {
                    continue;
                }
                Object empty = Array.newInstance(types[2].getComponentType(), 0);
                m.invoke(messageManager, p, key, empty);
                return;
            }
        } catch (Exception ignored) {
            // cosmetic only
        }
    }

    Plugin plugin() {
        return jt;
    }
}
