package com.tck.svnimporter.pvcsprovider.internal;

import java.io.File;
import java.text.DateFormat;
import java.util.Properties;
import org.polarion.svnimporter.common.Log;
import org.polarion.svnimporter.common.ProviderConfig;

public class PvcsConfig extends ProviderConfig {
	private static final Log LOG = Log.getLog(PvcsConfig.class);
	private String executable;
	private String projectPath;
	private String subproject;
	private File tempDir;
	private DateFormat logDateFormat;
	private String logDateFormatString;
	private String logDateLocale;
	private String logDateTimeZone;
	private String logEncoding;
	private boolean verboseExec;
	private String userName;
	private String password;
	private boolean keepVlogFile;
	private boolean importAttributes;
	private boolean validateCheckouts;
	private File checkoutTempDir;

	public PvcsConfig(Properties properties) {
		super(properties);
	}

	protected void configure() {
		super.configure();
		this.executable = this.getStringProperty("pvcs.executable", true);
		this.projectPath = this.getStringProperty("pvcs.projectpath", true);
		this.subproject = this.getStringProperty("pvcs.subproject", false);
		this.tempDir = this.getTempDir("pvcs.tempdir");
		this.logDateFormatString = this.getStringProperty("pvcs.log.dateformat", true);
		this.logDateLocale = this.getStringProperty("pvcs.log.datelocale", false);
		this.logDateTimeZone = this.getStringProperty("pvcs.log.datetimezone", false);
		this.logDateFormat = this.getDateFormat(this.logDateFormatString, this.logDateLocale, this.logDateTimeZone);
		this.logEncoding = this.getStringProperty("pvcs.log.encoding", true);
		this.verboseExec = this.getBooleanProperty("pvcs.verbose_exec");
		this.userName = this.getStringProperty("pvcs.username", false);
		this.password = this.getStringProperty("pvcs.password", false);
		this.keepVlogFile = this.getBooleanProperty("pvcs.keep_vlogfile");
		this.importAttributes = this.getBooleanProperty("pvcs.import_attributes");
		this.validateCheckouts = this.getBooleanProperty("pvcs.validate_checkouts");
		this.checkoutTempDir = this.getTempDir("pvcs.checkouttempdir");
	}

	public String getExecutable() {
		return this.executable;
	}

	public boolean isVerboseExec() {
		return this.verboseExec;
	}

	public String getProjectPath() {
		return this.projectPath;
	}

	public String getSubproject() {
		return this.subproject;
	}

	public File getTempDir() {
		return this.tempDir;
	}

	public File getCheckoutTempDir() {
		return this.checkoutTempDir;
	}

	public DateFormat getLogDateFormat() {
		return this.logDateFormat;
	}

	public String getLogEncoding() {
		return this.logEncoding;
	}

	public String getUserName() {
		return this.userName;
	}

	public String getPassword() {
		return this.password;
	}

	public boolean keepVlogFile() {
		return this.keepVlogFile;
	}

	public boolean importAttributes() {
		return this.importAttributes;
	}

	public boolean isValidateCheckouts() {
		return this.validateCheckouts;
	}

	protected void printError(String error) {
		LOG.error(error);
	}

	public void logEnvironmentInformation() {
		LOG.info("*** PVCS provider configuration ***");
		LOG.info("executable = \"" + this.executable + "\"");
		LOG.info("projectPath = \"" + this.projectPath + "\"");
		LOG.info("subproject = \"" + this.subproject + "\"");
		LOG.info("temp dir = \"" + this.tempDir.getAbsolutePath() + "\"");
		LOG.info("log date format = \"" + this.logDateFormatString + "\"");
		LOG.info("log date locale = \"" + this.logDateLocale + "\"");
		LOG.info("log date time zone = \"" + this.logDateTimeZone + "\"");
		LOG.info("log encoding = \"" + this.logEncoding + "\"");
		LOG.info("verbose exec = \"" + this.verboseExec + "\"");
		LOG.info("user name = \"" + this.userName + "\"");
		LOG.info("password = \"" + this.password + "\"");
		LOG.info("import archive attributes = \"" + this.importAttributes + "\"");
		LOG.info("keep vlog file = \"" + this.keepVlogFile + "\"");
		LOG.info("validate checkouts = \"" + this.validateCheckouts + "\"");
		LOG.info("checkouttempdir = \"" + this.checkoutTempDir + "\"");
		super.logEnvironmentInformation();
	}
}