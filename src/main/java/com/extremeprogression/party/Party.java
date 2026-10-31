package com.extremeprogression.party;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One party (design §16), as {@code <world>/extremeprogression/parties.json} keeps it. */
public class Party {
	public String id = "";
	/** The leader's UUID: always one of the members. */
	public String leader = "";
	/** The members' UUIDs in the order they joined, the leader among them. */
	public List<String> members = new ArrayList<>();
	/** Every member's name when last seen, for the list while they are offline. */
	public Map<String, String> names = new LinkedHashMap<>();
	public long created;

	public boolean has(UUID player) {
		return members.contains(player.toString());
	}

	public boolean isLeader(UUID player) {
		return leader.equals(player.toString());
	}

	public int size() {
		return members.size();
	}

	public String name(String uuid) {
		return names.getOrDefault(uuid, "?");
	}

	/** The member with this name (any case), or null. */
	public String byName(String name) {
		for (String m : members) {
			if (name(m).equalsIgnoreCase(name)) {
				return m;
			}
		}
		return null;
	}
}
