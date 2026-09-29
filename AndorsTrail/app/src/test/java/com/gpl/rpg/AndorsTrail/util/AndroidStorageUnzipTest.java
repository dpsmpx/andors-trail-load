package com.gpl.rpg.AndorsTrail.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** "Import world map" must only write inside the world map folder (audit finding M2, Zip Slip). */
public final class AndroidStorageUnzipTest {
	@Rule public final TemporaryFolder temp = new TemporaryFolder();
	private File savegames;
	private File worldmap;

	@Before
	public void createFolders() {
		savegames = new File(temp.getRoot(), "andors-trail");
		worldmap = new File(savegames, "worldmap");
		assertTrue(worldmap.mkdirs());
	}

	@Test
	public void extractsAnExportedWorldMap() throws Exception {
		File zip = zip("crossglen.png", "fallhaven_ne.png");

		AndroidStorage.unzipToDirectory(zip, worldmap, false);

		assertArrayEquals(bytes("crossglen.png"), Files.readAllBytes(new File(worldmap, "crossglen.png").toPath()));
		assertArrayEquals(bytes("fallhaven_ne.png"), Files.readAllBytes(new File(worldmap, "fallhaven_ne.png").toPath()));
	}

	@Test
	public void keepsExistingFilesWhenNotOverwriting() throws Exception {
		Files.write(new File(worldmap, "crossglen.png").toPath(), bytes("old"));

		AndroidStorage.unzipToDirectory(zip("crossglen.png"), worldmap, false);

		assertArrayEquals(bytes("old"), Files.readAllBytes(new File(worldmap, "crossglen.png").toPath()));
	}

	@Test
	public void rejectsEntriesOutsideTheTargetDirectory() throws Exception {
		assertRejected("../savegame1", new File(savegames, "savegame1"));
		assertRejected("maps/../../savegame2", new File(savegames, "savegame2"));
		assertRejected("../worldmap-other/x.png", new File(savegames, "worldmap-other/x.png"));
	}

	private void assertRejected(String entryName, File escaped) throws Exception {
		try {
			AndroidStorage.unzipToDirectory(zip("crossglen.png", entryName), worldmap, true);
			fail("expected IOException for " + entryName);
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains(entryName));
		}
		assertFalse(escaped + " must not be written", escaped.exists());
	}

	private File zip(String... entryNames) throws IOException {
		File zip = temp.newFile();
		try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
			for (String name : entryNames) {
				out.putNextEntry(new ZipEntry(name));
				out.write(bytes(name));
				out.closeEntry();
			}
		}
		return zip;
	}

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}
}
