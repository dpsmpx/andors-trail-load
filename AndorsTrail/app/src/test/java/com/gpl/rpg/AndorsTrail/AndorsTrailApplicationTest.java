package com.gpl.rpg.AndorsTrail;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class AndorsTrailApplicationTest {
	@Rule public final TemporaryFolder temp = new TemporaryFolder();
	private static final Locale SYSTEM = new Locale("fi", "FI");

	// The in-game language setting (res/values/arrays.xml) maps to a locale (audit finding L6).
	@Test
	public void languageValuesMapToLocales() {
		assertEquals(SYSTEM, AndorsTrailApplication.localeForLanguageTag("default", SYSTEM));
		assertEquals(SYSTEM, AndorsTrailApplication.localeForLanguageTag(null, SYSTEM));
		assertEquals(new Locale("de"), AndorsTrailApplication.localeForLanguageTag("de", SYSTEM));
		assertEquals(new Locale("pt", "BR"), AndorsTrailApplication.localeForLanguageTag("pt-BR", SYSTEM));
	}

	@Test
	public void resourceQualifierRegionIsARegion() {
		// Formerly Locale("zh", "RCN"), which only matched Chinese resources by fallback.
		assertEquals(Locale.SIMPLIFIED_CHINESE, AndorsTrailApplication.localeForLanguageTag("zh-rCN", SYSTEM));
	}

	// Debug builds write a log file per start (audit finding L8).
	@Test
	public void onlyTheNewestLogFilesAreKept() throws Exception {
		File dir = temp.getRoot();
		for (String name : new String[] { "logcat1759000000003.txt", "logcat1759000000001.txt", "logcat1759000000002.txt", "notes.txt" }) {
			assertTrue(new File(dir, name).createNewFile());
		}
		AndorsTrailApplication.deleteOldLogFiles(dir, 2);
		String[] left = dir.list();
		Arrays.sort(left);
		assertArrayEquals(new String[] { "logcat1759000000002.txt", "logcat1759000000003.txt", "notes.txt" }, left);

		AndorsTrailApplication.deleteOldLogFiles(new File(dir, "missing"), 2);
	}
}
