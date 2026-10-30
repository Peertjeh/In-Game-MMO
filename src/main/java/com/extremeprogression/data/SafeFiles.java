package com.extremeprogression.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

import com.extremeprogression.ExtremeProgression;

/** Crash-safe file writes with rotating backups, and reads that fall back to those backups. */
public final class SafeFiles {
	private SafeFiles() {}

	/** The n-th backup of a file: {@code .bak1} is the newest. */
	public static Path backup(Path path, int n) {
		return path.resolveSibling(path.getFileName() + ".bak" + n);
	}

	/**
	 * Writes a file crash-safe: the new text goes to a temporary file that replaces the old one in a single step. Up to
	 * {@code backups} earlier versions stay beside it. A crash at any moment leaves the old or the new file whole.
	 */
	public static void writeAtomically(Path path, String text, int backups) throws IOException {
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		Files.createDirectories(path.toAbsolutePath().getParent());
		Files.writeString(tmp, text, StandardCharsets.UTF_8);
		if (backups > 0 && Files.exists(path)) {
			Files.deleteIfExists(backup(path, backups));
			for (int i = backups - 1; i >= 1; i--) {
				Path from = backup(path, i);
				if (Files.exists(from)) {
					Files.move(from, backup(path, i + 1), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			Files.copy(path, backup(path, 1), StandardCopyOption.REPLACE_EXISTING);
		}
		try {
			Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Reads a file through {@code parse}. When it is missing or cannot be read (a crash, a bad hand edit), the newest
	 * backup that can be read takes over. A file that cannot be read is never overwritten: it is moved aside as
	 * {@code .unreadable-<time>} for the admin. Null when nothing could be read.
	 */
	public static <T> T readWithFallback(Path path, int backups, Function<String, T> parse, String what) {
		T loaded = read(path, parse, what);
		if (loaded != null) {
			return loaded;
		}
		if (Files.exists(path)) {
			Path aside = path.resolveSibling(path.getFileName() + ".unreadable-" + System.currentTimeMillis());
			try {
				Files.move(path, aside);
				ExtremeProgression.LOGGER.error("A {} could not be read; kept it as {}", what, aside.getFileName());
			} catch (IOException e) {
				ExtremeProgression.LOGGER.error("A {} could not be read or moved aside: {}", what, path, e);
			}
		}
		for (int i = 1; i <= backups; i++) {
			T restored = read(backup(path, i), parse, what);
			if (restored != null) {
				ExtremeProgression.LOGGER.warn("Restored a {} from {}", what, backup(path, i).getFileName());
				return restored;
			}
		}
		return null;
	}

	private static <T> T read(Path path, Function<String, T> parse, String what) {
		if (!Files.exists(path)) {
			return null;
		}
		try {
			return parse.apply(Files.readString(path, StandardCharsets.UTF_8));
		} catch (Exception e) {
			ExtremeProgression.LOGGER.error("Could not read {} {}", what, path.getFileName(), e);
			return null;
		}
	}
}
