/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.objects;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.Map.Entry;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.lib.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.BaseToRemoteConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.osgi.util.NLS;
import org.json.*;

public class Remote extends GitObject {

	public static final String RESOURCE = "remote"; //$NON-NLS-1$
	public static final String TYPE = "Remote"; //$NON-NLS-1$

	private String name;

	public Remote(URI cloneLocation, Repository db, String name) {
		super(cloneLocation, db);
		this.name = name;
	}

	/**
	 * Returns a JSON representation of this remote.
	 */
	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		return toJSON(true, null);
	}

	public JSONObject toJSON(final String newBranch) throws JSONException, URISyntaxException, IOException, CoreException {
		return toJSON(true, newBranch);
	}

	public JSONObject toJSON(boolean includeChildren, final String newBranch) throws JSONException, URISyntaxException, IOException, CoreException {
		check();
		JSONObject result = super.toJSON();
		result.put(ProtocolConstants.KEY_NAME, name);
		result.put(GitConstants.KEY_URL, getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, name, "url" /*RemoteConfig.KEY_URL*/)); //$NON-NLS-1$
		result.put(GitConstants.KEY_PUSH_URL, getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, name, "pushurl" /*RemoteConfig.KEY_PUSHURL*/)); //$NON-NLS-1$

		if (includeChildren) {
			JSONArray children = new JSONArray();
			boolean branchFound = false;
			List<Ref> refs = new ArrayList<Ref>();
			String currentBranch = db.getBranch();
			for (Entry<String, Ref> refEntry : db.getRefDatabase().getRefs(Constants.R_REMOTES + name + "/").entrySet()) { //$NON-NLS-1$
				if (!refEntry.getValue().isSymbolic()) {
					Ref ref = refEntry.getValue();
					String name = ref.getName();
					name = Repository.shortenRefName(name).substring(Constants.DEFAULT_REMOTE_NAME.length() + 1);
					if (currentBranch.equals(name)) {
						refs.add(0, ref);
					} else {
						refs.add(ref);
					}
				}
			}
			for (Ref ref : refs) {
				String remoteBranchName = Repository.shortenRefName(ref.getName());
				remoteBranchName = remoteBranchName.substring((this.name + "/").length()); //$NON-NLS-1$
				RemoteBranch remoteBranch = new RemoteBranch(cloneLocation, db, this, remoteBranchName);
				children.put(remoteBranch.toJSON());
				if (newBranch != null && !newBranch.isEmpty() && remoteBranchName.equals(newBranch)) {
					children = new JSONArray().put(remoteBranch.toJSON());
					branchFound = true;
					break;
				}
			}

			if (!branchFound && newBranch != null && !newBranch.isEmpty()) {
				JSONObject o = new JSONObject();
				// TODO: this should be a RemoteBranch
				String name = Constants.R_REMOTES + getName() + "/" + newBranch; //$NON-NLS-1$
				o.put(ProtocolConstants.KEY_NAME, name.substring(Constants.R_REMOTES.length()));
				o.put(ProtocolConstants.KEY_FULL_NAME, name);
				o.put(ProtocolConstants.KEY_TYPE, RemoteBranch.TYPE);
				o.put(ProtocolConstants.KEY_LOCATION, BaseToRemoteConverter.REMOVE_FIRST_2.baseToRemoteLocation(cloneLocation, "" /*short name is {remote}/{branch}*/, Repository.shortenRefName(name))); //$NON-NLS-1$
				children.put(o);
			}

			result.put(ProtocolConstants.KEY_CHILDREN, children);
		}
		return result;
	}

	public URI getLocation() throws URISyntaxException {
		return BaseToRemoteConverter.REMOVE_FIRST_2.baseToRemoteLocation(cloneLocation, name, "" /* no branch name */); //$NON-NLS-1$
	}

	private void check() {
		if (!getConfig().getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION).contains(name))
			throw new IllegalArgumentException(NLS.bind("Remote {0} not found.", name));
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return "Remote [name=" + name + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	protected String getType() {
		return TYPE;
	}
}
