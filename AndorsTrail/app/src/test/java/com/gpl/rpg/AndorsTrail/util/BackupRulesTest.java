package com.gpl.rpg.AndorsTrail.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.gpl.rpg.AndorsTrail.controller.Constants;

/**
 * Auto Backup skips everything when the data exceeds 25 MB, so the regenerable world map cache and
 * the debug logs are excluded from cloud backups (audit finding L3). The excluded paths must match
 * the directories the code writes to.
 */
public final class BackupRulesTest {
	private static final String WORLDMAP = Constants.FILENAME_SAVEGAME_DIRECTORY + "/" + Constants.FILENAME_WORLDMAP_DIRECTORY;
	private static final String LOG = Constants.FILENAME_SAVEGAME_DIRECTORY + "/log"; // AndorsTrailApplication.onCreate

	@Test
	public void cloudBackupsExcludeTheWorldMapCacheAndLogs() throws Exception {
		assertEquals(Arrays.asList(WORLDMAP, LOG), excludedExternalPaths("backup_rules.xml", "full-backup-content"));
		assertEquals(Arrays.asList(WORLDMAP, LOG), excludedExternalPaths("data_extraction_rules.xml", "cloud-backup"));
		assertEquals(Arrays.asList(LOG), excludedExternalPaths("data_extraction_rules.xml", "device-transfer"));
	}

	private static List<String> excludedExternalPaths(String file, String section) throws Exception {
		// Gradle runs unit tests in the module directory, AndorsTrail/app.
		Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new File("../res/xml/" + file)).getDocumentElement();
		Element parent = root.getTagName().equals(section) ? root : (Element) root.getElementsByTagName(section).item(0);
		List<String> paths = new ArrayList<String>();
		NodeList excludes = parent.getElementsByTagName("exclude");
		for (int i = 0; i < excludes.getLength(); ++i) {
			Element exclude = (Element) excludes.item(i);
			assertTrue(exclude.getAttribute("path"), exclude.getAttribute("domain").equals("external"));
			paths.add(exclude.getAttribute("path"));
		}
		return paths;
	}
}
