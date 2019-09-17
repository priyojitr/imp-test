package com.tck.svnimporter.pvcsprovider.internal.model;

import java.util.Comparator;
import org.polarion.svnimporter.common.RevisionNumber;

public class PvcsRevisionComparator implements Comparator {
	public static final PvcsRevisionComparator INSTANCE = new PvcsRevisionComparator();

	public int compare(Object o1, Object o2) {
		if (o1 == o2) {
			return 0;
		} else {
			PvcsRevision r1 = (PvcsRevision) o1;
			PvcsRevision r2 = (PvcsRevision) o2;
			return RevisionNumber.compare(r1.getNumber(), r2.getNumber());
		}
	}
}