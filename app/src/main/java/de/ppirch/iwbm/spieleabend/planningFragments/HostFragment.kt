package de.ppirch.iwbm.spieleabend.planningFragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import de.ppirch.iwbm.spieleabend.R
import de.ppirch.iwbm.spieleabend.databinding.HostFragLayoutBinding
import de.ppirch.iwbm.spieleabend.planningFragments.hostFragments.HostViewFragmentAdapter

/**
 * Layout-Steuerung fuer Ergebnsianzeige(Vorschlaege) und Auswahl als Host
 */
class HostFragment : Fragment(R.layout.host_frag_layout) {

    //Implementieren der ViewBinding-Klasse des Layouts
    private var _binding: HostFragLayoutBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabLayout: TabLayout
    private lateinit var fragmentFrame: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = HostFragLayoutBinding.inflate(inflater, container, false)

        tabLayout = binding.tabLayoutHost
        fragmentFrame = binding.viewPagerHost

        fragmentFrame.adapter = HostViewFragmentAdapter(childFragmentManager, lifecycle)

        val tabList = arrayOf(getString(R.string.poll_results), getString(R.string.set_night_fakts))
        TabLayoutMediator(tabLayout, fragmentFrame) { tab, position ->
            tab.text = tabList[position]
        }.attach()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}