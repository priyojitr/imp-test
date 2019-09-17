package com.tck.svnimporter.pvcsprovider.internal;

import org.polarion.svnimporter.common.Exec;
import org.polarion.svnimporter.common.Log;
import com.tck.svnimporter.pvcsprovider.internal.PvcsExec.1;
import com.tck.svnimporter.pvcsprovider.internal.PvcsExec.2;

public class PvcsExec extends Exec {
	private static final Log LOG = Log.getLog(PvcsExec.class);
	public static final int ERROR_WRONG_PROJECT_PATH = 401;

	public PvcsExec(String[] cmd) {
		super(cmd);
	}

	protected void setupProcessStderr(Process process) throws Exception {
      this.setStderrConsumer(new 1(this));
      super.setupProcessStderr(process);
   }

	protected void setupProcessStdout(Process process) throws Exception {
      this.setStdoutConsumer(new 2(this));
      super.setupProcessStdout(process);
   }

	private void checkProjectPathError(String line) {
		if (line.startsWith("The project root could not be loaded")) {
			LOG.error("wrong project path");
			this.setErrorCode(401);
		}

	}
}