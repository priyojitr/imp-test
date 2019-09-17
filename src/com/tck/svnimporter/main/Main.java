package com.tck.svnimporter.main;

import java.io.PrintStream;
import java.util.Date;
import org.polarion.svnimporter.common.ISvnModel;
import org.polarion.svnimporter.common.Log;
import org.polarion.svnimporter.common.Playback;
import org.polarion.svnimporter.common.Timer;
import org.polarion.svnimporter.common.Util;
import com.tck.svnimporter.main.internal.MainConfig;
import org.polarion.svnimporter.svnprovider.SvnAdmin;
import org.polarion.svnimporter.svnprovider.SvnDump;
import org.polarion.svnimporter.svnprovider.SvnHistoryHelper;

public class Main {
	private static final Log LOG = Log.getLog(Main.class);
	private MainConfig config;

	public static void main(String[] args) {
		int status = 1;
		Main main = new Main();
		if (main.configure(args)) {
			status = main.run();
		}

		System.exit(status);
	}

	private boolean configure(String[] args) {
		this.config = new MainConfig();
		this.config.parseArgs(args);
		return this.config.validate();
	}

	private int run() {
		int status = 0;
		this.config.logEnvironmentInformation();
		this.startHistory();
		Timer timer = new Timer();
		timer.start();

		try {
			if (this.config.isOnlyListFiles()) {
				this.listFiles();
			} else {
				ISvnModel fullModel;
				if (this.config.isFullDump()) {
					fullModel = this.buildFullSvnModel();
					if (fullModel.isEmpty()) {
						this.recordHistory("empty svn model - nothing to import");
					} else {
						this.recordHistory("saving svn model to file...");
						SvnDump fullDump = new SvnDump(this.config.getFullDumpFilePattern(),
								this.config.getDateStringFormat(), this.config.getDumpFileSizeLimit());
						this.saveDump(fullModel, fullDump);
						if (this.config.isImportDump()) {
							this.importDump(fullDump);
						}

						SvnHistoryHelper.saveIncrHistory(fullModel, this.config.getIncrHistoryFile(),
								this.config.getLastRevisionDate());
					}
				} else if (this.config.isIncrementalDump()) {
					fullModel = this.buildFullSvnModel();
					if (fullModel.isEmpty()) {
						this.recordHistory("empty svn model - nothing to import");
					} else {
						this.recordHistory("creating incremental svn model...");
						ISvnModel incrementalModel = SvnHistoryHelper.createIncrModel(fullModel,
								this.config.getIncrHistoryFile(), this.config.getLastRevisionDate());
						if (incrementalModel.isEmpty()) {
							this.recordHistory("no changes detected for incremental dump");
						} else {
							SvnDump incrDump = new SvnDump(this.config.getIncrDumpFilePattern(),
									this.config.getDateStringFormat(), this.config.getDumpFileSizeLimit());
							this.recordHistory("saving incremental dump to file...");
							this.saveDump(incrementalModel, incrDump);
							if (this.config.isImportDump()) {
								this.importDump(incrDump);
							}

							this.recordHistory("saving incremental history to file...");
							SvnHistoryHelper.saveIncrHistory(fullModel, this.config.getIncrHistoryFile(),
									this.config.getLastRevisionDate());
						}
					}
				}
			}

			this.recordHistory("successfully finished");
		} catch (Throwable var6) {
			LOG.error("EXCEPTION CAUGHT: " + Util.getStackTrace(var6));
			status = 1;
		}

		if (!this.config.isDisableCleanup()) {
			this.config.getSrcProvider().cleanup();
			if (this.config.getSvnAdmin() != null) {
				this.config.getSvnAdmin().cleanup();
			}
		}

		Playback.getInstance().close();
		timer.stop();
		this.recordHistory("duration: " + timer.getDuration() + " seconds");
		return status;
	}

	private ISvnModel buildFullSvnModel() {
		this.recordHistory("creating full svn model...");
		return this.config.getSrcProvider().buildSvnModel();
	}

	private void listFiles() {
		LOG.info("List files to " + this.config.getListFilesTo());
		PrintStream out = Util.openPrintStream(this.config.getListFilesTo());
		if (out != null) {
			try {
				this.config.getSrcProvider().listFiles(out);
			} finally {
				out.close();
			}

		}
	}

	private void importDump(SvnDump dump) {
		if (this.checkSvnRepository()) {
			this.recordHistory("import dump into svn...");
			this.config.getSvnAdmin().importDump(dump);
			this.recordHistory("svnadmin import dump finished");
		} else {
			this.recordHistory("import aborted on repository check");
		}

	}

	private boolean checkSvnRepository() {
		SvnAdmin svnadmin = this.config.getSvnAdmin();
		this.recordHistory("check repository...");
		if (!svnadmin.isRepositoryExists()) {
			this.recordHistory("repository is not exist");
			if (this.config.isExistingSvnrepos()) {
				this.recordHistory("aborting import");
				return false;
			}

			this.recordHistory("creating new svn repository...");
			svnadmin.createRepository();
		} else {
			this.recordHistory("repository is exist");
		}

		this.recordHistory("check parent dir in repository...");
		if (!svnadmin.isParentDirExists()) {
			this.recordHistory("parent dir is not exist, creating...");
			svnadmin.createParentDir();
		} else {
			this.recordHistory("parent dir is exist");
			if (this.config.isFullDump() && this.config.isClearSvnParentDir()) {
				this.recordHistory("clear svn parent dir...");
				svnadmin.clearParentDir();
			}
		}

		return true;
	}

	private void saveDump(ISvnModel svnModel, SvnDump svnDump) {
		svnDump.dump(svnModel, this.config.getLastRevisionDate());
	}

	private void recordHistory(String line) {
		this.config.getHistoryLogger().info(line);
	}

	private void startHistory() {
		this.recordHistory("**********************************************************************");
		this.recordHistory("date: " + Util.toString(new Date()));
		this.recordHistory("mode: " + this.config.getMode());
		this.recordHistory("src provider: " + this.config.getSrcProvider().getClass());
	}
}