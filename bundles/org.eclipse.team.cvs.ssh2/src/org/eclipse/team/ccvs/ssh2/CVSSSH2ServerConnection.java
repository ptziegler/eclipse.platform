/* -*-mode:java; c-basic-offset:2; -*- */
/*******************************************************************************
 * Copyright (c) 2003, Atsuhiko Yamanaka, JCraft,Inc. and others. All rights
 * reserved. This program and the accompanying materials are made available
 * under the terms of the Common Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: Atsuhiko Yamanaka, JCraft,Inc. - initial API and
 * implementation.
 ******************************************************************************/
package org.eclipse.team.ccvs.ssh2;
import java.io.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.IServerConnection;
import org.eclipse.team.internal.ccvs.core.connection.CVSAuthenticationException;
import org.eclipse.team.internal.ccvs.ssh.SSHServerConnection;
import com.jcraft.jsch.*;
import org.eclipse.team.ccvs.ssh2.Policy;
public class CVSSSH2ServerConnection implements IServerConnection {
	private static final String COMMAND = "cvs server"; //$NON-NLS-1$
	private ICVSRepositoryLocation location;
	private String password;
	private InputStream inputStream;
	private OutputStream outputStream;
	private Session session;
	private Channel channel;
	private IServerConnection ssh1;
	protected CVSSSH2ServerConnection(ICVSRepositoryLocation location, String password) {
		this.location = location;
		this.password = password;
	}
	public void close() throws IOException {
		if (ssh1 != null) {
			ssh1.close();
			return;
		}
		if (channel != null)
			channel.disconnect();
	}
	public InputStream getInputStream() {
		if (ssh1 != null) {
			return ssh1.getInputStream();
		}
		return inputStream;
	}
	public OutputStream getOutputStream() {
		if (ssh1 != null) {
			return ssh1.getOutputStream();
		}
		return outputStream;
	}
	public void open(IProgressMonitor monitor) throws IOException, CVSAuthenticationException {
		if (ssh1 != null) {
			ssh1.open(monitor);
			return;
		}
		monitor.subTask(Policy.bind("CVSSSH2ServerConnection.open", location.getHost())); //$NON-NLS-1$
		monitor.worked(1);
		try {
			String hostname = location.getHost();
			String username = location.getUsername();
			int port = location.getPort();
			if (port == ICVSRepositoryLocation.USE_DEFAULT_PORT)
				port = 0;
			int retry = 1;
			OutputStream channel_out;
			InputStream channel_in;
			while (true) {
				session = JSchSession.getSession(location, username, password, hostname, port, monitor);
				channel = session.openChannel("exec"); //$NON-NLS-1$
				((ChannelExec) channel).setCommand(COMMAND);
				channel_out = channel.getOutputStream();
				channel_in = channel.getInputStream();
				try {
					channel.connect();
				} catch (JSchException ee) {
				  retry--;
				  if(retry<0){
				    throw new CVSAuthenticationException(Policy.bind("CVSSSH2ServerConnection.3"), CVSAuthenticationException.NO_RETRY); //$NON-NLS-1$
				  }
				  if(session.isConnected()){
				    session.disconnect();
				  }
				  continue;
				}
				break;
			}
			inputStream = channel_in;
			outputStream = channel_out;
		} catch (JSchException e) {
			if (e.toString().indexOf("invalid server's version string") == -1) { //$NON-NLS-1$
				throw new CVSAuthenticationException(e.toString(), CVSAuthenticationException.NO_RETRY);
			}
			ssh1 = new SSHServerConnection(location, password);
			if (ssh1 == null) {
				throw new CVSAuthenticationException(e.toString(), CVSAuthenticationException.NO_RETRY);
			}
			ssh1.open(monitor);
		}
	}
}
