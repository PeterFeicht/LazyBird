package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.support.v4.app.Fragment;

/**
 * Defines only one method that Fragments for tabs need to implement, {@link #getTitle()}.
 * 
 * @author Peter
 */
public abstract class AbstractTabFragment extends Fragment
{
	/**
	 * Gets the title associated with this fragment for use in an {@link ActionBar} tab.
	 * 
	 * @return the localized title of this fragment in case it is already attached to an
	 *         activity, a default title otherwise.
	 */
	public abstract CharSequence getTitle();
}
