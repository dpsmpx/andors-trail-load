package com.gpl.rpg.AndorsTrail.model.map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Collections;

import org.junit.Test;

import com.gpl.rpg.AndorsTrail.AndorsTrailApplication;
import com.gpl.rpg.AndorsTrail.context.WorldContext;
import com.gpl.rpg.AndorsTrail.model.ModelContainer;
import com.gpl.rpg.AndorsTrail.util.Coord;
import com.gpl.rpg.AndorsTrail.util.CoordRect;
import com.gpl.rpg.AndorsTrail.util.Range;
import com.gpl.rpg.AndorsTrail.util.Size;

/**
 * Spawn areas are restored by ID. Areas that were not in the savegame must be initialized, and
 * the restored ones must keep their saved state, also when an update adds areas before them
 * (audit finding M3).
 */
public final class PredefinedMapSpawnAreaTest {
	private final WorldContext world = new WorldContext();

	@Test
	public void areasAddedBeforeSavedAreasDoNotResetThem() throws Exception {
		PredefinedMap saved = map("a", "b");
		switchOff(saved, "a", "b");

		PredefinedMap updated = map("new", "a", "b");
		updated.spawnAreas[0].isSpawning = false; // Map objects are reused by the next game in the same process.
		load(updated, save(saved));

		assertFalse("a keeps its saved state", updated.spawnAreas[1].isSpawning);
		assertFalse("b keeps its saved state", updated.spawnAreas[2].isSpawning);
		assertTrue("the new area is initialized", updated.spawnAreas[0].isSpawning);
	}

	@Test
	public void duplicateAreaIdsAreRestoredOnce() throws Exception {
		// 303 area IDs are duplicated in current content (AUDIT.md, M3).
		PredefinedMap saved = map("dup", "dup");
		saved.spawnAreas[0].isSpawning = false;

		PredefinedMap updated = map("new", "dup", "dup");
		load(updated, save(saved));

		assertTrue(updated.spawnAreas[0].isSpawning);
		assertFalse("the first saved area goes to the first matching area", updated.spawnAreas[1].isSpawning);
		assertTrue("the second saved area goes to the second matching area", updated.spawnAreas[2].isSpawning);
	}

	private static PredefinedMap map(String... areaIDs) {
		MonsterSpawnArea[] areas = new MonsterSpawnArea[areaIDs.length];
		for (int i = 0; i < areas.length; ++i) {
			areas[i] = new MonsterSpawnArea(new CoordRect(new Coord(i, 0), new Size(1, 1)), new Range(1, 0), new Range(10, 0),
					areaIDs[i], new String[0], false, false, "", true);
		}
		return new PredefinedMap(0, "map", new Size(10, 10), new MapObject[0], areas, Collections.<String>emptyList(), true, null);
	}

	private static void switchOff(PredefinedMap map, String... areaIDs) {
		for (MonsterSpawnArea area : map.spawnAreas) {
			for (String id : areaIDs) {
				if (area.areaID.equals(id)) area.isSpawning = false;
			}
		}
	}

	private byte[] save(PredefinedMap map) throws Exception {
		world.model = new ModelContainer(1, true);
		map.visited = true;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		map.writeToParcel(new DataOutputStream(bytes), world);
		return bytes.toByteArray();
	}

	private void load(PredefinedMap map, byte[] data) throws Exception {
		map.readFromParcel(new DataInputStream(new ByteArrayInputStream(data)), world, null, AndorsTrailApplication.CURRENT_VERSION);
	}
}
