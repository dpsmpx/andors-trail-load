package com.gpl.rpg.AndorsTrail.savegames;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.Test;

import com.gpl.rpg.AndorsTrail.context.WorldContext;
import com.gpl.rpg.AndorsTrail.model.GameStatistics;
import com.gpl.rpg.AndorsTrail.model.ModelContainer;
import com.gpl.rpg.AndorsTrail.model.map.MapObject;
import com.gpl.rpg.AndorsTrail.model.map.MonsterSpawnArea;
import com.gpl.rpg.AndorsTrail.model.map.PredefinedMap;
import com.gpl.rpg.AndorsTrail.util.Coord;
import com.gpl.rpg.AndorsTrail.util.CoordRect;
import com.gpl.rpg.AndorsTrail.util.Range;
import com.gpl.rpg.AndorsTrail.util.Size;

/**
 * A damaged savegame must fail to load with an IOException, which the caller reports as "cannot
 * load", instead of crashing the app with a RuntimeException (audit finding M4).
 *
 * The savegames here contain the header and the maps. The player part is not written, because
 * Player.writeToParcel uses android.util.SparseIntArray, which JVM unit tests do not provide.
 */
public final class SavegameCorruptionTest {
	private static final String MAP = "testmap";

	@Test
	public void mapsOfTheFixtureAreReadBack() throws Exception {
		// Guards the fixture used below: the maps part is complete and carries the saved state.
		WorldContext world = createWorld();
		DataInputStream src = new DataInputStream(new ByteArrayInputStream(saveHeaderAndMaps()));
		Savegames.FileHeader header = new Savegames.FileHeader(src, false);
		world.maps.readFromParcel(src, world, null, header.fileversion);
		assertEquals(-1, src.read());
		assertTrue(map(world).visited);
		assertFalse(map(world).spawnAreas[0].isSpawning);
	}

	@Test
	public void truncatedSavegameFailsAndLeavesNoMapState() throws Exception {
		// The file ends where the player should start.
		WorldContext world = assertDamaged(saveHeaderAndMaps());
		assertFalse("maps must not keep state from the failed load", map(world).visited);
		assertTrue(map(world).spawnAreas[0].isSpawning);
	}

	@Test
	public void moreSpawnAreasThanTheMapHasFails() throws Exception {
		// Before file version 43, spawn areas were stored by index. A count larger than the
		// number of areas in the map used to throw ArrayIndexOutOfBoundsException.
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(42); // file version
		out.writeUTF("Tester");
		out.writeUTF("test");
		out.writeInt(1); // maps
		out.writeUTF(MAP);
		out.writeBoolean(true); // map data follows
		out.writeInt(2); // spawn areas; the map has one
		for (int i = 0; i < 2; ++i) {
			out.writeBoolean(false); // is spawning
			out.writeInt(0); // monsters
		}

		WorldContext world = assertDamaged(bytes.toByteArray());
		assertTrue(map(world).spawnAreas[0].isSpawning);
	}

	@Test
	public void hugeChecksumLengthFailsBeforeAllocating() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(0); // deaths
		out.writeInt(0); // killed monsters
		out.writeInt(0); // used items
		out.writeInt(0); // spent gold
		out.writeInt(1); // start lives
		out.writeBoolean(false); // unlimited saves
		out.writeBoolean(false); // altered savegame
		out.writeInt(Integer.MAX_VALUE); // checksum length

		try {
			new GameStatistics(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), createWorld(), 81);
			fail("expected IOException");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("checksum"));
		}
	}

	private static WorldContext assertDamaged(byte[] data) throws Exception {
		WorldContext world = createWorld();
		Savegames.FileHeader header = new Savegames.FileHeader(new DataInputStream(new ByteArrayInputStream(data)), false);
		try {
			Savegames.loadWorld(null, world, null, null, new ByteArrayInputStream(data), header);
			fail("expected IOException");
		} catch (IOException expected) {
			// Loading reports this as LoadSavegameResult.unknownError.
		}
		return world;
	}

	private static byte[] saveHeaderAndMaps() throws IOException {
		WorldContext world = createWorld();
		PredefinedMap map = map(world);
		map.visited = true;
		map.spawnAreas[0].isSpawning = false;
		world.model = new ModelContainer(1, false);
		world.model.currentMaps.map = map;

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		Savegames.FileHeader.writeToParcel(out, "Tester", "test", 0, false, false, "id", 0, false);
		world.maps.writeToParcel(out, world);
		return bytes.toByteArray();
	}

	private static WorldContext createWorld() {
		WorldContext world = new WorldContext();
		MonsterSpawnArea area = new MonsterSpawnArea(new CoordRect(new Coord(1, 1), new Size(2, 2)), new Range(1, 0), new Range(10, 0),
				"area", new String[0], false, false, "", true);
		ArrayList<PredefinedMap> maps = new ArrayList<PredefinedMap>();
		maps.add(new PredefinedMap(0, MAP, new Size(10, 10), new MapObject[0], new MonsterSpawnArea[] { area },
				Collections.<String>emptyList(), true, null));
		world.maps.addAll(maps);
		return world;
	}

	private static PredefinedMap map(WorldContext world) {
		PredefinedMap map = world.maps.findPredefinedMap(MAP);
		assertNotNull(map);
		return map;
	}
}
