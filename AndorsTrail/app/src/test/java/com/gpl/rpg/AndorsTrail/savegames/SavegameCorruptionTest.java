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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import com.gpl.rpg.AndorsTrail.context.WorldContext;
import com.gpl.rpg.AndorsTrail.model.GameStatistics;
import com.gpl.rpg.AndorsTrail.model.ModelContainer;
import com.gpl.rpg.AndorsTrail.model.item.Inventory;
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
 */
public final class SavegameCorruptionTest {
	private static final String MAP = "testmap";
	private static final int GOLD_MARKER = 0x13572468;

	@Test
	public void intactSavegameIsReadUpToTheEnd() throws Exception {
		// Guards the fixture: the damaged variants below differ from this one only by the damage.
		WorldContext world = createWorld();
		DataInputStream src = new DataInputStream(new ByteArrayInputStream(save(createSavedWorld())));
		Savegames.FileHeader header = new Savegames.FileHeader(src, false);
		world.maps.readFromParcel(src, world, null, header.fileversion);
		world.model = new ModelContainer(src, world, null, header.fileversion);
		assertEquals(-1, src.read());
		assertEquals(GOLD_MARKER, world.model.player.inventory.gold);
		assertTrue(map(world).visited);
	}

	@Test
	public void truncatedSavegameFailsAndLeavesNoMapState() throws Exception {
		byte[] data = save(createSavedWorld());
		WorldContext world = assertDamaged(Arrays.copyOf(data, data.length - 10));
		assertFalse("maps must not keep state from the failed load", map(world).visited);
		assertTrue(map(world).spawnAreas[0].isSpawning);
	}

	@Test
	public void outOfRangeWornSlotCountFails() throws Exception {
		// The count of worn slots follows the gold. Claim one slot more than exists, and fill it.
		byte[] data = save(createSavedWorld());
		int count = indexOf(data, intBytes(GOLD_MARKER), 0) + 4;
		int slots = readInt(data, count);
		ByteArrayOutputStream damaged = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(damaged);
		out.write(data, 0, count);
		out.writeInt(slots + 1);
		for (int i = 0; i < slots; ++i) out.writeBoolean(false);
		out.writeBoolean(true);
		out.writeUTF("some_item");
		out.write(data, count + 4 + slots, data.length - (count + 4 + slots));

		WorldContext world = assertDamaged(damaged.toByteArray());
		assertFalse(map(world).visited);
	}

	@Test
	public void unknownCurrentMapFails() throws Exception {
		// The map name is stored twice: in the list of saved maps and as the current map.
		byte[] data = save(createSavedWorld());
		byte[] name = MAP.getBytes(StandardCharsets.UTF_8);
		int currentMap = indexOf(data, name, indexOf(data, name, 0) + 1);
		byte[] other = "nomap00".getBytes(StandardCharsets.UTF_8);
		System.arraycopy(other, 0, data, currentMap, name.length);

		assertDamaged(data);
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

	private static WorldContext createSavedWorld() {
		WorldContext world = createWorld();
		PredefinedMap map = map(world);
		map.visited = true;
		map.spawnAreas[0].isSpawning = false;
		world.model = new ModelContainer(1, false);
		world.model.player.setName("Tester");
		world.model.player.setSpawnPlace("home", "bed");
		world.model.player.inventory.gold = GOLD_MARKER;
		world.model.currentMaps.map = map;
		return world;
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

	private static byte[] save(WorldContext world) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Savegames.saveWorld(world, out, "test");
		return out.toByteArray();
	}

	private static byte[] intBytes(int value) {
		return new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
	}

	private static int readInt(byte[] data, int offset) {
		return ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16) | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
	}

	private static int indexOf(byte[] data, byte[] pattern, int from) {
		for (int i = from; i <= data.length - pattern.length; ++i) {
			if (Arrays.equals(Arrays.copyOfRange(data, i, i + pattern.length), pattern)) return i;
		}
		throw new AssertionError("pattern not found: " + Arrays.toString(pattern));
	}
}
