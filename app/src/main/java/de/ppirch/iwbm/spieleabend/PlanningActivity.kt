package de.ppirch.iwbm.spieleabend

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import de.ppirch.iwbm.spieleabend.databinding.PlanningLayoutBinding
import de.ppirch.iwbm.spieleabend.planningFragments.PlanningViewFragmentAdapter

/**
 * Navigation innerhalb der Planning-Activity ueber deren Fragmente.
 * Fachlogik entsprechend innerhalb den Fragmenten @see planningFragments
 */
class PlanningActivity : AppCompatActivity() {

    //Implementieren der ViewBinding-Klasse des Layouts
    private lateinit var binding: PlanningLayoutBinding
    private lateinit var tabLayout: TabLayout
    private lateinit var fragmentFrame: ViewPager2

    private val connection = DBServerConnectionClass(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PlanningLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar((binding.toolbarPlanning))

        //Beschriftung abhaengig der ausgewaehlten Gruppe
        binding.toolbarPlanning.title = "${binding.toolbarPlanning.title} - ${
            connection.getCurrentUserGroup().getAsString("group")
        }"

        tabLayout = binding.tabLayoutPlanning
        fragmentFrame = binding.viewPagerPlanning

        fragmentFrame.adapter = PlanningViewFragmentAdapter(supportFragmentManager, lifecycle)

        //Navigation ueber Tab
        val tabList = arrayOf(
            getString(R.string.suggestions),
            getString(R.string.poll),
            getString(R.string.host)
        )
        TabLayoutMediator(tabLayout, fragmentFrame) { tab, position ->
            tab.text = tabList[position]
        }.attach()
    }

}