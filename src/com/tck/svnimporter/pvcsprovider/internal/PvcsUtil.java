package com.tck.svnimporter.pvcsprovider.internal;

import java.io.UnsupportedEncodingException;
import org.polarion.svnimporter.common.RevisionNumber;
import com.tck.svnimporter.pvcsprovider.PvcsException;

public class PvcsUtil {
	public static String getBranchNumber(String revisionNumber) {
		int[] n = RevisionNumber.parse(revisionNumber);
		return RevisionNumber.getSubNumber(n, 0, n.length - 1);
	}

	public static String getSproutRevisionNumber(String branchNumber) {
		int[] n = RevisionNumber.parse(branchNumber);
		return RevisionNumber.getSubNumber(n, 0, n.length - 1);
	}

	public static String toUtf8(String s) {
		try {
			return new String(s.getBytes("utf-8"));
		} catch (UnsupportedEncodingException var2) {
			throw new PvcsException(var2);
		}
	}
}