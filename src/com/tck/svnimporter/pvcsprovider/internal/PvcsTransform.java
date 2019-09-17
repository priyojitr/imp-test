package com.tck.svnimporter.pvcsprovider.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.polarion.svnimporter.common.Log;
import org.polarion.svnimporter.common.model.Revision;
import com.tck.svnimporter.pvcsprovider.PvcsException;
import com.tck.svnimporter.pvcsprovider.PvcsProvider;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsBranch;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsCommit;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsModel;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsRevision;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsRevisionState;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsTag;
import org.polarion.svnimporter.svnprovider.SvnModel;
import org.polarion.svnimporter.svnprovider.internal.SvnProperties;

public class PvcsTransform {
	private static final Log LOG = Log.getLog(PvcsTransform.class);
	private static final String PVCS_REVISION_NUMBER = "PVCSRevisionNumber";
	private PvcsProvider provider;

	public PvcsTransform(PvcsProvider provider) {
		this.provider = provider;
	}

	public SvnModel transform(PvcsModel srcModel) {
		if (srcModel.getCommits().size() < 1) {
			return new SvnModel();
		} else {
			SvnModel svnModel = new SvnModel();
			svnModel.setSvnimporterUsername(this.provider.getConfig().getSvnimporterUsername());
			PvcsCommit firstCommit = (PvcsCommit) srcModel.getCommits().get(0);
			svnModel.createFirstRevision(firstCommit.getDate());
			svnModel.createTrunkPath(this.provider.getConfig().getTrunkPath());
			if (!this.isOnlyTrunk()) {
				svnModel.createBranchesPath(this.provider.getConfig().getBranchesPath());
				svnModel.createTagsPath(this.provider.getConfig().getTagsPath());
			}

			Iterator i = srcModel.getCommits().iterator();

			while (i.hasNext()) {
				this.transformCommit((PvcsCommit) i.next(), svnModel);
			}

			return svnModel;
		}
	}

	private boolean isOnlyTrunk() {
		return this.provider.getConfig().isOnlyTrunk();
	}

	private void transformCommit(PvcsCommit commit, SvnModel svnModel) {
		svnModel.createNewRevision(commit.getAuthor(), commit.getDate(), commit.getMessage());
		svnModel.getCurRevision().getProperties().set("PVCSRevisionNumbers", commit.joinRevisionNumbers());
		Map childBranches = new HashMap();
		Map childTags = new HashMap();
		Iterator i = commit.getRevisions().iterator();

		while (true) {
			PvcsRevision revision;
			Iterator j;
			do {
				if (!i.hasNext()) {
					if (!this.isOnlyTrunk()) {
						i = childBranches.keySet().iterator();

						String tagName;
						while (i.hasNext()) {
							tagName = (String) i.next();
							if (!svnModel.isBranchCreated(tagName)) {
								svnModel.createBranch(tagName, commit.getDate());
							}
						}

						if (!childTags.isEmpty()) {
							if (this.provider.getConfig().useFileCopy()) {
								int oldRevno = svnModel.getCurRevisionNumber();
								svnModel.createNewRevision(commit.getAuthor(), commit.getDate(),
										"svnimporter: adding tags to revision " + oldRevno);
								Iterator i1 = childTags.keySet().iterator();

								while (i1.hasNext()) {
									String tagName1 = (String) i1.next();
									if (!svnModel.isTagCreated(tagName1)) {
										svnModel.createTag(tagName1, commit.getDate());
									}

									Iterator j1 = ((Collection) childTags.get(tagName1)).iterator();

									while (j1.hasNext()) {
										PvcsRevision revision1 = (PvcsRevision) j1.next();
										svnModel.addFileCopyToTag(revision1.getPath(), tagName1,
												revision1.getBranch().getName(), revision1.getPath(), oldRevno);
									}
								}
							} else {
								i = childTags.keySet().iterator();

								while (i.hasNext()) {
									tagName = (String) i.next();
									if (!svnModel.isTagCreated(tagName)) {
										svnModel.createTag(tagName, commit.getDate());
									}

									j = ((Collection) childTags.get(tagName)).iterator();

									while (j.hasNext()) {
										PvcsRevision revision1 = (PvcsRevision) j.next();
										SvnProperties properties = new SvnProperties();
										properties.set("PVCSRevisionNumber", revision1.getNumber());
										svnModel.addFileToTag(revision1.getPath(), tagName,
												this.provider.createContentRetriever(revision1), properties);
									}
								}
							}
						}
					}

					return;
				}

				revision = (PvcsRevision) i.next();
				this.transformRevision(revision, svnModel);
			} while (this.isOnlyTrunk());

			PvcsBranch childBranch;
			for (j = revision.getChildBranches().iterator(); j
					.hasNext(); ((Collection) childBranches.get(childBranch.getName())).add(revision)) {
				childBranch = (PvcsBranch) j.next();
				if (!childBranches.containsKey(childBranch.getName())) {
					childBranches.put(childBranch.getName(), new ArrayList());
				}
			}

			PvcsTag childTag;
			for (j = revision.getTags().iterator(); j.hasNext(); ((Collection) childTags.get(childTag.getName()))
					.add(revision)) {
				childTag = (PvcsTag) j.next();
				if (!childTags.containsKey(childTag.getName())) {
					childTags.put(childTag.getName(), new ArrayList());
				}
			}
		}
	}

