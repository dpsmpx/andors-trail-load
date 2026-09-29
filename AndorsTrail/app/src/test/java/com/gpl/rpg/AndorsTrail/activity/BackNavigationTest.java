package com.gpl.rpg.AndorsTrail.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * On Android 16 the system Back gesture no longer reaches {@code onBackPressed()} or
 * {@code OnBackPressedCallback}s from androidx.activity 1.0.0 (see {@link BackNavigation}).
 * Custom Back handling that is not registered with BackNavigation silently stops working there.
 */
public final class BackNavigationTest {
	private static final Pattern CUSTOM_BACK_HANDLING = Pattern.compile(
			"void\\s+onBackPressed\\s*\\(\\s*\\)\\s*\\{|new\\s+OnBackPressedCallback\\s*\\(");

	@Test
	public void customBackHandlingIsRegisteredForAndroid16() throws IOException {
		List<String> handlers = new ArrayList<>();
		List<String> unregistered = new ArrayList<>();
		try (Stream<Path> files = Files.walk(new File("src/main/java").toPath())) {
			for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
				String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				if (!CUSTOM_BACK_HANDLING.matcher(source).find()) continue;
				handlers.add(file.getFileName().toString());
				if (!source.contains("BackNavigation.register(")) unregistered.add(file.getFileName().toString());
			}
		}
		assertTrue("expected to find the activities with custom Back handling", handlers.size() >= 2);
		assertEquals("custom Back handling without BackNavigation.register", new ArrayList<String>(), unregistered);
	}
}
