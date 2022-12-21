package de.ppirch.iwbm.spieleabend

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import de.ppirch.iwbm.spieleabend.databinding.EvaluationLayoutBinding
import de.ppirch.iwbm.spieleabend.evaluation.EvaluationViewFragmentAdapter

/**
 * Navigation innerhalb der Evaluation-Activity ueber deren Fragmente.
 * Fachlogik entsprechend innerhalb den Fragmenten @see evaluation
 */
class EvaluationActivity : AppCompatActivity() {
    private lateinit var binding: EvaluationLayoutBinding
    private lateinit var tabLayout: TabLayout
    private lateinit var fragmentFrame: ViewPager2

    private val connection = DBServerConnectionClass(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EvaluationLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar((binding.toolbarEvaluation))
        //Aktuellen Gruppennamen zur Actionbar hinzufügen
        binding.toolbarEvaluation.title = "${binding.toolbarEvaluation.title} - ${
            connection.getCurrentUserGroup().getAsString("group")
        }"

        tabLayout = binding.tabLayoutEvaluation
        fragmentFrame = binding.viewPagerEvaluation
        fragmentFrame.adapter = EvaluationViewFragmentAdapter(supportFragmentManager, lifecycle)

        val tabList = arrayOf(getString(R.string.evaluation), getString(R.string.myEvaluation))
        TabLayoutMediator(tabLayout, fragmentFrame) { tab, position ->
            tab.text = tabList[position]
        }.attach()
    }
}