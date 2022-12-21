package de.ppirch.iwbm.spieleabend.planningFragments.hostFragments

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import de.ppirch.iwbm.spieleabend.DBServerConnectionClass
import de.ppirch.iwbm.spieleabend.R
import de.ppirch.iwbm.spieleabend.databinding.HostPollResultsFragLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Anzeigen der gewichteten Ergebnisse aus geaeusserten Wuenschen (@see "SuggestionsFragment" und
 * der Bewertung @see "PollSuggestionsFragment").
 * Dies ist von allen Anwendern (derzeit) einsehbar..
 */
class PollResults : Fragment() {
    //Implementieren der ViewBinding-Klasse des Layouts
    private var _binding: HostPollResultsFragLayoutBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var connection: DBServerConnectionClass
    private val suggestionsNamesList = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = HostPollResultsFragLayoutBinding.inflate(inflater, container, false)

        connection = DBServerConnectionClass(requireContext())

        binding.swipeLayout.setOnRefreshListener {
            scope.launch { getSuggestions() }
            binding.swipeLayout.isRefreshing = false
        }

        return binding.root
    }


    override fun onResume() {
        super.onResume()
        //Coroutine zum Aktualisieren der Ergebnisse
        scope.launch {
            getSuggestions()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Beziehen der Empfehlungen als JSON-Object von suggestions.php (WebFTP) und Umwandlung dieser.
     */
    private suspend fun getSuggestions() {
        val responseObject =
            connection.getObject(action = "getSuggestions", phpScript = "suggestions")

        withContext(Dispatchers.Main) {
            if (!responseObject.has("fehler")) {
                try {
                    var gameSuggestions = JSONObject()
                    if (objectStringIsNotEmpty(responseObject["gameSuggestions"].toString())) {
                        gameSuggestions = responseObject.getJSONObject("gameSuggestions")
                    }

                    var mealSuggestions = JSONObject()
                    if (objectStringIsNotEmpty(responseObject["mealSuggestions"].toString())) {
                        mealSuggestions = responseObject.getJSONObject("mealSuggestions")
                    }

                    val suggestions = unionJSONObjekts(gameSuggestions, mealSuggestions)
                    var suggestionNames = mutableListOf<String>()
                    if (suggestions.length() > 0) {
                        suggestionNames = jsonArrayToList(suggestions.names())
                    }

                    if (suggestionNames.isEmpty()) {
                        binding.noNewSuggestionText.visibility = View.VISIBLE
                    }

                    var gamePollResults = JSONObject()
                    if (objectStringIsNotEmpty(responseObject["gamePollResults"].toString())) {
                        gamePollResults = responseObject.getJSONObject("gamePollResults")
                    }

                    var mealPollResults = JSONObject()
                    if (objectStringIsNotEmpty(responseObject["mealPollResults"].toString())) {
                        mealPollResults = responseObject.getJSONObject("mealPollResults")
                    }

                    val pollResults = unionJSONObjekts(gamePollResults, mealPollResults)

                    val frame = binding.suggestionsLayoutFrame
                    binding.noNewSuggestionText.visibility = View.GONE
                    frame.removeAllViews()
                    suggestionsNamesList.clear()

                    //umformen, damit Durchschnittsbildung (als Ergebnis) moeglich ist mit Praesentation als Sterne-Rating
                    if (suggestionNames.isNotEmpty()) {
                        suggestionNames.indices.forEach {
                            var userSuggested = mutableListOf<String>()
                            if (suggestions[suggestionNames[it]].toString().contains(", ")) {
                                userSuggested = suggestions[suggestionNames[it]].toString()
                                    .split(", ") as MutableList<String>
                            } else {
                                userSuggested.add(suggestions[suggestionNames[it]].toString())
                            }

                            val textView =
                                TextView(requireContext(), null, 0, R.style.pollSuggestionText)
                            textView.layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            textView.text = suggestionNames[it]
                            suggestionsNamesList.add(suggestionNames[it])
                            frame.addView(textView)

                            val ratingBar =
                                RatingBar(requireContext(), null, 0, R.style.showSuggestions)
                            ratingBar.layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            ratingBar.tag = suggestionNames[it]
                            ratingBar.rating = pollResults[suggestionNames[it]].toString()
                                .toFloat() / userSuggested.size
                            frame.addView(ratingBar)
                        }
                    } else {
                        binding.noNewSuggestionText.visibility = View.VISIBLE
                    }
                } catch (error: Exception) {
                    Log.e("Error in PollResultsFragment in PlanningActivity", "Error: $error")
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.information_not_processed_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                when (responseObject.get("fehler")) {
                    "JSON" -> Toast.makeText(
                        requireContext(),
                        getString(R.string.information_not_processed_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                    "Server" -> Toast.makeText(
                        requireContext(),
                        getString(R.string.server_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                    "No Group" -> Toast.makeText(
                        requireContext(),
                        getString(R.string.no_group_found),
                        Toast.LENGTH_SHORT
                    ).show()
                    "Unbekannt" -> Toast.makeText(
                        requireContext(),
                        getString(R.string.unknown_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun jsonArrayToList(jsonArray: JSONArray?): MutableList<String> {
        val list = mutableListOf<String>()
        if (jsonArray!!.length() > 0) {
            for (index in 0 until jsonArray.length()) {
                list.add(jsonArray[index].toString())
            }
        }
        return list
    }

    private fun unionJSONObjekts(object1: JSONObject, object2: JSONObject): JSONObject {
        return if (object1.length() > 0) {
            if (object2.length() > 0) {
                val objekt2NameList = jsonArrayToList(object2.names())
                objekt2NameList.forEach {
                    object1.put(it, object2[it])
                }
                object1
            } else {
                object1
            }

        } else {
            return if (object2.length() > 0) {
                object2
            } else {
                (JSONObject())
            }
        }
    }

    private fun objectStringIsNotEmpty(objectString: String): Boolean {
        return !(objectString == "{}"
                || objectString == "[]"
                || objectString == "")
    }
}