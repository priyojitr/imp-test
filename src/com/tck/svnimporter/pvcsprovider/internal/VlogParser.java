package com.tck.svnimporter.pvcsprovider.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.polarion.svnimporter.common.Log;
import org.polarion.svnimporter.common.Util;
import com.tck.svnimporter.pvcsprovider.PvcsException;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsBranch;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsFile;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsRevision;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsTag;

public class VlogParser {
	private static final Log LOG = Log.getLog(VlogParser.class);
	private PvcsConfig config;
	private Map<String, PvcsFile> files;
	private BufferedReader filesReader;
	private BufferedReader vlogReader;

	public VlogParser(PvcsConfig config) {
		this.config = config;
	}

	public void parse(File filesFile, File vlogfile) {
		this.files = new HashMap();

		try {
			InputStreamReader encReader = new InputStreamReader(new FileInputStream(vlogfile),
					this.config.getLogEncoding());
			this.vlogReader = new BufferedReader(encReader);
			this.filesReader = new BufferedReader(
					new InputStreamReader(new FileInputStream(filesFile), this.config.getLogEncoding()));

			PvcsFile file;
			try {
				while ((file = this.parseFile()) != null) {
					this.files.put(file.getPath(), file);
				}
			} finally {
				this.vlogReader.close();
				this.filesReader.close();
			}

		} catch (IOException var9) {
			throw new PvcsException(var9);
		}
	}

