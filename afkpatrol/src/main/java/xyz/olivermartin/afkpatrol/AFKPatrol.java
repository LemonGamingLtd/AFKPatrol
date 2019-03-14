package xyz.olivermartin.afkpatrol;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import org.bukkit.ChatColor;
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

	private static final Long NOTIFY_PERIOD = 45L;
	private static final Long ACTION_PERIOD = 60L;
	private static final Integer KICK_COUNT = 10;
	private static final Long INTERACT_PERIOD = 10L;
	private HashMap<UUID, Long> lastMove = new HashMap<UUID, Long>();
	private HashMap<UUID, Long> lastInteract = new HashMap<UUID, Long>();
	private HashMap<UUID, Integer> nonceList = new HashMap<UUID, Integer>();
	private HashMap<UUID, Integer> actionList = new HashMap<UUID, Integer>();

	@Override
	public void onEnable() {
		getLogger().info("AFKPatrol is now loading...");
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		getLogger().info("AFKPatrol is now disabling...");
	}

	/*@Override
	public boolean onCommand(CommandSender commandSender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("freeze")) {

			Player sender;

			// Verify if the command sender is a player, and if not then display that only players may use the command
			if (commandSender instanceof Player) {
				sender = (Player) commandSender;
			} else {
				commandSender.sendMessage(ChatColor.DARK_RED + "Only players can use this command!");
				return true;
			}

			// Verify correct command length otherwise display help message
			if (args.length != 1) {
				return false;
			}

			Player target = sender.getServer().getPlayer(args[0]);
			// Make sure the player is online.
			if (target == null) {
				sender.sendMessage(ChatColor.DARK_RED + args[0] + " is not currently online so cannot be frozen!");
				return true;
			}

			UUID targetUUID = target.getUniqueId();

			if (frozenPlayers.containsKey(targetUUID)) {
				frozenPlayers.remove(targetUUID);
				target.sendMessage(ChatColor.GREEN + "You have been unfrozen!");
				sender.sendMessage(ChatColor.AQUA + "You have unfrozen " + target.getDisplayName());
			} else {
				frozenPlayers.put(targetUUID, sender.getUniqueId());
				target.sendMessage(ChatColor.AQUA + "You have been frozen by a staff member!");
				sender.sendMessage(ChatColor.GREEN + "You have frozen " + target.getDisplayName());
			}
			return true;
		}
		return false;
	}*/

	@EventHandler(priority = EventPriority.LOWEST)
	public void onChat(AsyncPlayerChatEvent e) {
		Player target = e.getPlayer();

		if (nonceList.containsKey(target.getUniqueId())) {
			if (e.getMessage().contains(nonceList.get(target.getUniqueId()).toString())) {
				lastMove.put(target.getUniqueId(), (System.currentTimeMillis() / 1000L));
				nonceList.remove(target.getUniqueId());
				actionList.remove(target.getUniqueId());
				e.setCancelled(true);
				target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f&l<< &2AFK PATROL &f&l>> &a&lThank you :)"));
			} else {
				if (actionList.containsKey(target.getUniqueId())) {
					e.setCancelled(true);
					target.sendMessage(ChatColor.translateAlternateColorCodes('&', "       &c- - - &f&l<< &4AFK PATROL &f&l>> &c- - -"));
					target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Your action has been cancelled because you are AFK mining!"));
					target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Please move or type the following number: &c" + nonceList.get(target.getUniqueId())));
					target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7You will be kicked after &c" + actionList.get(target.getUniqueId()) + " &7more AFK actions!"));
				}
			}
		}

	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onCommandPreProcess(PlayerCommandPreprocessEvent e) {
		Player target = e.getPlayer();

		if (actionList.containsKey(target.getUniqueId())) {
			e.setCancelled(true);
			target.sendMessage(ChatColor.translateAlternateColorCodes('&', "       &c- - - &f&l<< &4AFK PATROL &f&l>> &c- - -"));
			target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Your action has been cancelled because you are AFK mining!"));
			target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Please move or type the following number: &c" + nonceList.get(target.getUniqueId())));
			target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7You will be kicked after &c" + actionList.get(target.getUniqueId()) + " &7more AFK actions!"));
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
		if (nonceList.containsKey(target.getUniqueId())) {
			nonceList.remove(target.getUniqueId());
			target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f&l<< &2AFK PATROL &f&l>> &a&lThank you :)"));
		}
		if (actionList.containsKey(target.getUniqueId())) {
			actionList.remove(target.getUniqueId());
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInteract(PlayerInteractEvent e) {
		Player target = e.getPlayer();
		
		if (target.hasPermission("afkpatrol.bypass")) return;

		if (!lastMove.containsKey(target.getUniqueId())) return;

		Long currentTime = System.currentTimeMillis() / 1000L;
		Long interactPeriod = currentTime - lastInteract.get(target.getUniqueId());

		if (interactPeriod > INTERACT_PERIOD) {
			lastMove.put(target.getUniqueId(), (System.currentTimeMillis() / 1000L));
		}

		Long period = currentTime - lastMove.get(target.getUniqueId());
		lastInteract.put(target.getUniqueId(), (System.currentTimeMillis() / 1000L));

		// IF ACTION SHOULD BE TAKEN
		if (period > ACTION_PERIOD) {

			// If the player has already been "actioned"
			if (actionList.containsKey(target.getUniqueId())) {
				actionList.put(target.getUniqueId(), actionList.get(target.getUniqueId()) - 1);
				// If the player hasn't get been "actioned"
			} else {
				actionList.put(target.getUniqueId(), KICK_COUNT);
			}

			// CANCEL THE EVENT
			e.setCancelled(true);

			// If the player has ignored the message too many times, then kick them!
			if (actionList.get(target.getUniqueId()) <= 0) {
				target.kickPlayer("AFK PATROL: You have been kicked for AFK mining!");
				return;
			}

			// Notify the player they need to stop AFK mining
			target.sendMessage(ChatColor.translateAlternateColorCodes('&', "       &c- - - &f&l<< &4AFK PATROL &f&l>> &c- - -"));
			target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Your action has been cancelled because you are AFK mining!"));
			target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Please move or type the following number: &c" + nonceList.get(target.getUniqueId())));
			target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7You will be kicked after &c" + actionList.get(target.getUniqueId()) + " &7more AFK actions!"));

			// IF THE PLAYER SHOULD BE NOTIFIED
		} else if (period > NOTIFY_PERIOD) {

			// IF THEY HAVENT BEEN NOTIFIED YET THEN LETS NOTIFY THEM!
			if (!nonceList.containsKey(target.getUniqueId())) {
				Integer nonce = new Random().nextInt(9999);
				nonceList.put(target.getUniqueId(), nonce);
				target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f&l<< &4AFK PATROL &f&l>> &c&lPlease confirm you are not AFK mining by moving or typing: " + nonce));
			}

			// IF THE PLAYER IS JUST NORMAL!
		} else {
			if (nonceList.containsKey(target.getUniqueId())) {
				nonceList.remove(target.getUniqueId());
				target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f&l<< &2AFK PATROL &f&l>> &a&lThank you :)"));
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
