package com.gpl.rpg.AndorsTrail.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class AtomicFileWriterTest {
	@Rule public final TemporaryFolder folder = new TemporaryFolder();

	private static final byte[] OLD = "previous savegame".getBytes(StandardCharsets.UTF_8);
	private static final byte[] NEW = "new savegame with more data".getBytes(StandardCharsets.UTF_8);

	@Test
	public void writesNewFile() throws IOException {
		File target = new File(folder.getRoot(), "savegame1");
		AtomicFileWriter.write(target, NEW);
		assertArrayEquals(NEW, Files.readAllBytes(target.toPath()));
		assertNoTemporaryFile(target);
	}

	@Test
	public void replacesExistingFileWithShorterContent() throws IOException {
		File target = existingFile(NEW);
		AtomicFileWriter.write(target, OLD);
		assertArrayEquals(OLD, Files.readAllBytes(target.toPath()));
		assertNoTemporaryFile(target);
	}

	@Test
	public void failedWriteKeepsPreviousContent() throws IOException {
		File target = existingFile(OLD);
		try {
			AtomicFileWriter.write(target, new AtomicFileWriter.Content() {
				@Override
				public void writeTo(OutputStream out) throws IOException {
					out.write(NEW, 0, 5);
					throw new IOException("No space left on device");
				}
			});
			fail("the IOException must reach the caller");
		} catch (IOException expected) {
			assertEquals("No space left on device", expected.getMessage());
		}
		assertArrayEquals(OLD, Files.readAllBytes(target.toPath()));
		assertNoTemporaryFile(target);
	}

	@Test
	public void runtimeFailureWhileWritingKeepsPreviousContent() throws IOException {
		File target = existingFile(OLD);
		try {
			AtomicFileWriter.write(target, new AtomicFileWriter.Content() {
				@Override
				public void writeTo(OutputStream out) throws IOException {
					out.write(NEW, 0, 5);
					throw new IllegalStateException("bitmap was recycled");
				}
			});
			fail("the exception must reach the caller");
		} catch (IllegalStateException expected) {
			// Expected.
		}
		assertArrayEquals(OLD, Files.readAllBytes(target.toPath()));
		assertNoTemporaryFile(target);
	}

	@Test
	public void interruptedWriteLeavesPreviousContentAndIsRecoveredByTheNextWrite() throws IOException {
		File target = existingFile(OLD);
		// A process killed while writing leaves a partial temporary file behind, but never touches the target.
		File temp = new File(target.getPath() + AtomicFileWriter.TEMP_SUFFIX);
		try (FileOutputStream out = new FileOutputStream(temp)) {
			out.write(NEW, 0, 3);
		}
		assertArrayEquals(OLD, Files.readAllBytes(target.toPath()));

		AtomicFileWriter.write(target, NEW);
		assertArrayEquals(NEW, Files.readAllBytes(target.toPath()));
		assertNoTemporaryFile(target);
	}

	@Test
	public void failedRenameKeepsPreviousContent() throws IOException {
		// A non-empty directory cannot be replaced by a file, so the rename fails.
		File target = folder.newFolder("savegame2");
		File inside = new File(target, "keep");
		Files.write(inside.toPath(), OLD);
		try {
			AtomicFileWriter.write(target, NEW);
			fail("a failed rename must be reported");
		} catch (IOException expected) {
			// Expected.
		}
		assertTrue(target.isDirectory());
		assertArrayEquals(OLD, Files.readAllBytes(inside.toPath()));
		assertNoTemporaryFile(target);
	}

	@Test
	public void missingDirectoryIsReported() {
		File target = new File(new File(folder.getRoot(), "missing"), "savegame3");
		try {
			AtomicFileWriter.write(target, NEW);
			fail("writing into a missing directory must fail");
		} catch (IOException expected) {
			// Expected.
		}
		assertFalse(target.exists());
	}

	private File existingFile(byte[] content) throws IOException {
		File target = new File(folder.getRoot(), "savegame1");
		Files.write(target.toPath(), content);
		return target;
	}

	private static void assertNoTemporaryFile(File target) {
		assertFalse(new File(target.getPath() + AtomicFileWriter.TEMP_SUFFIX).exists());
	}
}
