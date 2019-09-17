package com.tck.svnimporter.main.internal;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import org.polarion.svnimporter.common.ConfigUtil;
import org.polarion.svnimporter.common.IProvider;
import org.polarion.svnimporter.common.Log;
import org.polarion.svnimporter.common.Util;
import org.polarion.svnimporter.svnprovider.SvnAdmin;

public class MainConfig {
	private static final Log LOG = Log.getLog(MainConfig.class);
	private static final String SVNIMPORTER_VERSION = "svnimporter 1.1-SHAPSHOT (after M8)";
	private static final String ONLY_LIST_MODE = "list";
	private static final String FULL_DUMP_MODE = "full";
	private static final String INCREMENTAL_DUMP_MODE = "incremental";
	private static final String HELP_MODE = "help";
	private static final String VERSION_MODE = "version";
	private static final String FULL_DUMP_FILE_KEY = "full.dump.file";
	private static final String INCR_DUMP_FILE_KEY = "incr.dump.file";
	private static final String INCR_HISTORY_FILE_KEY = "incr.history.file";
	private static final String LIST_FILES_TO_KEY = "list.files.to";
	private static final String SRC_PROVIDER_KEY = "srcprovider";
	private static final String DUMPFILE_SIZE_LIMIT = "dump.file.sizelimit.mb";
	private static final String USAGE = "usage: command config_file [lastdate]\n\nCommands:\n\tlist - dont create dump, only list files\n\tfull - create full dump [revisions up to lastdate]\n\tincremental - create incremental dump [revisions up to lastdate]\n\thelp - show help\n\tversion - show version";
	private static final String AUTOPROPS_FILE_KEY = "config.autoprops";
	private String fullDumpFilePattern;
	private String incrlDumpFilePattern;
	private String incrHistoryFile;
	private String autopropsFile;
	private boolean fullDump;
	private boolean incrementalDump;
	private boolean onlyListFiles;
	private String listFilesTo;
	private boolean importDump;
	private boolean existingSvnrepos;
	private boolean disableCleanup;
	private IProvider srcProvider;
	private boolean clearSvnParentDir;
	private SvnAdmin svnAdmin;
	private Log historyLogger;
	private Properties srcProperties;
	private Date lastRevDate;
	private int dumpFileSizeLimit;
	private boolean hasErrors = false;
	private final DateFormat dateStringFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
	private String dateString;

	public void parseArgs(String[] args) {
		if (args.length < 1) {
			this.usage("You must specify command");
		} else {
			String firstArg = args[0];
			if ("list".equals(firstArg)) {
				this.onlyListFiles = true;
			} else if ("full".equals(firstArg)) {
				this.fullDump = true;
			} else {
				if (!"incremental".equals(firstArg)) {
					if ("help".equals(firstArg)) {
						this.usage((String) null);
						return;
					}

					if ("version".equals(firstArg)) {
						this.showVersion();
						return;
					}

					this.usage("Unknown command \"" + firstArg + "\"");
					return;
				}

				this.incrementalDump = true;
			}

			if (args.length < 2) {
				this.usage("You must specify config file as second parameter");
			} else if (args.length <= 3) {
				String configFile = args[1];

				try {
					this.srcProperties = Util.loadProperties(configFile);
					Log.configure(this.srcProperties);
					this.historyLogger = Log.getLog("historyLogger");
					this.parseConfig();
				} catch (IOException var5) {
					this.error("can't open config file \"" + configFile + "\"", var5);
				}

				if (args.length == 3) {
					SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss");
					this.lastRevDate = fmt.parse(args[2], new ParsePosition(0));
					if (this.lastRevDate == null) {
						this.usage("ERROR: Cannot convert " + args[2]
								+ " to date. Expected format: \"yyyy-MM-dd'T'kk:mm:ss\"");
						return;
					}
				}

			} else {
				StringBuffer b = new StringBuffer();
				b.append("unknown command line parameters: ");

				for (int i = 2; i < args.length; ++i) {
					b.append(args[i] + " ");
				}

				this.usage(b.toString());
			}
		}
	}

