package app.andrey_voroshkov.chorus_laptimer;

import android.content.res.Resources;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

/**
 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
public class SectionsPagerAdapter extends FragmentPagerAdapter {

    private Resources resources;

    public SectionsPagerAdapter(FragmentManager fm) {
        super(fm);
    }

    public SectionsPagerAdapter(FragmentManager fm, Resources resources) {
        this(fm);
        this.resources = resources;
    }

    @Override
    public Fragment getItem(int position) {
        // getItem is called to instantiate the fragment for the given page.
        // Return a DeviceSetupFragment (defined as a static inner class below).
        switch (position) {
            case 0: return RaceSetupFragment.newInstance(position + 1);
            case 1: return ChannelsSetupFragment.newInstance(position + 1);
            case 2: return PilotsSetupFragment.newInstance(position + 1);
            case 3: return RaceResultFragment.newInstance(position + 1);
        }
        return null;
    }

    @Override
    public int getCount() {
        // Show 4 total pages.
        return 4;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        String title = null;
        switch (position) {
            case 0:
                title = resources.getString(R.string.tab_setup);
                break;
            case 1:
                title = resources.getString(R.string.tab_frequency);
                break;
            case 2:
                title = resources.getString(R.string.tab_pilots);
                break;
            case 3:
                title = resources.getString(R.string.tab_race);
                break;
        }
        return title;
    }
}

