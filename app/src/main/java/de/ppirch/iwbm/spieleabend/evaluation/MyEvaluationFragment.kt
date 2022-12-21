package de.ppirch.iwbm.spieleabend.evaluation

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import de.ppirch.iwbm.spieleabend.DBServerConnectionClass
import de.ppirch.iwbm.spieleabend.R
import de.ppirch.iwbm.spieleabend.databinding.MyEvaluationFragLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Diese Klasse dient der Anzeige von Ergebnissen als Host vorheriger Spieleabende mit einem fortlaufenden Durchschnitt
 */
class MyEvaluationFragment : Fragment() {
    //Implementieren der ViewBinding-Klasse des Layouts
    private var _binding: MyEvaluationFragLayoutBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var connection: DBServerConnectionClass

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MyEvaluationFragLayoutBinding.inflate(inflater, container, false)
        connection = DBServerConnectionClass(requireContext())

        binding.swipeLayout.setOnRefreshListener {
            scope.launch {
                getEvaluation()
            }
            binding.swipeLayout.isRefreshing = false
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        //Coroutine zum Beziehen der Ergebnisse vom Server
        scope.launch {
            getEvaluation()
        }
    }

    /**
     * Beziehen der Ergebnisse als JSON-Object vom Server (evaluation.php), falls vorhanden,
     * und Feedback an den Nutzer.
     */
    private suspend fun getEvaluation() {
        val evaluationObject = connection.getObject(action = "get", phpScript = "evaluation")

        withContext(Dispatchers.Main) {
            val evalBarMeal = binding.evaluationBarMeal
            val evalBarHost = binding.evaluationBarHost
            val evalBarEvening = binding.evaluationBarEvening

            if (!evaluationObject.has("fehler")) {
                try {
                    val countEvaluations = evaluationObject.getInt("count")
                    if (countEvaluations > 0) { // Absicherung, um Teilen durch 0 zu verhindern
                        evalBarMeal.rating =
                            evaluationObject.getInt("evalMeal").toFloat() / countEvaluations
                        evalBarHost.rating =
                            evaluationObject.getInt("evalHost").toFloat() / countEvaluations
                        evalBarEvening.rating =
                            evaluationObject.getInt("evalEvening").toFloat() / countEvaluations
                    }

                } catch (error: Exception) {
                    Log.e("Error in MyEvaluationFrag", "Error: $error")
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.unknown_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                evalBarMeal.rating = 0F
                evalBarHost.rating = 0F
                evalBarEvening.rating = 0F

                Toast.makeText(
                    requireContext(),
                    when (evaluationObject.get("fehler")) {
                        "JSON" -> getString(R.string.information_not_processed_toast)
                        "Server" -> getString(R.string.server_not_found)
                        else -> getString(R.string.unknown_error)
                    }, Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}