	public boolean validate() {
		return !this.hasErrors;
	}

	private void error(String message, Exception e) {
		LOG.error(message, e);
		this.hasErrors = true;
	}

	private void error(String message) {
		LOG.error(message);
		this.hasErrors = true;
	}

	private void usage(String error) {
		if (error != null) {
			System.err.println(error + "\n");
		}

		System.err.println(
				"usage: command config_file [lastdate]\n\nCommands:\n\tlist - dont create dump, only list files\n\tfull - create full dump [revisions up to lastdate]\n\tincremental - create incremental dump [revisions up to lastdate]\n\thelp - show help\n\tversion - show version");
		this.hasErrors = true;
	}

	private void showVersion() {
		System.out.println("svnimporter 1.1-SHAPSHOT (after M8)");
		this.hasErrors = true;
	}

	private String getDateString() {
		if (this.dateString == null) {
			this.dateString = this.dateStringFormat.format(new Date());
		}

		return this.dateString;
	}

	public DateFormat getDateStringFormat() {
		return this.dateStringFormat;
	}

	private void setIncrHistoryFile() {
		String s = this.getStringProperty("incr.history.file");
		if (s != null) {
			this.incrHistoryFile = s;
		}
	}

	private void setListFilesTo() {
		String s = this.getStringProperty("list.files.to");
		if (s != null) {
			this.listFilesTo = this.insertDate(s);
		}
	}

	private void setSrcProvider() {
		String providerName = this.getStringProperty("srcprovider");
		if (providerName != null) {
			String providerClass = this.getStringProperty(providerName + ".class");
			if (providerClass != null) {
				try {
					Class c = Class.forName(providerClass);
					Object o = c.newInstance();
					if (!(o instanceof IProvider)) {
						this.error("class \"" + providerClass + "\" does not implement IProvider interface");
						return;
					}

					this.srcProvider = (IProvider) o;
					if (!this.isFullDump()) {
						ConfigUtil.setBooleanProperty(this.srcProperties, "useOnlyLastRevisionContent", false);
					}

					this.srcProvider.configure(this.srcProperties);
					if (!this.srcProvider.validateConfig()) {
						this.hasErrors = true;
					}
				} catch (Exception var5) {
					this.error("can't create new instance of provider class \"" + providerClass + "\"", var5);
				}

			}
		}
	}

	private void parseConfig() {
		this.fullDumpFilePattern = this.getStringProperty("full.dump.file");
		this.incrlDumpFilePattern = this.getStringProperty("incr.dump.file");
		this.autopropsFile = this.getStringProperty("config.autoprops", false);
		this.setIncrHistoryFile();
		this.setSrcProvider();
		this.setListFilesTo();
		this.disableCleanup = this.getBooleanProperty("disable_cleanup");
		this.importDump = this.getBooleanProperty("import_dump_into_svn");
		this.existingSvnrepos = this.getBooleanProperty("existing_svnrepos");
		this.clearSvnParentDir = this.getBooleanProperty("clear_svn_parent_dir");
		this.dumpFileSizeLimit = this.getIntProperty("dump.file.sizelimit.mb");
		if (this.dumpFileSizeLimit < 0) {
			this.error("configuration property value \"dump.file.sizelimit.mb\" may not be negative.");
		}

		if (this.importDump && (this.isFullDump() || this.isIncrementalDump())) {
			this.svnAdmin = new SvnAdmin();
			this.svnAdmin.configure(this.srcProperties);
			if (!this.svnAdmin.validateConfig()) {
				this.hasErrors = true;
			}
		}

	}

	public String getFullDumpFilePattern() {
		return this.fullDumpFilePattern;
	}

	public String getIncrDumpFilePattern() {
		return this.incrlDumpFilePattern;
	}

