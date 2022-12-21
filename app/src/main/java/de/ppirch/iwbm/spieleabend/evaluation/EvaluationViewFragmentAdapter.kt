package de.ppirch.iwbm.spieleabend.evaluation

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Container-Klasse zur Verwaltung der Evaluation-Fragmente
 */
class EvaluationViewFragmentAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {
    override fun getItemCount(): Int {
        return 2
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> EvaluationFragment()
            1 -> MyEvaluationFragment()
            else -> EvaluationFragment()
        }
    }

}
