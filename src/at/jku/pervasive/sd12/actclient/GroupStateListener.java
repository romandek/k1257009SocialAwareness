package at.jku.pervasive.sd12.actclient;

import at.jku.pervasive.sd12.actclient.CoordinatorClient.UserState;

/**
 * A GroupStateListener will be notified of any user activity changes.
 */
public interface GroupStateListener {
	void groupStateChanged(UserState[] groupState);
}
