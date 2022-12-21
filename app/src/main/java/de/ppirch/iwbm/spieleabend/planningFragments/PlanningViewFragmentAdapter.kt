package de.ppirch.iwbm.spieleabend.planningFragments

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Container-Klasse zur Verwaltung der Planing-Fragmente
 */
class PlanningViewFragmentAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {
    override fun getItemCount(): Int {
        return 3
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SuggestionsFragment()
            1 -> PollSuggestionsFragment()
            2 -> HostFragment()
            else -> SuggestionsFragment()
        }
    }

}
