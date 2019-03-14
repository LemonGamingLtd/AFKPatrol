package xyz.olivermartin.afkpatrol;

import java.util.HashMap;
import java.util.Map;

public class MessageManager {

	private static MessageManager instance;
	
	public static MessageManager getInstance() {
		return instance;
	}
	
	static {
		instance = new MessageManager();
	}
	
	// END STATIC
	
	private Map<String, String> messages;
	
	private MessageManager() {
		messages = new HashMap<String, String>();
	}
	
	public void loadMessage(String id, String message) {
		messages.put(id.toLowerCase(), message);
	}
	
	public String getMessage(String id) {
		if (!messages.containsKey(id.toLowerCase())) {
			return "[AFKPatrol][ERROR] No message found in config for: " + id.toLowerCase();
		} else {
			return messages.get(id.toLowerCase());
		}
	}
	
}
