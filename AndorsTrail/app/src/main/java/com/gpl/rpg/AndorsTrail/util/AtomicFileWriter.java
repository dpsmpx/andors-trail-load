package com.gpl.rpg.AndorsTrail.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Replaces a file so that it always holds either its previous content or the complete new content.
 *
 * <p>The content is written to a temporary file next to the target, synced to the storage device and
 * then renamed over the target. If anything fails, the target is left untouched and the temporary
 * file is removed. Opening the target with {@link FileOutputStream} instead would truncate it first,
 * so a failed or interrupted write would destroy the previous content.</p>
 */
public final class AtomicFileWriter {
	public static final String TEMP_SUFFIX = ".tmp";

	public interface Content {
		void writeTo(OutputStream out) throws IOException;
	}

	public static void write(File target, final byte[] data) throws IOException {
		write(target, new Content() {
			@Override
			public void writeTo(OutputStream out) throws IOException {
				out.write(data);
			}
		});
	}

	public static void write(File target, Content content) throws IOException {
		File temp = new File(target.getPath() + TEMP_SUFFIX);
		boolean replaced = false;
		try {
			FileOutputStream out = new FileOutputStream(temp);
			try {
				content.writeTo(out);
				out.flush();
				out.getFD().sync();
			} finally {
				out.close();
			}
			// rename() replaces an existing target atomically on the file systems Android uses.
			if (!temp.renameTo(target)) throw new IOException("Cannot replace " + target);
			replaced = true;
		} finally {
			if (!replaced) temp.delete();
		}
	}
}
