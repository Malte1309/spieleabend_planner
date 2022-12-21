package de.ppirch.iwbm.spieleabend

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import de.ppirch.iwbm.spieleabend.databinding.MessageLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Fachlogik des Messengers
 */
class MessageActivity : AppCompatActivity() {
    private lateinit var binding: MessageLayoutBinding

    private val scope = CoroutineScope(Dispatchers.IO)
    private val connection = DBServerConnectionClass(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MessageLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar((binding.toolbarMessages))
        //Aktuellen Gruppennamen zur Actionbar hinzufügen
        binding.toolbarMessages.title = "${binding.toolbarMessages.title} - ${
            connection.getCurrentUserGroup().getAsString("group")
        }"

        //Coroutine zum Empfangen neuer Nachrichten
        scope.launch {
            getSendMessages("get")
        }

        binding.swipeLayout.setOnRefreshListener {
            scope.launch {
                getSendMessages("get")
            }
            binding.swipeLayout.isRefreshing = false
        }

        binding.sendButton.setOnClickListener {
            if (binding.editMessage.text.toString().isNotEmpty()) {
                scope.launch {
                    getSendMessages("send", binding.editMessage.text.toString())
                }
            }
        }

        binding.editMessage.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                //Hier Funktionen rein, die ausgeführt werden sollen
                scope.launch {
                    getSendMessages("send", binding.editMessage.text.toString())
                }

                return@OnKeyListener true
            }
            false
        })
    }

    /**
     * Nachrichten verschicken oder empfangen, empfangen dabei automatisiert ueber Coroutines und evrsenden ueber onClick
     * @param action Fallunterscheidung innerhalb des PHP-Skripts (get oder send)
     * @param message Zu sendende Nachricht
     */
    private suspend fun getSendMessages(action: String, message: String = "") {
        if (action == "send") {
            //nutzen des OkHttp-Client
            val response =
                connection.sendObject(action = action, phpScript = "messages", extraData = message)

            withContext(Dispatchers.Main) {
                binding.editMessage.text.clear()

                when (response) {
                    "Group not found" -> Toast.makeText(
                        this@MessageActivity,
                        getString(R.string.group_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                    "Fehler: Server" -> Toast.makeText(
                        this@MessageActivity,
                        getString(R.string.server_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                    "Fehler: Unbekannt" -> Toast.makeText(
                        this@MessageActivity,
                        getString(R.string.unknown_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            //Seite neu laden, damit auch die gerade gesendeten Nachrichten und eventuelle neue angezeigt werden.
            getSendMessages("get")

        } else if (action == "get") {
            val responseObject = connection.getObject(action = action, phpScript = "messages")

            withContext(Dispatchers.Main) {
                val frame = binding.messagesFrame
                frame.removeAllViews()

                if (!responseObject.has("fehler")) {
                    try {
                        responseObject.remove("user")
                        responseObject.remove("group")

                        val names = jsonArrayToList(responseObject.names())
                        names.forEach {
                            val messages = jsonArrayToList(responseObject.getJSONObject(it).names())
                            val textView =
                                TextView(this@MessageActivity, null, 0, R.style.messageText)
                            var text = it

                            messages.forEach { getMessage ->
                                text += "\n   $getMessage"
                            }

                            textView.text = text
                            frame.addView(textView)
                        }

                        //Ans Ende des Nachrichtenblocks scrollen
                        val scrollView = binding.scrollView
                        var lastScrollPosition: Int
                        do {
                            lastScrollPosition = scrollView.scrollY
                            scrollView.scrollY += 100
                        } while (scrollView.scrollY > lastScrollPosition)

                    } catch (error: Exception) {
                        Log.e("Error in MessageActivity", "Error: $error")
                        Toast.makeText(
                            this@MessageActivity,
                            getString(R.string.information_not_processed_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    when (responseObject.get("fehler")) {
                        "Server" -> Toast.makeText(
                            this@MessageActivity,
                            getString(R.string.server_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                        "Unbekannt" -> Toast.makeText(
                            this@MessageActivity,
                            getString(R.string.unknown_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            hideKeyBoard()
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

    private fun hideKeyBoard() {
        val imm = this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        var currentFocus = this.currentFocus
        if (currentFocus == null) {
            currentFocus = View(this)
        }
        imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
    }
}