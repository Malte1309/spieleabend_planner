package de.ppirch.iwbm.spieleabend

import android.app.Activity
import android.database.Cursor
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.ppirch.iwbm.spieleabend.databinding.SettingsLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.StandardCharsets

/*
 * Bei den anderen Activity's werden die Server- und DB-Aufrufe durch die DBServerConnectionKlasse geregelt.
 * Hier können aber keine standardisierten Aufrufe verwendet werden, weil die Aufrufe zu komplex sind,
 * und außerdem sonst nirgends verwendet werden.
 *
 * Daher werden in dieser Activity die Server- und DB-Aufrufe direkt durchgeführt.
 * Die OKHttp-Request werden in eigenen Funktionen aufgebaut, versendet und die Antworten verarbeitet.
 * Die DB-Aufrufe (Die DB, die auf dem Endgerät gespeichert ist) werden über die DBHelperClass,auf die auch
 * die DBServerConnectionClass zugreift, geregelt.
 */

/**
 * Diese Klasse steuert die Grundeinstellungen wie Gruppe erstellen, Gruppe beitreten, Gruppe verlassen,
 * Namen aendern, ausgewaehlte Gruppe wechseln und entsprechend auch die SQLite-DB
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: SettingsLayoutBinding

    private val scope = CoroutineScope(Dispatchers.IO)
    private val groups = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar((binding.toolbarDashboard))

        //Username soll erstmal nur eine Anzeige sein, und erst bei ButtonClick aenderbar sein.
        binding.fieldUsername.inputType = InputType.TYPE_NULL

        val db = DBHelperClass(this, null)
        var cursor = refresh(db)

        //Name aendern - falls neuer Name kein Gruppenmitglied - Achtung Evaluation bleiben beim alten Namen
        var safeCurrentUsername = ""
        binding.buttonUsername.setOnClickListener {
            if (binding.buttonUsername.text == getString(R.string.change) && binding.spinnerGroups.selectedItem.toString() != getString(
                    R.string.no_group_found
                )
            ) {
                safeCurrentUsername = binding.fieldUsername.text.toString()
                binding.fieldUsername.inputType = InputType.TYPE_CLASS_TEXT
                binding.buttonUsername.text = getString((R.string.save))

            } else if (binding.spinnerGroups.selectedItem.toString() != getString(R.string.no_group_found)) {
                if (binding.fieldUsername.text.isNotEmpty()) {
                    scope.launch {
                        cursor = callServer(
                            "change",
                            safeCurrentUsername,
                            binding.spinnerGroups.selectedItem.toString(),
                            cursor,
                            db,
                            binding.fieldUsername.text.toString()
                        )
                    }

                } else {
                    Toast.makeText(this, getString(R.string.username_not_empty), Toast.LENGTH_SHORT)
                        .show()
                    binding.fieldUsername.setText(safeCurrentUsername)
                }
                binding.fieldUsername.inputType = InputType.TYPE_NULL
                binding.buttonUsername.text = getString((R.string.change))
            }
        }

        //Gruppe erstellen - falls Gruppe noch nicht vorhanden & kein Mitglied
        binding.buttonCreateGroup.setOnClickListener {
            if (binding.editJoinGroup.text.isNotEmpty()) {
                if (binding.editJoinGroupUsername.text.isNotEmpty()) {
                    var groupJoined = false
                    if (binding.spinnerGroups.selectedItem.toString() != getString(R.string.no_group_found)) {
                        cursor.moveToPosition(DBHelperClass.ID_FIRST_GROUP)
                        while (!cursor.isAfterLast) {
                            if (cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX) == binding.editJoinGroup.text.toString()) {
                                groupJoined = true
                                Toast.makeText(
                                    this,
                                    getString(R.string.in_group),
                                    Toast.LENGTH_SHORT
                                ).show()
                                binding.editJoinGroup.setText("")
                                binding.editJoinGroupUsername.setText("")
                                hideKeyBoard()
                                break
                            } else {
                                cursor.moveToNext()
                            }
                        }
                    }

                    if (!groupJoined) {
                        scope.launch {
                            cursor = callServer(
                                "create",
                                binding.editJoinGroupUsername.text.toString(),
                                binding.editJoinGroup.text.toString(),
                                cursor,
                                db
                            )
                        }
                    }

                } else {
                    Toast.makeText(this, getString(R.string.username_not_empty), Toast.LENGTH_SHORT)
                        .show()
                }
            } else {
                Toast.makeText(this, getString(R.string.group_not_empty), Toast.LENGTH_SHORT).show()
            }
            hideKeyBoard()
        }

        //Gruppe beitreten - falls nicht bereits Mitglied & Gruppe vorhanden
        binding.buttonJoinGroup.setOnClickListener {
            if (binding.editJoinGroup.text.isNotEmpty()) {
                if (binding.editJoinGroupUsername.text.isNotEmpty()) {
                    var groupJoined = false
                    if (binding.spinnerGroups.selectedItem.toString() != getString(R.string.no_group_found)) {
                        cursor.moveToPosition(DBHelperClass.ID_FIRST_GROUP)
                        while (!cursor.isAfterLast) {
                            if (cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX) == binding.editJoinGroup.text.toString()) {
                                groupJoined = true
                                binding.editJoinGroup.setText("")
                                binding.editJoinGroupUsername.setText("")
                                hideKeyBoard()
                                Toast.makeText(
                                    this,
                                    getString(R.string.in_group),
                                    Toast.LENGTH_SHORT
                                ).show()
                                break
                            } else {
                                cursor.moveToNext()
                            }
                        }
                    }
                    if (!groupJoined) {
                        scope.launch {
                            cursor = callServer(
                                "join",
                                binding.editJoinGroupUsername.text.toString(),
                                binding.editJoinGroup.text.toString(),
                                cursor,
                                db
                            )
                        }
                    }
                } else {
                    Toast.makeText(this, getString(R.string.username_not_empty), Toast.LENGTH_SHORT)
                        .show()
                }
            } else {
                Toast.makeText(this, getString(R.string.group_not_empty), Toast.LENGTH_SHORT).show()
            }
            hideKeyBoard()
        }

        //Gruppe verlassen - Host kann dies nicht.
        binding.buttonLeaveGroup.setOnClickListener {
            if (binding.spinnerGroups.selectedItem.toString() != getString(R.string.no_group_found)) {
                scope.launch {
                    cursor = callServer(
                        "leave",
                        binding.fieldUsername.text.toString(),
                        binding.spinnerGroups.selectedItem.toString(),
                        cursor,
                        db
                    )
                }
            }
        }

        binding.spinnerGroups.onItemSelectedListener = object :

            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                db.update(DBHelperClass.NAME_CURRENT_GROUP, position.toString())
                binding.fieldUsername.setText(
                    if (groups[0] != getString(R.string.no_group_found)) {
                        cursor.moveToPosition(DBHelperClass.ID_FIRST_GROUP)
                        while (cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX) != binding.spinnerGroups.selectedItem && !cursor.isAfterLast) {
                            cursor.moveToNext()
                        }
                        cursor.getString(DBHelperClass.NAME_COLUMN_INDEX)
                    } else {
                        ""
                    }
                )
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}

        }
    }

    /**
     * aktualisieren der Gruppenzugehoerigkeit in der Dropdownlsite (SQLite)
     */
    private fun refresh(db: DBHelperClass, currentGroupFromField: String = ""): Cursor {
        var currentGroup =
            currentGroupFromField //Wenn das Feld leer ist, wird diese Variable spaeter aus der Datenbank gefuellt
        groups.clear()

        val cursor = db.select()
        cursor!!.moveToFirst()

        if (!cursor.isAfterLast && cursor.getColumnIndex(DBHelperClass.VALUE_COLUMN) == DBHelperClass.VALUE_COLUMN_INDEX) {
            cursor.moveToPosition(DBHelperClass.ID_GROUP_COUNT)
            val countGroups: Int = cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX).toInt()
            if (countGroups > 0) {
                val countGroupsLastId: Int = countGroups + DBHelperClass.ID_FIRST_GROUP - 1
                for (groupIndex in DBHelperClass.ID_FIRST_GROUP..countGroupsLastId) {
                    cursor.moveToPosition(groupIndex)
                    groups.add(cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX))
                }
            } else {
                groups.add(getString(R.string.no_group_found))
            }
        }

        binding.spinnerGroups.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, groups)


        if (currentGroup.isNotEmpty()) {
            binding.spinnerGroups.setSelection(groups.indexOf(currentGroup))
        } else {
            cursor.moveToPosition(DBHelperClass.ID_CURRENT_GROUP)
            binding.spinnerGroups.setSelection(
                cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX).toInt()
            )
            currentGroup = groups[cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX).toInt()]
        }

        binding.fieldUsername.setText(

            if (groups[0] != getString(R.string.no_group_found)) {

                cursor.moveToPosition(DBHelperClass.ID_FIRST_GROUP)

                while (cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX) != currentGroup) {
                    if (!cursor.isAfterLast) {
                        cursor.moveToNext()
                    } else {
                        break
                    }
                }

                cursor.getString(DBHelperClass.NAME_COLUMN_INDEX)

            } else {
                ""
            }
        )

        binding.editJoinGroup.setText("")
        binding.editJoinGroupUsername.setText("")
        hideKeyBoard()

        return cursor
    }

    private fun hideKeyBoard() {
        val imm = this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        var currentFocus = this.currentFocus
        if (currentFocus == null) {
            currentFocus = View(this)
        }
        imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
    }

    /**
     * Persistierung der Eingaben ueber enterGroup.php (WebFTP) im <groupname>.json je nach gewaehlter Aktion mit Interpretierung
     * des Feedbacks und Aktualisierung der SQLite-DB fuer Navigation in der App.
     */
    private suspend fun callServer(
        action: String,
        username: String = "",
        group: String,
        getCursor: Cursor,
        db: DBHelperClass,
        additionalData: String = ""
    ): Cursor {
        var connectFailed = false
        var cursor = getCursor

        val client = OkHttpClient()
        val url = "https://spieleabend.open-serv.info/enterGroup.php"

        val body: FormBody = FormBody.Builder(StandardCharsets.UTF_8)
            .add("access", "spieleabend.iwbm@access")
            .add("action", action)
            .add("group", group)
            .add("username", username)
            .add("additionalData", additionalData)
            .build()

        val request: Request = Request
            .Builder()
            .url(url)
            .post(body)
            .build()

        val response: String = try {
            client.newCall(request).execute().body!!.string()
        } catch (_: Exception) {
            connectFailed = true
            getString(R.string.connect_failed)
        }

        if (!connectFailed) {
            withContext(Dispatchers.Main) {
                //Gruppe erstellen oder beitreten
                if (action == "create" || action == "join") {
                    if (response == "Group create and join" || response == "Join success") {
                        db.insert(group, username)
                        cursor.moveToPosition(DBHelperClass.ID_GROUP_COUNT)
                        db.update(
                            DBHelperClass.NAME_GROUP_COUNT,
                            (cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX)
                                .toInt() + 1).toString()
                        )
                        Toast.makeText(
                            this@SettingsActivity,
                            when (response) {
                                "Join success" -> getString(R.string.join_success)
                                "Group create and join" -> getString(R.string.group_create_join)
                                else -> "Fehler"
                            }, Toast.LENGTH_SHORT
                        ).show()

                        cursor = refresh(db, binding.editJoinGroup.text.toString())
                    } else {
                        if (response == "Username exist in group") {
                            Toast.makeText(
                                this@SettingsActivity,
                                getString(R.string.username_exists),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        cursor = refresh(db)
                        Toast.makeText(this@SettingsActivity, response, Toast.LENGTH_SHORT).show()

                    }

                    //Gruppe verlassen - nur falls kein Host
                } else if (action == "leave") {
                    if (response == "Host can't leave!") {
                        Toast.makeText(
                            this@SettingsActivity,
                            getString(R.string.leave_fail_host),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        db.delete(binding.spinnerGroups.selectedItem.toString())
                        cursor.moveToPosition(DBHelperClass.ID_GROUP_COUNT)
                        db.update(
                            DBHelperClass.NAME_GROUP_COUNT,
                            (cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX)
                                .toInt() - 1).toString()
                        )
                        db.update(DBHelperClass.NAME_CURRENT_GROUP, "0")
                        cursor = refresh(db)
                        Toast.makeText(
                            this@SettingsActivity,
                            getString(R.string.leave_group),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    //Usernamen aendern in members und dashbaord (falls Host) innerhalb des JSON, nicht in Evaluation!
                } else if (action == "change") {
                    if (response == "Username changed") {
                        db.update(
                            binding.fieldUsername.text.toString(),
                            binding.spinnerGroups.selectedItem.toString(),
                            true
                        )
                        Toast.makeText(
                            this@SettingsActivity,
                            getString(R.string.username_changed),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            when (response) {
                                "User already exists" -> getString(R.string.username_exists)
                                "User not found" -> getString(R.string.user_not_found)
                                "Group not found" -> getString(R.string.group_not_found)
                                else -> "Fehler"
                            }, Toast.LENGTH_SHORT
                        ).show()
                    }
                    cursor = refresh(db)
                }
            }
            return cursor
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsActivity, response, Toast.LENGTH_SHORT).show()
            }
            return cursor
        }


    }
}