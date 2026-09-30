package com.gpl.rpg.AndorsTrail.model.actor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import com.gpl.rpg.AndorsTrail.util.Size;

/**
 * The spawn group index must return what the former linear scan returned, in the same order
 * (audit finding M7).
 */
public final class MonsterTypeCollectionTest {
	private static final String[][] TYPES = {
			// id, spawn group
			{ "rat1", "Rats" },
			{ "rat2", "rats" },
			{ "rat3", "RATS" },
			{ "boss", "boss" },
			{ "wolf", "wolves" },
			{ "wolf_pup", "Wolves" },
			{ "guard", "town_guard" },
			{ "guard_alone", "guard_alone" },
	};

	@Test
	public void lookupMatchesTheLinearScan() {
		// Same insertions into a HashMap of the same capacity give the same iteration order.
		HashMap<String, MonsterType> reference = new HashMap<String, MonsterType>();
		MonsterTypeCollection collection = new MonsterTypeCollection();
		for (String[] t : TYPES) {
			MonsterType type = monsterType(t[0], t[1]);
			reference.put(type.id, type);
			collection.add(type);
		}

		String[] queries = { "rats", "Rats", "RATS", "rAtS", "boss", "BOSS", "wolves", "town_guard",
				"guard", "GUARD", "guard_alone", "rat1", "no_such_group", "", null };
		for (String query : queries) {
			assertEquals(String.valueOf(query), ids(linearScan(reference, query)), ids(collection.getMonsterTypesFromSpawnGroup(query)));
		}
		assertEquals(3, collection.getMonsterTypesFromSpawnGroup("rats").size());
		assertTrue(collection.getMonsterTypesFromSpawnGroup("no_such_group").isEmpty());
	}

	@Test
	public void typesAddedLaterAreFound() {
		// ResourceLoader adds the monster lists one after the other.
		MonsterTypeCollection collection = new MonsterTypeCollection();
		collection.add(monsterType("rat1", "rats"));
		assertEquals(1, collection.getMonsterTypesFromSpawnGroup("rats").size());

		collection.add(monsterType("rat2", "Rats"));
		assertEquals(2, collection.getMonsterTypesFromSpawnGroup("rats").size());
	}

	@Test
	public void resultCanBeChangedByTheCaller() {
		MonsterTypeCollection collection = new MonsterTypeCollection();
		for (String[] t : TYPES) collection.add(monsterType(t[0], t[1]));
		collection.getMonsterTypesFromSpawnGroup("rats").clear();
		assertEquals(3, collection.getMonsterTypesFromSpawnGroup("rats").size());
	}

	// The code before the index.
	private static List<MonsterType> linearScan(HashMap<String, MonsterType> monsterTypesById, String spawnGroup) {
		ArrayList<MonsterType> result = new ArrayList<MonsterType>();
		for (MonsterType t : monsterTypesById.values()) {
			if (t.spawnGroup.equalsIgnoreCase(spawnGroup)) result.add(t);
		}
		if (result.isEmpty()) {
			MonsterType t = monsterTypesById.get(spawnGroup);
			if (t != null) result.add(t);
		}
		return result;
	}

	private static List<String> ids(List<MonsterType> types) {
		List<String> ids = new ArrayList<String>();
		for (MonsterType t : types) ids.add(t.id);
		return ids;
	}

	private static MonsterType monsterType(String id, String spawnGroup) {
		return new MonsterType(id, id, spawnGroup, 0, null, null, false, null, MonsterType.MonsterClass.humanoid,
				MonsterType.AggressionType.none, new Size(1, 1), 0, 0, 10, 1, 10, 10, 0, 0, 0, null, 0, 0, null, null, null);
	}
}
