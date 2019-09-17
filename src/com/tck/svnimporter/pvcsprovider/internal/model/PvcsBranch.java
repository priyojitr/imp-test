package com.tck.svnimporter.pvcsprovider.internal.model;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import org.polarion.svnimporter.common.model.Branch;

public class PvcsBranch extends Branch {
	private SortedSet revisions;

	public PvcsBranch(String number) {
		super(number);
		this.revisions = new TreeSet(PvcsRevisionComparator.INSTANCE);
	}

	public SortedSet getRevisions() {
		return this.revisions;
	}

	public void resolveRevisionStates() {
		boolean first = true;
		Iterator i = this.getRevisions().iterator();

		while (i.hasNext()) {
			PvcsRevision revision = (PvcsRevision) i.next();
			if (first) {
				revision.setState(PvcsRevisionState.ADD);
				first = false;
			} else {
				revision.setState(PvcsRevisionState.CHANGE);
			}
		}

	}
}