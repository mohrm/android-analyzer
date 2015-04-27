package edu.kit.jodroid.io;

import java.io.File;

public class AppSpec {
	public final File apkFile;
	public final File manifestFile;

	public AppSpec(File apkFile, File manifestFile) {
		this.apkFile = apkFile;
		this.manifestFile = manifestFile;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((apkFile == null) ? 0 : apkFile.hashCode());
		result = prime * result + ((manifestFile == null) ? 0 : manifestFile.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof AppSpec)) {
			return false;
		}
		AppSpec other = (AppSpec) obj;
		if (apkFile == null) {
			if (other.apkFile != null) {
				return false;
			}
		} else if (!apkFile.equals(other.apkFile)) {
			return false;
		}
		if (manifestFile == null) {
			if (other.manifestFile != null) {
				return false;
			}
		} else if (!manifestFile.equals(other.manifestFile)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return apkFile.getAbsolutePath();
	}
}