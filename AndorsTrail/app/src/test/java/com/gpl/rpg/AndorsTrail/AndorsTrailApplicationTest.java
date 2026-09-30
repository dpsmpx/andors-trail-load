package com.gpl.rpg.AndorsTrail;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.Test;

/** The in-game language setting (res/values/arrays.xml) maps to a locale (audit finding L6). */
public final class AndorsTrailApplicationTest {
	private static final Locale SYSTEM = new Locale("fi", "FI");

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
}