	private PvcsFile parseFile() throws IOException {
		String line;
		do {
			line = this.vlogReader.readLine();
			if (line == null) {
				return null;
			}
		} while (line.trim().length() == 0);

		if (!line.startsWith("Archive:")) {
			throw new PvcsException("VlogParser expected Archive: but found " + line);
		} else {
			line = line.substring(8).trim();
			String workfile = this.vlogReader.readLine();
			if (workfile != null && workfile.startsWith("Workfile:")) {
				workfile = workfile.substring(9).trim();
				String pvcsPath = null;

				do {
					pvcsPath = this.filesReader.readLine();
					if (pvcsPath == null) {
						throw new PvcsException("no PVCS path in \"files.tmp\" for archive \"" + line + "\"");
					}

					if (!pvcsPath.toLowerCase().endsWith(workfile.toLowerCase())) {
						LOG.error("Vlog workfile does not correspond to the files entry. Skipping the files entry "
								+ pvcsPath + "\nSearch vlog for error messages!");
						pvcsPath = null;
					}
				} while (pvcsPath == null);

				String path = this.getPath(pvcsPath);
				PvcsFile curFile = new PvcsFile(path);
				curFile.setPvcsPath(pvcsPath);
				PvcsBranch trunk = new PvcsBranch("1");
				trunk.setTrunk(true);
				curFile.addBranch(trunk);
				Map<String, String> versionLabels = new HashMap();
				line = this.vlogReader.readLine();
				if (line == null) {
					throw new PvcsException("Premature end of vlogfile");
				} else {
					boolean done = false;
					boolean revDone = false;

					String branchNumber;
					String sproutRevNumber;
					int st;
					String label;
					String revNumber;
					do {
						if (line.startsWith("Attributes:")) {
							while (true) {
								line = this.vlogReader.readLine();
								if (line == null) {
									throw new PvcsException("Premature end of vlogfile");
								}

								if (!line.startsWith(" ")) {
									break;
								}

								if (this.config.importAttributes()) {
									line = line.trim();
									revNumber = null;
									int sep = line.indexOf(61);
									if (sep < 0) {
										label = line.trim();
									} else {
										label = line.substring(0, sep).trim();
										int start = this.next(line, sep, '"') + 1;
										int end = this.next(line, start, '"');
										revNumber = line.substring(start, end);
									}

									curFile.addProperty(label, revNumber);
								}
							}
						} else if (line.startsWith("Version labels:")) {
							while (true) {
								line = this.vlogReader.readLine();
								if (line == null) {
									throw new PvcsException("Premature end of vlogfile");
								}

								if (!line.startsWith(" ")) {
									break;
								}

								line = line.trim();
								int start = 1;
								int end = this.next(line, start, '"');
								branchNumber = Util.cleanLabel(line.substring(start, end));
								st = end + 4;
								sproutRevNumber = line.substring(st);
								if (!sproutRevNumber.endsWith("*")) {
									versionLabels.put(branchNumber, sproutRevNumber);
								}
							}
						} else if (line.startsWith("Description:")) {
							StringBuilder sb = new StringBuilder();

							while (true) {
								line = this.vlogReader.readLine();
								if (line == null) {
									throw new PvcsException("Premature end of vlogfile");
								}

								if (line.startsWith("===================================")) {
									revDone = true;
								}

								if (revDone || line.startsWith("-----------------------------------")) {
									if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
										sb.setLength(sb.length() - 1);
									}

									curFile.addProperty("description", sb.toString());
									done = true;
									break;
								}

								if (sb.length() > 0) {
									sb.append('\n');
								}

								sb.append(line);
							}
						} else {
							line = this.vlogReader.readLine();
							if (line == null) {
								throw new PvcsException("Premature end of vlogfile");
							}
						}
					} while (!done);

					PvcsRevision curRevision;
					StringBuilder sb;
					label179 : for (done = false; !revDone; curRevision.setMessage(sb.toString())) {
						line = this.vlogReader.readLine();
						if (line == null) {
							throw new PvcsException("Premature end of vlogfile");
						}

						for (st = 0; st < line.length() && Character.isWhitespace(line.charAt(st)); ++st) {
							;
						}

						line = line.substring(st);
						if (!line.startsWith("Rev ")) {
							throw new PvcsException("Expected revision but found: " + line);
						}

						revNumber = line.substring(4).trim();
						branchNumber = PvcsUtil.getBranchNumber(revNumber);
						if (branchNumber.indexOf(".") == -1) {
							branchNumber = "1";
						}

						curRevision = new PvcsRevision(revNumber);
						PvcsBranch branch = curFile.getBranch(branchNumber);
						if (branch == null) {
							branch = new PvcsBranch(branchNumber);
							branch.setName(branchNumber);
							curFile.addBranch(branch);
						}

						curRevision.setBranch(branch);
						branch.addRevision(curRevision);
						curFile.addRevision(curRevision);

						while (true) {
							line = this.vlogReader.readLine();
							if (line == null) {
								throw new PvcsException("Premature end of vlogfile");
							}

							line = line.substring(st);
							if (line.startsWith("Checked in:")) {
								line = line.substring(11).trim();
								Date date = this.parseDate(line);
								if (date != null) {
									curRevision.setDate(date);
								}
							} else if (line.startsWith("Author id:")) {
								line = line.substring(10).trim();
								int pt = line.indexOf(32);
								if (pt >= 0) {
									line = line.substring(0, pt);
								}

								curRevision.setAuthor(line);
								sb = new StringBuilder();

								while (true) {
									line = this.vlogReader.readLine();
									if (line == null) {
										throw new PvcsException("Premature end of vlogfile");
									}

									if (line.startsWith("===================================")) {
										revDone = true;
									}

									if (revDone || line.startsWith("-----------------------------------")) {
										continue label179;
									}

									line = line.substring(st);
									if (!line.startsWith("Branches: ")) {
										if (sb.length() > 0) {
											sb.append("\n");
										}

										sb.append(line);
									}
								}
							}
						}
					}

					Iterator var21 = curFile.getBranches().values().iterator();

					while (var21.hasNext()) {
						PvcsBranch branch = (PvcsBranch) var21.next();
						if (!branch.isTrunk()) {
							branchNumber = branch.getNumber();
							sproutRevNumber = PvcsUtil.getSproutRevisionNumber(branchNumber);
							PvcsRevision sproutRevision = curFile.getRevision(sproutRevNumber);
							if (sproutRevision == null) {
								LOG.error(curFile.getPath() + ": unknown sprout revision for branch " + branchNumber);
							} else {
								branch.setSproutRevision(sproutRevision);
								sproutRevision.addChildBranch(branch);
							}
						}
					}

					var21 = versionLabels.keySet().iterator();

					while (var21.hasNext()) {
						label = (String) var21.next();
						branchNumber = (String) versionLabels.get(label);
						curRevision = curFile.getRevision(branchNumber);
						if (curRevision == null) {
							LOG.warn("Labeled revision " + branchNumber + " not found, label=" + label + ")");
						} else {
							curRevision.addTag(new PvcsTag(label));
						}
					}

					curFile.checkSequenceDates();
					return curFile;
				}
			} else {
				throw new PvcsException("Workfile: line should follow Archive: line in vlog (Archive: " + line + ")");
			}
		}
	}

	private String getPath(String line) {
		String filename = line;
		if (line.startsWith("/")) {
			filename = line.substring(1);
		}

		if (this.config.getSubproject() != null) {
			if (!filename.toLowerCase().startsWith(this.config.getSubproject().toLowerCase())) {
				throw new PvcsException(
						"filename " + filename + " must start with subproject name: " + this.config.getSubproject());
			}

			filename = filename.substring(this.config.getSubproject().length());
		}

		if (filename.startsWith("/")) {
			filename = filename.substring(1);
		}

		return filename;
	}

	private Date parseDate(String sdate) {
		DateFormat df = this.config.getLogDateFormat();

		try {
			return df.parse(sdate);
		} catch (ParseException var4) {
			LOG.error("wrong date: " + sdate + "(" + "sample format: " + df.format(new Date()) + ")");
			return null;
		}
	}

	private int next(String s, int startIndex, char c) {
		int i;
		for (i = startIndex; i < s.length() && s.charAt(i) != c; ++i) {
			;
		}

		return i;
	}

	public Map<String, PvcsFile> getFiles() {
		return this.files;
	}
}