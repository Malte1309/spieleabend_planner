package de.ppirch.iwbm.spieleabend.planningFragments

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import de.ppirch.iwbm.spieleabend.DBServerConnectionClass
import de.ppirch.iwbm.spieleabend.R
import de.ppirch.iwbm.spieleabend.databinding.SuggestionsFragLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Diese Klasse ist die Fachlogik der zu versendeten Essens- & Spielvorschlaege
 * Dargestellt als TAB innerhalb der Planning-Activity (ist ein Fragment)
 */
class SuggestionsFragment : Fragment(R.layout.suggestions_frag_layout) {

    //Implementieren der ViewBinding-Klasse des Layouts
    private var _binding: SuggestionsFragLayoutBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(Dispatchers.IO)

    private lateinit var connection: DBServerConnectionClass

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SuggestionsFragLayoutBinding.inflate(inflater, container, false)

        connection = DBServerConnectionClass(requireContext())

        scope.launch {
            getServerData("get")
        }

        binding.buttonSuggestionCommit.setOnClickListener {
            /*Wenn im Feld für die Textfeldeingabe etwas drinsteht, soll diese Eingabe als Vorschlag gesendet werden,
            die Auswahl in der DropDown wird in diesem Fall ignoriert. */
            scope.launch {

                val mealText: String = if (binding.editSuggestMeal.text.isNotEmpty()) {
                    //Eingabe via Textfeld
                    binding.editSuggestMeal.text.toString()
                } else {
                    //Eingabe via Dropdown-Liste
                    binding.spinnerMeals.selectedItem.toString()
                }
                val gameText: String = if (binding.editSuggestGame.text.isNotEmpty()) {
                    binding.editSuggestGame.text.toString()

                } else {
                    binding.spinnerGames.selectedItem.toString()
                }

                getServerData("suggestion", gameText, mealText)

                //reset der Eingabe nach Versenden
                withContext(Dispatchers.Main) {
                    binding.editSuggestGame.setText("")
                    binding.editSuggestMeal.setText("")
                    binding.spinnerGames.setSelection(0)
                    binding.spinnerMeals.setSelection(0)
                }
            }


            hideKeyBoard()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Beziehen der Daten als JSON vom WebFTP-Server, falls action="get" oder senden der Vorschlaege (action="suggestion")
     * @param action Fallunterscheidung im php-Skript ("get", "suggestions")
     * @param game Spielvorschlag
     * @param meal Essensvorschlag
     */
    private suspend fun getServerData(action: String, game: String = "", meal: String = "") {
        var responseObject = JSONObject()
        var sendResponse = ""

        if (action == "get") {
            responseObject =
                connection.getObject(action = action, phpScript = "DataAllGroups/index")
        } else {
            sendResponse = connection.sendObject(
                action = action,
                phpScript = "suggestions",
                game = game,
                meal = meal
            )
        }

        println("Antwort: $responseObject")

        withContext(Dispatchers.Main) {
            if (action == "get") {
                if (!responseObject.has("fehler")) {
                    try {
                        val games = jsonArrayToList(responseObject.getJSONArray("games"))
                        games.add(0, getString(R.string.game))
                        val meals = jsonArrayToList(responseObject.getJSONArray("meals"))
                        meals.add(0, getString(R.string.meal))
                        binding.spinnerGames.adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_spinner_dropdown_item,
                            games
                        )
                        binding.spinnerMeals.adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_spinner_dropdown_item,
                            meals
                        )
                    } catch (error: Exception) {
                        Log.e("Error in SuggestionsFragment in PlanningActivity", "Error: $error")
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.information_not_processed_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        when (responseObject.get("fehler")) {
                            "JSON" -> getString(R.string.information_not_processed_toast)
                            "Server" -> getString(R.string.server_not_found)
                            "No Group" -> getString(R.string.no_group_found)
                            else -> getString(R.string.unknown_error)
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(requireContext(), sendResponse, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Verarbeiten des Uebertragungsformats JSON in eine Liste
     */
    private fun jsonArrayToList(jsonArray: JSONArray): MutableList<String> {
        val list = mutableListOf<String>()

        for (index in 0 until jsonArray.length()) {
            list.add(jsonArray[index].toString())
        }
        return list
    }

    private fun hideKeyBoard() {
        val imm =
            requireActivity().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        var currentFocus = requireActivity().currentFocus
        if (currentFocus == null) {
            currentFocus = View(requireActivity())
        }
        imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
    }

}