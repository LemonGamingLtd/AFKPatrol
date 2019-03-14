package xyz.olivermartin.afkpatrol;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class AFKPatrol extends JavaPlugin implements Listener {

	private static Long notifyPeriod = 45L;
	private static Long actionPeriod = 60L;
	private static Integer kickCount = 10;
	private static Long interactGracePeriod = 10L;
	private static Boolean shouldNotify = true;

	private HashMap<UUID, Long> lastMove = new HashMap<UUID, Long>();
	private HashMap<UUID, Long> lastInteract = new HashMap<UUID, Long>();
	private HashMap<UUID, Integer> nonceList = new HashMap<UUID, Integer>();
	private HashMap<UUID, Integer> actionList = new HashMap<UUID, Integer>();

	@Override
	public void onEnable() {
		getLogger().info("AFKPatrol is now loading...");

		ConfigManager.getInstance().registerHandler("config.yml", getDataFolder());
		Configuration config = ConfigManager.getInstance().getHandler("config.yml").getConfig();

		notifyPeriod = config.getLong("notify_period");
		actionPeriod = config.getLong("action_period");
		kickCount = config.getInt("disconnect_count");
		interactGracePeriod = config.getLong("interact_grace_period");
		shouldNotify = config.getBoolean("should_notify");

		ConfigurationSection messageSection = config.getConfigurationSection("messages");
		for (String key : messageSection.getKeys(true)) {
			MessageManager.getInstance().loadMessage(key, messageSection.getString(key));
		}

		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		getLogger().info("AFKPatrol is now disabling...");
	}

	@Override
	public boolean onCommand(CommandSender commandSender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("afkpatrol")) {

			// Verify correct command length otherwise display help message
			if (args.length != 1) {
				return false;
			}

			if (!args[0].equalsIgnoreCase("reload")) {
				return false;
			}

			if (!commandSender.hasPermission("afkpatrol.reload")) {
				return false;
			}

			ConfigManager.getInstance().getHandler("config.yml").startupConfig();
			Configuration config = ConfigManager.getInstance().getHandler("config.yml").getConfig();

			notifyPeriod = config.getLong("notify_period");
			actionPeriod = config.getLong("action_period");
			kickCount = config.getInt("disconnect_count");
			interactGracePeriod = config.getLong("interact_grace_period");
			shouldNotify = config.getBoolean("should_notify");

			ConfigurationSection messageSection = config.getConfigurationSection("messages");
			for (String key : messageSection.getKeys(true)) {
				MessageManager.getInstance().loadMessage(key, messageSection.getString(key));
			}

			commandSender.sendMessage("&aReloaded AFKPatrol");

			return true;
		}
		return false;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onChat(AsyncPlayerChatEvent e) {
		Player target = e.getPlayer();

		// If the player has been notified
		if (nonceList.containsKey(target.getUniqueId())) {

			// If their message contains the number we asked for
			if (e.getMessage().contains(nonceList.get(target.getUniqueId()).toString())) {

				// Reset their last move count to now!
				lastMove.put(target.getUniqueId(), (System.currentTimeMillis() / 1000L));

				// Remove them from the action lists
				nonceList.remove(target.getUniqueId());
				actionList.remove(target.getUniqueId());

				// Cancel this chat event as no one else needs to see it
				e.setCancelled(true);

				// Send them a message!
				target.sendMessage(ChatColor.translateAlternateColorCodes('&', MessageManager.getInstance().getMessage("not_afk")));

			} else {

				// If their message doesn't have the number we have asked for, AND we are taking action
				if (actionList.containsKey(target.getUniqueId())) {

					// Cancel their message
					e.setCancelled(true);

					// Send them a new message!!
					target.sendMessage(ChatColor.translateAlternateColorCodes('&', MessageManager.getInstance().getMessage("action").replace("%COUNT", actionList.get(target.getUniqueId()).toString()).replace("%NUMBER%",nonceList.get(target.getUniqueId()).toString())));
				}
			}
		}

	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onCommandPreProcess(PlayerCommandPreprocessEvent e) {
		Player target = e.getPlayer();

		// If we are taking action against them, they don't need to be doing commands!
		if (actionList.containsKey(target.getUniqueId())) {
			e.setCancelled(true);
			target.sendMessage(ChatColor.translateAlternateColorCodes('&', MessageManager.getInstance().getMessage("action").replace("%COUNT", actionList.get(target.getUniqueId()).toString()).replace("%NUMBER%",nonceList.get(target.getUniqueId()).toString())));
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onMove(PlayerMoveEvent e) {

		int fromX = (int)e.getFrom().getX();
		int fromZ = (int)e.getFrom().getZ();
		int fromY = (int)e.getFrom().getY();
		int toX = (int)e.getTo().getX();
		int toZ = (int)e.getTo().getZ();
		int toY = (int)e.getTo().getY();

		if (fromX == toX && fromZ == toZ && fromY == toY) return;

		// Only register movement if their coordinate actually changes
		Player target = e.getPlayer();
		lastMove.put(target.getUniqueId(), (System.currentTimeMillis() / 1000L));

		// If they have moved then we can safely remove them from the action lists!
		if (nonceList.containsKey(target.getUniqueId())) {
			nonceList.remove(target.getUniqueId());
			target.sendMessage(ChatColor.translateAlternateColorCodes('&',  MessageManager.getInstance().getMessage("not_afk")));
		}
		if (actionList.containsKey(target.getUniqueId())) {
			actionList.remove(target.getUniqueId());
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInteract(PlayerInteractEvent e) {
		Player target = e.getPlayer();

		// If they bypass this plugin, we dont need to check their interaction
		if (target.hasPermission("afkpatrol.bypass")) return;

		// If they for some reason dont have a last move registered, lets turn the other cheek...
		if (!lastMove.containsKey(target.getUniqueId())) return;

		// Get the current time
		Long currentTime = System.currentTimeMillis() / 1000L;
		// How long since they interacted last?
		Long interactPeriod = currentTime - lastInteract.get(target.getUniqueId());

		// If its been a long time since they interacted last, they probably have just come back from being AFK
		// We can reset their move timer, they havent been interacting, so werent AFK mining!
		if (interactPeriod > interactGracePeriod) {
			lastMove.put(target.getUniqueId(), (System.currentTimeMillis() / 1000L));
		}

		// Calculate how long since they moved last
		Long period = currentTime - lastMove.get(target.getUniqueId());
		// Lets also update their last interact time to now
		lastInteract.put(target.getUniqueId(), (System.currentTimeMillis() / 1000L));



		// IF ACTION SHOULD BE TAKEN (THEY HAVENT MOVED FOR A LONG TIME AND ARE INTERACTING!)
		if (period > actionPeriod) {

			// If they for some reason aren't already in the nonce list, lets add them!
			if (!nonceList.containsKey(target.getUniqueId())) {
				Integer nonce = new Random().nextInt(9999);
				nonceList.put(target.getUniqueId(), nonce);
			}

			// If the player has already been "actioned"
			if (actionList.containsKey(target.getUniqueId())) {
				actionList.put(target.getUniqueId(), actionList.get(target.getUniqueId()) - 1);

				// If the player hasn't get been "actioned"
			} else {
				actionList.put(target.getUniqueId(), kickCount);
			}

			// CANCEL THE EVENT
			e.setCancelled(true);

			// If the player has ignored the message too many times, then kick them!
			if (actionList.get(target.getUniqueId()) <= 0) {
				target.kickPlayer( MessageManager.getInstance().getMessage("afk_kick"));
				return;
			}

			// Notify the player they need to stop AFK mining
			target.sendMessage(ChatColor.translateAlternateColorCodes('&', MessageManager.getInstance().getMessage("action").replace("%COUNT", actionList.get(target.getUniqueId()).toString()).replace("%NUMBER%",nonceList.get(target.getUniqueId()).toString())));

			// IF THE PLAYER SHOULD BE NOTIFIED
		} else if (period > notifyPeriod) {

			// If the config says not to notify them, we will deal with this when we take action!
			if (!shouldNotify) return;

			// IF THEY HAVENT BEEN NOTIFIED YET THEN LETS NOTIFY THEM!
			if (!nonceList.containsKey(target.getUniqueId())) {
				Integer nonce = new Random().nextInt(9999);
				nonceList.put(target.getUniqueId(), nonce);
				target.sendMessage(ChatColor.translateAlternateColorCodes('&',  MessageManager.getInstance().getMessage("notify").replace("%NUMBER%", nonce.toString())));
			}

			// IF THE PLAYER IS JUST NORMAL!
		} else {
			if (nonceList.containsKey(target.getUniqueId())) {
				nonceList.remove(target.getUniqueId());
				target.sendMessage(ChatColor.translateAlternateColorCodes('&',  MessageManager.getInstance().getMessage("not_afk")));
			}
			if (actionList.containsKey(target.getUniqueId())) {
				actionList.remove(target.getUniqueId());
			}
		}

	}

	@EventHandler
	public void onLogout(PlayerQuitEvent e) {
		Player target = e.getPlayer();
		if (lastMove.containsKey(target.getUniqueId())) lastMove.remove(target.getUniqueId()); 
		if (lastInteract.containsKey(target.getUniqueId())) lastInteract.remove(target.getUniqueId()); 
		if (nonceList.containsKey(target.getUniqueId())) {
			nonceList.remove(target.getUniqueId());
		}
		if (actionList.containsKey(target.getUniqueId())) {
			actionList.remove(target.getUniqueId());
		}
	}

	@EventHandler
	public void onLogin(PlayerJoinEvent e) {
		Player target = e.getPlayer();
		lastMove.put(target.getUniqueId(), (System.currentTimeMillis() / 1000L));
		lastInteract.put(target.getUniqueId(), (System.currentTimeMillis() / 1000L));
	}

}
