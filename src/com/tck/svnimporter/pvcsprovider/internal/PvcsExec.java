package com.tck.svnimporter.pvcsprovider.internal;

import org.polarion.svnimporter.common.Exec;
import org.polarion.svnimporter.common.Log;
import org.polarion.svnimporter.common.StreamConsumer;

public class PvcsExec extends Exec {
	private static final Log LOG = Log.getLog(PvcsExec.class);
	public static final int ERROR_WRONG_PROJECT_PATH = 401;

	public PvcsExec(String[] cmd) {
		super(cmd);
	}

	protected void setupProcessStderr(Process process) throws Exception {
		this.setStderrConsumer((StreamConsumer) new StreamConsumer() {
			public void consumeLine(final String line) {
				PvcsExec.this.checkProjectPathError(line);
			}
		});
		super.setupProcessStderr(process);
	}

	protected void setupProcessStdout(Process process) throws Exception {
		this.setStdoutConsumer((StreamConsumer) new StreamConsumer() {
			public void consumeLine(final String line) {
				PvcsExec.this.checkProjectPathError(line);
			}
		});
		super.setupProcessStdout(process);
	}

	private void checkProjectPathError(String line) {
		if (line.startsWith("The project root could not be loaded")) {
			LOG.error("wrong project path");
			this.setErrorCode(401);
		}

	}
}