	private void transformRevision(PvcsRevision revision, SvnModel model) {
		String path = revision.getPath();
		String branchName = revision.getBranch().getBranchName();
		if (!this.isOnlyTrunk() || revision.getBranch().isTrunk()) {
			SvnProperties props;
			if (revision.getState() == PvcsRevisionState.ADD) {
				if (!revision.getBranch().isTrunk() && revision.isFirstRevision()
						&& this.provider.getConfig().useFileCopy()) {
					Revision sproutRevision = revision.getBranch().getSproutRevision();
					model.addFileCopyToBranch(path, branchName, sproutRevision.getBranch().getName(),
							sproutRevision.getPath(), sproutRevision.getSvnRevisionNumber(),
							this.provider.createContentRetriever(revision));
				} else {
					props = new SvnProperties();
					props.set("PVCSRevisionNumber", revision.getNumber());
					Map attrs = revision.getModelFile().getProperties();
					if (attrs.containsKey("EXPANDKEYWORDS")) {
						props.set("svn:keywords", "URL Author Revision Date Id");
					}

					String pvcsval;
					if (attrs.containsKey("NEWLINE")) {
						pvcsval = (String) attrs.get("NEWLINE");
						String svnval = null;
						if (pvcsval.equals("\\r\\n")) {
							svnval = "CRLF";
						} else if (pvcsval.equals("\\n")) {
							svnval = "LF";
						} else if (pvcsval.equals("\\r")) {
							svnval = "CR";
						}

						if (svnval != null) {
							props.set("svn:eol-style", svnval);
						}
					}

					pvcsval = this.provider.getConfig().getFileDescriptionPropKey();
					if (pvcsval != null && attrs.containsKey("description")) {
						props.set(pvcsval, PvcsUtil.toUtf8((String) attrs.get("description")));
					}

					model.addFile(path, branchName, this.provider.createContentRetriever(revision), props);
				}
			} else {
				if (revision.getState() != PvcsRevisionState.CHANGE) {
					LOG.error(revision.getModelFile().getPath());
					LOG.error(revision.getDebugInfo());
					LOG.error(revision.getBranch().getDebugInfo());
					throw new PvcsException("unknown Pvcs revision state: " + revision.getState());
				}

				props = new SvnProperties();
				props.set("PVCSRevisionNumber", revision.getNumber());
				model.changeFile(path, branchName, this.provider.createContentRetriever(revision), props);
			}

			revision.setSvnRevisionNumber(model.getCurRevisionNumber());
		}
	}
}