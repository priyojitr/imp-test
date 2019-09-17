package com.tck.svnimporter.pvcsprovider.internal.model;

import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;
import org.polarion.svnimporter.common.Log;
import org.polarion.svnimporter.common.model.CommitsCollection;
import org.polarion.svnimporter.common.model.Model;

public class PvcsModel extends Model {
	private static final Log LOG = Log.getLog(PvcsModel.class);

	public void finishModel() {
		Collection files = this.getFiles().values();
		Iterator i = files.iterator();

		while (i.hasNext()) {
			PvcsFile file = (PvcsFile) i.next();
			Collection branches = file.getBranches().values();
			Iterator j = branches.iterator();

			while (j.hasNext()) {
				PvcsBranch branch = (PvcsBranch) j.next();
				branch.resolveRevisionStates();
			}
		}

		this.separateCommits();
	}

	private int compareRevNumbers(String rev1, String rev2) {
		StringTokenizer rev1Tokenizer = new StringTokenizer(rev1, ".");
		StringTokenizer rev2Tokenizer = new StringTokenizer(rev2, ".");
		Vector rev1Tokens = new Vector();
		Vector rev2Tokens = new Vector();

		while (rev1Tokenizer.hasMoreTokens()) {
			rev1Tokens.add(rev1Tokenizer.nextToken());
		}

		while (rev2Tokenizer.hasMoreTokens()) {
			rev2Tokens.add(rev2Tokenizer.nextToken());
		}

		for (int index = 0; index < rev1Tokens.size() || index < rev2Tokens.size(); ++index) {
			if (rev1Tokens.size() <= index) {
				return -1;
			}

			if (rev2Tokens.size() <= index) {
				return 1;
			}

			int revInt1 = Integer.parseInt((String) rev1Tokens.elementAt(index));
			int revInt2 = Integer.parseInt((String) rev2Tokens.elementAt(index));
			if (revInt1 != revInt2) {
				if (revInt1 > revInt2) {
					return 1;
				}

				return -1;
			}
		}

		return 0;
	}

	private void separateCommits() {
		CommitsCollection c = new CommitsCollection(PvcsCommit.class);
		c.addFiles(this.getFiles().values());
		this.getCommits().clear();
		this.getCommits().addAll(c.separateCommits());
	}
}