package de.ppirch.iwbm.spieleabend.planningFragments.hostFragments

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Container-Klasse zur Verwaltung der Host-Fragmente (innerhalb der Planning-Activity)
 */
class HostViewFragmentAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {
    override fun getItemCount(): Int {
        return 2
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PollResults()
            1 -> SetNightFakts()
            else -> PollResults()
        }
    }

}
