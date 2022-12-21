package de.ppirch.iwbm.spieleabend

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Verwalten der SQLite-DB
 */
class DBHelperClass(context: Context, factory: SQLiteDatabase.CursorFactory?) :
    SQLiteOpenHelper(context, DATABASE_NAME, factory, DATABASE_VERSION) {

    // below is the method for creating a database by a sqlite query
    override fun onCreate(db: SQLiteDatabase) {
        // below is a sqlite query, where column names
        // along with their data types is given
        val query =
            ("CREATE TABLE $TABLE_NAME ($ID_COLUMN INTEGER PRIMARY KEY AUTOINCREMENT, $NAME_COLUMN TEXT, $VALUE_COLUMN TEXT)")

        // we are calling sqlite
        // method for executing our query
        db.execSQL(query)
        val queryGroup =
            ("INSERT INTO $TABLE_NAME ($NAME_COLUMN, $VALUE_COLUMN) VALUES ('$NAME_GROUP_COUNT', '0')")
        val queryCurrentGroup =
            ("INSERT INTO $TABLE_NAME ($NAME_COLUMN, $VALUE_COLUMN) VALUES ('$NAME_CURRENT_GROUP', '0')")
        db.execSQL(queryGroup)
        db.execSQL(queryCurrentGroup)

    }

    override fun onUpgrade(db: SQLiteDatabase, p1: Int, p2: Int) {
        // this method is to check if table already exists
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    //Standard
    // This method is for adding data in our database
    fun insert(group: String, username: String) {

        // below we are creating
        // a content values variable
        val values = ContentValues()

        // we are inserting our values
        // in the form of key-value pair
        values.put(NAME_COLUMN, username)
        values.put(VALUE_COLUMN, group)

        // here we are creating a
        // writable variable of
        // our database as we want to
        // insert value in our database
        val db = this.writableDatabase

        // all values are inserted into database
        db.insert(TABLE_NAME, null, values)

        // at last we are
        // closing our database
        db.close()
    }

    // below method is to get
    // all data from our database
    fun select(name: String? = null): Cursor? {
        // here we are creating a readable
        // variable of our database
        // as we want to read value from it
        val db = this.readableDatabase

        // below code returns a cursor to
        // read data from the database
        return if (name == null) {
            db.rawQuery("SELECT * FROM $TABLE_NAME", null)
        } else {

            db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $NAME_COLUMN = '$name'", null)
        }
    }

    //update any Data-Row
    fun update(name: String, value: String, updateGroup: Boolean = false) {
        //writeable variable of Database
        val db = this.writableDatabase
        //variable with all Values
        val values = ContentValues()

        if (!updateGroup) {
            values.put(VALUE_COLUMN, value)
            db.update(TABLE_NAME, values, "$NAME_COLUMN=?", arrayOf(name))
        } else {
            values.put(NAME_COLUMN, name)
            db.update(TABLE_NAME, values, "$VALUE_COLUMN=?", arrayOf(value))
        }

        //close Connection
        db.close()
    }

    // This method is for adding data in our database
    fun delete(group: String) {

        //writeable variable of Database
        val db = this.writableDatabase
        db.delete(TABLE_NAME, "$VALUE_COLUMN=?", arrayOf(group))

        db.close()
    }


    companion object {
        // here we have defined variables for our database

        // below is variable for database name
        private const val DATABASE_NAME = "Spieleabend"

        // below is the variable for database version
        private const val DATABASE_VERSION = 1

        // below is the variable for table name
        const val TABLE_NAME = "personal_data_table"

        // below is the variable for id column
        const val ID_COLUMN = "id"

        // below is the variable for name column
        const val NAME_COLUMN = "name"
        const val NAME_COLUMN_INDEX = 1


        // below is the variable for age column
        const val VALUE_COLUMN = "value"
        const val VALUE_COLUMN_INDEX = 2

        // below are the IDs for different Entries

        const val NAME_GROUP_COUNT = "groupCount"
        const val ID_GROUP_COUNT = 0
        const val NAME_CURRENT_GROUP = "currentGroup"
        const val ID_CURRENT_GROUP = 1
        const val ID_FIRST_GROUP = 2
    }
}