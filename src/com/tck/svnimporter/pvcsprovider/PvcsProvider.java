package com.tck.svnimporter.pvcsprovider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import org.polarion.svnimporter.common.IContentRetriever;
import org.polarion.svnimporter.common.IProvider;
import org.polarion.svnimporter.common.ISvnModel;
import org.polarion.svnimporter.common.Log;
import org.polarion.svnimporter.common.ProviderConfig;
import org.polarion.svnimporter.common.Util;
import org.polarion.svnimporter.common.ZeroContentRetriever;
import com.tck.svnimporter.pvcsprovider.internal.PvcsConfig;
import com.tck.svnimporter.pvcsprovider.internal.PvcsContentRetriever;
import com.tck.svnimporter.pvcsprovider.internal.PvcsExec;
import com.tck.svnimporter.pvcsprovider.internal.PvcsTransform;
import com.tck.svnimporter.pvcsprovider.internal.VlogParser;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsFile;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsModel;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsRevision;
import org.polarion.svnimporter.svnprovider.SvnModel;

public class PvcsProvider implements IProvider {
	private static final Log LOG = Log.getLog(PvcsProvider.class);
	private PvcsConfig config;
	private File m_InstructionsFile;
	private File m_InstructionsFileChecksum;
	private Map m_File2rev2path;
	private Map m_File2rev2pathChecksum;
	private boolean testMode = false;

	public void setTestMode(boolean testMode) {
		this.testMode = testMode;
	}

	public void configure(Properties properties) {
		this.config = new PvcsConfig(properties);
		this.m_InstructionsFile = new File(this.config.getTempDir(), "instr.tmp");
		this.m_InstructionsFileChecksum = new File(this.config.getTempDir(), "instrChecksum.tmp");
	}

	public boolean validateConfig() {
		return this.config.validate();
	}

	public ProviderConfig getConfig() {
		return this.config;
	}

	public void logEnvironmentInformation() {
		this.config.logEnvironmentInformation();
	}

	private PvcsModel buildPvcsModel() {
		File vlogFile = this.getVlogFile();
		File filesFile = this.getFilesFile();
		if (!this.config.keepVlogFile() || !vlogFile.exists() || !filesFile.exists()) {
			this.getLogInformation(filesFile, vlogFile);
		}

		VlogParser parser = new VlogParser(this.config);
		parser.parse(filesFile, vlogFile);
		PvcsModel pvcsModel = new PvcsModel();
		Iterator var6 = parser.getFiles().values().iterator();

		while (var6.hasNext()) {
			PvcsFile pvcsFile = (PvcsFile) var6.next();
			pvcsModel.addFile(pvcsFile);
		}

		pvcsModel.finishModel();
		LOG.info("PVCS model has been created.");
		pvcsModel.printSummary();
		return pvcsModel;
	}

	protected File getFilesFile() {
		return new File(this.config.getTempDir(), "files.tmp");
	}

	protected File getVlogFile() {
		return new File(this.config.getTempDir(), "vlog.tmp");
	}

	public void listFiles(PrintStream out) {
		Collection files = this.buildPvcsModel().getFiles().keySet();
		Iterator i = files.iterator();

		while (i.hasNext()) {
			out.println(i.next());
		}

	}

	public ISvnModel buildSvnModel() {
		PvcsModel pvcsModel = this.buildPvcsModel();
		PvcsTransform transform = new PvcsTransform(this);
		SvnModel svnModel = transform.transform(pvcsModel);
		LOG.info("Svn model has been created");
		LOG.info("total number of revisions in svn model: " + svnModel.getRevisions().size());
		if (!this.testMode) {
			this.m_File2rev2path = this.getAllContents(pvcsModel, this.config.getTempDir(), this.m_InstructionsFile);
			if (this.config.isValidateCheckouts()) {
				this.m_File2rev2pathChecksum = this.getAllContents(pvcsModel, this.config.getCheckoutTempDir(),
						this.m_InstructionsFileChecksum);
			}
		}

		return svnModel;
	}

