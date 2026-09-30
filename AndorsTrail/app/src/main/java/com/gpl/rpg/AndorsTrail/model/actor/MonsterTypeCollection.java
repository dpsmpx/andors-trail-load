package com.gpl.rpg.AndorsTrail.model.actor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import com.gpl.rpg.AndorsTrail.AndorsTrailApplication;
import com.gpl.rpg.AndorsTrail.resource.parsers.MonsterTypeParser;
import com.gpl.rpg.AndorsTrail.util.L;

public final class MonsterTypeCollection {
	private final HashMap<String, MonsterType> monsterTypesById = new HashMap<String, MonsterType>();
	// Spawn group -> monster types, in the order of monsterTypesById. Built on first use, because
	// map loading looks up every spawn area (about 6,700) and scanning all types each time is slow.
	// CASE_INSENSITIVE_ORDER matches like String.equalsIgnoreCase.
	private TreeMap<String, ArrayList<MonsterType>> monsterTypesBySpawnGroup = null;

	public MonsterType getMonsterType(String id) {
		if (AndorsTrailApplication.DEVELOPMENT_VALIDATEDATA) {
			if (!monsterTypesById.containsKey(id)) {
				L.log("WARNING: Cannot find MonsterType for id \"" + id + "\".");
			}
		}
		return monsterTypesById.get(id);
	}

	public ArrayList<MonsterType> getMonsterTypesFromSpawnGroup(String spawnGroup) {
		ArrayList<MonsterType> result = new ArrayList<MonsterType>();
		if (spawnGroup != null) {
			ArrayList<MonsterType> group = getMonsterTypesBySpawnGroup().get(spawnGroup);
			if (group != null) result.addAll(group);
		}
		//If the spawnGroup is empty, it should be a direct reference to a MonsterType's id.
		if (result.isEmpty()) {
			MonsterType t = monsterTypesById.get(spawnGroup);
			if (t != null) result.add(t);
		}
		return result;
	}

	public MonsterType guessMonsterTypeFromName(String name) {
		for (MonsterType t : monsterTypesById.values()) {
			if (t.name.equalsIgnoreCase(name)) return t;
		}
		return null;
	}

	private TreeMap<String, ArrayList<MonsterType>> getMonsterTypesBySpawnGroup() {
		if (monsterTypesBySpawnGroup == null) {
			TreeMap<String, ArrayList<MonsterType>> index = new TreeMap<String, ArrayList<MonsterType>>(String.CASE_INSENSITIVE_ORDER);
			for (MonsterType t : monsterTypesById.values()) {
				ArrayList<MonsterType> group = index.get(t.spawnGroup);
				if (group == null) {
					group = new ArrayList<MonsterType>();
					index.put(t.spawnGroup, group);
				}
				group.add(t);
			}
			monsterTypesBySpawnGroup = index;
		}
		return monsterTypesBySpawnGroup;
	}

	public void initialize(MonsterTypeParser parser, String input) {
		parser.parseRows(input, monsterTypesById);
		monsterTypesBySpawnGroup = null;
	}

	// Package-private for MonsterTypeCollectionTest: JVM unit tests cannot parse JSON.
	void add(MonsterType type) {
		monsterTypesById.put(type.id, type);
		monsterTypesBySpawnGroup = null;
	}
}
