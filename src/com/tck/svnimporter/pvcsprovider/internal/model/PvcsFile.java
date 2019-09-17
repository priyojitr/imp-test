package com.tck.svnimporter.pvcsprovider.internal.model;

import org.polarion.svnimporter.common.model.ModelFile;

public class PvcsFile extends ModelFile {
	private String pvcsPath;

	public PvcsFile(String path) {
		super(path);
	}

	public PvcsBranch getBranch(String branchNumber) {
		return (PvcsBranch) this.getBranches().get(branchNumber);
	}

	public PvcsRevision getRevision(String revisionNumber) {
		return (PvcsRevision) this.getRevisions().get(revisionNumber);
	}

	public String getPvcsPath() {
		return this.pvcsPath;
	}

	public void setPvcsPath(String pvcsPath) {
		this.pvcsPath = pvcsPath;
	}
}