	protected void getLogInformation(File targetFilesFile, File targetVlogFile) {
		String subproject = this.config.getSubproject();
		if (subproject == null) {
			subproject = "";
		}

		if (!subproject.startsWith("/")) {
			subproject = "/" + subproject;
		}

		this.executeCommand(new String[]{this.config.getExecutable(), "run",
				"->" + this.getPvcsPath(targetFilesFile.getAbsolutePath()), "-q", "listversionedfiles",
				"-pr" + this.getPvcsPath(this.config.getProjectPath()), this.getLogonString(false), "-l", "-z",
				this.getPvcsPath(subproject)});
		this.executeCommand(new String[]{this.config.getExecutable(), "run",
				"->" + this.getPvcsPath(targetVlogFile.getAbsolutePath()), "-q", "vlog",
				"-pr" + this.getPvcsPath(this.config.getProjectPath()), this.getLogonString(false), "-z",
				this.getPvcsPath(subproject)});
	}

	public IContentRetriever createContentRetriever(PvcsRevision revision) {
		return (IContentRetriever) (this.config.isUseOnlyLastRevisionContent() && !revision.isLastRevision()
				? ZeroContentRetriever.INSTANCE
				: new PvcsContentRetriever(this, revision));
	}

	private String getLogonString(boolean useQuotes) {
		String userId = this.config.getUserName();
		if (userId == null) {
			return "";
		} else {
			if (this.config.getPassword() != null) {
				userId = userId + ":" + this.config.getPassword();
			}

			String quotes = useQuotes ? "\"" : "";
			return "-id" + quotes + userId + quotes;
		}
	}

	private Map getAllContents(PvcsModel model, File tempDir, File instructionsFile) {
		LOG.debug("get all contents for " + tempDir.getAbsolutePath());
		HashMap f2r2p = new HashMap();

		try {
			PrintWriter out = new PrintWriter(new FileOutputStream(instructionsFile));
			out.println("set -vDOLLAR '$'");

			try {
				model.getFiles();
				Iterator i = model.getFiles().values().iterator();

				while (i.hasNext()) {
					PvcsFile file = (PvcsFile) i.next();
					Map rev2path = new HashMap();
					f2r2p.put(file, rev2path);
					Iterator j = file.getRevisions().values().iterator();

					while (j.hasNext()) {
						PvcsRevision revision = (PvcsRevision) j.next();
						String revNum = revision.getNumber();
						File localFile = this.getLocalFile(revision, tempDir);
						rev2path.put(revision.getNumber(), localFile);
						String pvcsPath = ((PvcsFile) revision.getModelFile()).getPvcsPath();
						if (!pvcsPath.startsWith("/")) {
							pvcsPath = "/" + pvcsPath;
						}

						out.print("run get ");
						out.print("-pr\"'" + this.getPvcsPath(this.config.getProjectPath(), true) + "'\" ");
						out.print("-a\"" + this.getPvcsPath(localFile.getAbsolutePath(), true) + "\" ");
						out.print("-r\"" + revNum + "\" ");
						out.print(this.getLogonString(true) + " ");
						out.print("\"" + this.getPvcsPath(pvcsPath, true) + "\"");
						out.println();
						localFile.getParentFile().mkdirs();
					}
				}
			} finally {
				out.close();
			}

			this.executeCommand(
					new String[]{this.config.getExecutable(), "run", "-s" + instructionsFile.getAbsolutePath()});
			return f2r2p;
		} catch (FileNotFoundException var18) {
			throw new PvcsException(var18);
		}
	}

	public File checkout(PvcsRevision revision) throws IOException {
		File alreadyReceivedFile = this.getFileFromMap(revision, this.m_File2rev2path, "PvcsProvider.checkout():");
		if (alreadyReceivedFile == null) {
			return null;
		} else {
			LOG.info("  PvcsProvider.checkout() => : " + alreadyReceivedFile);
			if (this.config.isValidateCheckouts()) {
				LOG.info("Validating checkout...");
				String firstChecksum = Util.md5checksum(alreadyReceivedFile);
				File checksumFile = this.getFileFromMap(revision, this.m_File2rev2pathChecksum,
						"PvcsProvider.checkoutChecksum():");
				if (checksumFile == null) {
					return null;
				}

				String secondChecksum = Util.md5checksum(checksumFile);
				if (!secondChecksum.equals(firstChecksum)) {
					LOG.error("The checksums of file the " + alreadyReceivedFile + " revision " + revision.getNumber()
							+ " doesn't match !!! [" + firstChecksum + "] != [" + secondChecksum + "]");
					return null;
				}

				LOG.info("chechkSum validation successfully completed");
			}

			return alreadyReceivedFile;
		}
	}

