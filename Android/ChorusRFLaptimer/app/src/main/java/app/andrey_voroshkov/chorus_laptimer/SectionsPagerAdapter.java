package app.andrey_voroshkov.chorus_laptimer;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

/**
 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
public class SectionsPagerAdapter extends FragmentPagerAdapter {

    public SectionsPagerAdapter(FragmentManager fm) {
        super(fm);
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
        // Show 3 total pages.
        return 4;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 0:
                return "Setup";
            case 1:
                return "Freq";
            case 2:
                return "Pilots";
            case 3:
                return "Race";
        }
        return null;
    }
}

