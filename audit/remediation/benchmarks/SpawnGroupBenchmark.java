package com.gpl.rpg.AndorsTrail.model.actor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.gpl.rpg.AndorsTrail.util.Size;

/**
 * Spawn group lookups during map loading (audit finding M7), with the real content.
 *
 * Loads every monster list and every map listed in res/values/loadresources.xml, the way
 * ResourceLoader does, and replays the lookup TMXMapTranslator makes for each spawn area:
 * - "linear scan": the code before the index, which compares every monster type;
 * - "index": MonsterTypeCollection.getMonsterTypesFromSpawnGroup.
 * Checks that both give the same monster types in the same order for every lookup, then times a
 * full pass of all lookups with each.
 *
 * Run with audit/remediation/scripts/spawn-group-benchmark.sh.
 */
public final class SpawnGroupBenchmark {
	public static void main(String[] args) throws Exception {
		File res = new File(args[0]);
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); // The .tmx files name a DTD URL.
		DocumentBuilder xml = factory.newDocumentBuilder();
		Element resources = xml.parse(new File(res, "values/loadresources.xml")).getDocumentElement();

		HashMap<String, MonsterType> byId = new HashMap<String, MonsterType>();
		MonsterTypeCollection collection = new MonsterTypeCollection();
		for (String name : items(resources, "loadresource_monsters")) {
			JSONArray rows = new JSONArray(new String(Files.readAllBytes(new File(res, "raw/" + name + ".json").toPath()), StandardCharsets.UTF_8));
			for (int i = 0; i < rows.length(); ++i) {
				JSONObject o = rows.getJSONObject(i);
				String id = o.getString("id");
				MonsterType type = new MonsterType(id, id, o.optString("spawnGroup", id), 0, null, null, false, null,
						MonsterType.MonsterClass.humanoid, MonsterType.AggressionType.none, new Size(1, 1),
						0, 0, 10, 1, 10, 10, 0, 0, 0, null, 0, 0, null, null, null);
				byId.put(id, type);
				collection.add(type);
			}
		}

		List<String> lookups = new ArrayList<String>();
		List<String> maps = items(resources, "loadresource_maps");
		for (String name : maps) {
			NodeList objects = xml.parse(new File(res, "xml/" + name + ".tmx")).getElementsByTagName("object");
			for (int i = 0; i < objects.getLength(); ++i) {
				Element object = (Element) objects.item(i);
				if (!object.getAttribute("type").equalsIgnoreCase("spawn")) continue;
				String spawnGroup = object.getAttribute("name");
				NodeList properties = object.getElementsByTagName("property");
				for (int j = 0; j < properties.getLength(); ++j) {
					Element p = (Element) properties.item(j);
					if (p.getAttribute("name").equalsIgnoreCase("spawngroup")) spawnGroup = p.getAttribute("value");
				}
				lookups.add(spawnGroup);
			}
		}

		for (String group : lookups) {
			if (!ids(linearScan(byId, group)).equals(ids(collection.getMonsterTypesFromSpawnGroup(group)))) {
				throw new AssertionError("Different result for spawn group " + group);
			}
		}

		System.out.println("monster types: " + byId.size() + ", maps: " + maps.size() + ", spawn area lookups: " + lookups.size());
		System.out.println("same result for every lookup: yes");
		System.out.println("equalsIgnoreCase comparisons per pass, linear scan: " + (long) byId.size() * lookups.size());
		int passes = 20;
		for (int round = 0; round < 2; ++round) { // The first round warms up the JIT.
			long linear = 0, indexed = 0;
			for (int pass = 0; pass < passes; ++pass) {
				long start = System.nanoTime();
				for (String group : lookups) linearScan(byId, group);
				linear += System.nanoTime() - start;

				MonsterTypeCollection fresh = new MonsterTypeCollection(); // Includes building the index.
				for (MonsterType t : byId.values()) fresh.add(t);
				start = System.nanoTime();
				for (String group : lookups) fresh.getMonsterTypesFromSpawnGroup(group);
				indexed += System.nanoTime() - start;
			}
			if (round == 1) {
				System.out.printf("ms per pass (all lookups), mean of %d: linear scan %.1f, index %.2f%n",
						passes, linear / 1e6 / passes, indexed / 1e6 / passes);
			}
		}
	}

	// MonsterTypeCollection.getMonsterTypesFromSpawnGroup before the index.
	private static ArrayList<MonsterType> linearScan(HashMap<String, MonsterType> monsterTypesById, String spawnGroup) {
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

	private static List<String> items(Element resources, String arrayName) {
		NodeList arrays = resources.getElementsByTagName("array");
		for (int i = 0; i < arrays.getLength(); ++i) {
			Element array = (Element) arrays.item(i);
			if (!array.getAttribute("name").equals(arrayName)) continue;
			List<String> result = new ArrayList<String>();
			NodeList items = array.getElementsByTagName("item");
			for (int j = 0; j < items.getLength(); ++j) {
				String item = items.item(j).getTextContent().trim();
				result.add(item.substring(item.indexOf('/') + 1));
			}
			return result;
		}
		throw new IllegalArgumentException("No array " + arrayName);
	}
}