	private File getFileFromMap(PvcsRevision revision, Map map, String logMessage) {
		Map rev2path = (Map) map.get(revision.getModelFile());
		if (rev2path == null) {
			LOG.error(logMessage + "rev2path == null => getContent - Problem !");
			return null;
		} else {
			File mapFile = (File) rev2path.get(revision.getNumber());
			if (mapFile == null) {
				LOG.error(logMessage + "File not found");
				return null;
			} else if (!mapFile.exists()) {
				LOG.error(logMessage + "File " + mapFile.getAbsolutePath() + " doesn't exist.");
				return null;
			} else if (!mapFile.isFile()) {
				LOG.error(logMessage + "File " + mapFile.getAbsolutePath() + " is not a file.");
				return null;
			} else {
				return mapFile;
			}
		}
	}

	private boolean checkout(File file, String revisionNumber, String pvcsPath) {
		this.executeCommand(new String[]{this.config.getExecutable(), "get",
				"-pr" + this.getPvcsPath(this.config.getProjectPath()), "-a" + this.getPvcsPath(file.getAbsolutePath()),
				"-r" + revisionNumber, this.getLogonString(false), this.getPvcsPath(pvcsPath)});
		return file.exists();
	}

	private String getPvcsPath(String path) {
		return this.getPvcsPath(path, false);
	}

	private String getPvcsPath(String path, boolean replaceDollarByVariable) {
		StringBuffer buf = new StringBuffer();

		for (int i = 0; i < path.length(); ++i) {
			char c = path.charAt(i);
			switch (c) {
				case '"' :
					buf.append("\\\"");
					break;
				case '$' :
					if (replaceDollarByVariable) {
						buf.append("'$DOLLAR'");
					} else {
						buf.append("'$'");
					}
					break;
				case '\'' :
					buf.append("\\'");
					break;
				case '\\' :
					buf.append('/');
					break;
				default :
					buf.append(c);
			}
		}

		return buf.toString();
	}

	private File getLocalFile(PvcsRevision pvcsRevision, File tempDir) {
		File localFilePr = new File(tempDir, pvcsRevision.getPath());
		String revNum = pvcsRevision.getNumber();
		return new File(localFilePr.getAbsolutePath() + "_rev" + revNum.replaceAll("[^0-9]", "_"));
	}

	private void executeCommand(String[] cmd) {
		PvcsExec exec = new PvcsExec(cmd);
		exec.setWorkdir(this.config.getTempDir());
		exec.setVerboseExec(this.config.isVerboseExec());
		exec.exec();
		if (exec.getErrorCode() != 0) {
			if (exec.getErrorCode() == 1) {
				throw new PvcsException(
						"error during execution command " + Util.toString(exec.getCmd(), " ") + ", exception caught",
						exec.getException());
			} else if (exec.getErrorCode() == 401) {
				throw new PvcsException("error during execution command " + Util.toString(exec.getCmd(), " ")
						+ ": wrong project path \"" + this.config.getProjectPath() + "\"");
			} else {
				throw new PvcsException("error during execution command " + Util.toString(exec.getCmd(), " "));
			}
		} else if (exec.getRc() != 0) {
			throw new PvcsException("Process exit code: " + exec.getRc());
		}
	}

	public void cleanup() {
		LOG.debug("cleanup");
		File tempDir = this.config.getTempDir();
		if (!Util.delete(tempDir)) {
			LOG.error("can't delete temp dir: " + tempDir.getAbsolutePath());
		}

		if (this.config.isValidateCheckouts()) {
			File tempDirCheckOut = this.config.getCheckoutTempDir();
			if (!Util.delete(tempDirCheckOut)) {
				LOG.error("can't delete temp dir: " + tempDirCheckOut.getAbsolutePath());
			}
		}

	}
}