	public String getIncrHistoryFile() {
		return this.incrHistoryFile;
	}

	public String getAutopropsFile() {
		return this.autopropsFile;
	}

	public boolean isFullDump() {
		return this.fullDump;
	}

	public boolean isIncrementalDump() {
		return this.incrementalDump;
	}

	public boolean isOnlyListFiles() {
		return this.onlyListFiles;
	}

	public String getListFilesTo() {
		return this.listFilesTo;
	}

	public boolean isImportDump() {
		return this.importDump;
	}

	public boolean isExistingSvnrepos() {
		return this.existingSvnrepos;
	}

	public boolean isDisableCleanup() {
		return this.disableCleanup;
	}

	public boolean isClearSvnParentDir() {
		return this.clearSvnParentDir;
	}

	public Log getHistoryLogger() {
		return this.historyLogger;
	}

	public IProvider getSrcProvider() {
		return this.srcProvider;
	}

	public SvnAdmin getSvnAdmin() {
		return this.svnAdmin;
	}

	public Date getLastRevisionDate() {
		return this.lastRevDate;
	}

	public int getDumpFileSizeLimit() {
		return this.dumpFileSizeLimit;
	}

	public void logEnvironmentInformation() {
		LOG.info("****************************************************************************");
		LOG.info("*** Global options ***");
		LOG.info("Mode = " + this.getMode());
		LOG.info("Import dump into svn = \"" + this.importDump + "\"");
		LOG.info("Import dump only if svn repository exist = \"" + this.existingSvnrepos + "\"");
		LOG.info("Full dump path = \"" + this.fullDumpFilePattern + "\"");
		LOG.info("Incremental dump path = \"" + this.incrlDumpFilePattern + "\"");
		LOG.info("Incremental history path = \"" + this.incrHistoryFile + "\"");
		LOG.info("Save files list to = \"" + this.listFilesTo + "\"");
		LOG.info("Source provider's class = \"" + this.srcProvider.getClass().getName() + "\"");
		if (this.lastRevDate != null) {
			LOG.info("Date of last revision to dump = \"" + this.lastRevDate.toString() + "\"");
		}

		LOG.info("Size limit for dump files (in MB): "
				+ (this.dumpFileSizeLimit > 0 ? Integer.toString(this.dumpFileSizeLimit) : "none"));
		this.srcProvider.logEnvironmentInformation();
		if (this.svnAdmin != null) {
			this.svnAdmin.logEnvironmentInformation();
		}

		LOG.info("****************************************************************************");
	}

	public String getMode() {
		String mode = "";
		if (this.incrementalDump) {
			mode = "create incremental dump";
		}

		if (this.fullDump) {
			mode = "create full dump";
		}

		if (this.onlyListFiles) {
			mode = "list files";
		}

		return mode;
	}

	private String getStringProperty(String key, boolean mandatory) {
		String value = this.srcProperties.getProperty(key);
		if (value != null && value.length() >= 1) {
			value = value.trim();
		} else {
			if (mandatory) {
				this.error("configuration property \"" + key + "\" is not set");
			}

			value = null;
		}

		return value;
	}

	private String getStringProperty(String key) {
		return this.getStringProperty(key, true);
	}

	protected boolean getBooleanProperty(String key) {
		return ConfigUtil.getBooleanProperty(this.srcProperties, key);
	}

	private int getIntProperty(String key) {
		String val = this.getStringProperty(key);
		if (val == null) {
			return 0;
		} else {
			try {
				return Integer.parseInt(val);
			} catch (NumberFormatException var4) {
				this.error("configuration property value \"" + key + "\" is not a valid integer.");
				return 0;
			}
		}
	}

	private String insertDate(String value) {
		int indexOfDate = value.indexOf("%date%");
		return indexOfDate != -1 ? value.replaceAll("%date%", this.getDateString()) : value;
	}
}