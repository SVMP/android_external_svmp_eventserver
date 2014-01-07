/*
 Copyright 2013 The MITRE Corporation, All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this work except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.mitre.svmp.events;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * @author Joe Portner
 */
public class DatabaseHandler extends SQLiteOpenHelper {
    public static final String DB_NAME="org.mitre.svmp.events.db";
    public static final int DB_VERSION = 1;

    public static final int TABLE_LOCATION_SUBSCRIPTIONS = 0;
    public static final String[] Tables = new String[]{
            "LocationSubscriptions"
    };

    private static final String[] CreateTableQueries = new String[]{
            "CREATE TABLE LocationSubscriptions (ID INTEGER PRIMARY KEY, Provider TEXT NOT NULL, MinTime INTEGER, MinDistance REAL);"
    };

    private SQLiteDatabase db;

    public DatabaseHandler(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    public void close() {
        // cleanup
        try {
            if( db != null )
                db.close();
        } catch( Exception e ) {
            // don't care
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // loop through create table query strings and execute them
        for( String query : CreateTableQueries ) {
            try {
                db.execSQL(query);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        recreateTables(db);
    }

    private void recreateTables(SQLiteDatabase db) {
        // drop older table(s) if they exist
        for( String table : Tables )
            db.execSQL("DROP TABLE IF EXISTS " + table);

        // create tables again
        onCreate(db);
    }

    public LocationSubscription getForemostSubscription(String provider) {
        db = this.getWritableDatabase();

        // prepared statement for speed and security
        Cursor cursor = db.query(
                Tables[TABLE_LOCATION_SUBSCRIPTIONS], // table
                new String[]{ "MIN(MinTime)", "MIN(MinDistance), COUNT(*)" }, // columns (null == "*")
                "Provider=?", // selection ('where' clause)
                new String[]{ provider }, // selection args
                null, // group by
                null, // having
                null // order by
        );

        // try to get results and make a Subscription to return
        LocationSubscription locationSubscription = null;
        if (cursor.moveToFirst()) {
            try {
                // get values from query
                Long minTime = cursor.getLong(0);
                Float minDistance = cursor.getFloat(1);
                Integer count = cursor.getInt(2);

                if( count > 0 ) // if the table is not empty... (aggregate functions return a result regardless)
                    locationSubscription = new LocationSubscription(provider, minTime, minDistance);
            }
            catch( Exception e ) {
                e.printStackTrace();
            }
        }

        // cleanup
        try {
            cursor.close();
        } catch( Exception e ) {
            // don't care
        }

        return locationSubscription;
    }

    public long getSingleSubscriptionID(LocationSubscription locationSubscription) {
        db = this.getWritableDatabase();

        // prepared statement for speed and security
        Cursor cursor = db.query(
                Tables[TABLE_LOCATION_SUBSCRIPTIONS], // table
                new String[]{ "ID" }, // columns (null == "*")
                "Provider=? AND MinTime=? AND MinDistance=?", // selection ('where' clause)
                new String[]{ // selection args
                        locationSubscription.getProvider(),
                        String.valueOf(locationSubscription.getMinTime()),
                        String.valueOf(locationSubscription.getMinDistance())
                },
                null, // group by
                null, // having
                "ID LIMIT 1" // order by
        );

        // try to get results and make a Subscription to return
        long id = -1;
        if (cursor.moveToFirst()) {
            try {
                // get ID from query
                id = cursor.getLong(0);
            }
            catch( Exception e ) {
                e.printStackTrace();
            }
        }

        // cleanup
        try {
            cursor.close();
        } catch( Exception e ) {
            // don't care
        }

        return id;
    }

    protected long insertSubscription( LocationSubscription locationSubscription) {
        ContentValues contentValues = new ContentValues();
        contentValues.put( "Provider", locationSubscription.getProvider() );
        contentValues.put( "MinTime", locationSubscription.getMinTime() );
        contentValues.put( "MinDistance", locationSubscription.getMinDistance() );

        return insertRecord(TABLE_LOCATION_SUBSCRIPTIONS, contentValues );
    }

    private long insertRecord( int tableId, ContentValues contentValues ) {
        long result = -1;
        db = this.getWritableDatabase();

        // attempt insert
        try {
            result = db.insert(Tables[tableId], null, contentValues);
        } catch( Exception e ) {
            e.printStackTrace();
        }

        // return result
        return result;
    }

    protected long deleteSubscription( LocationSubscription locationSubscription) {
        // android doesn't currently support the optional SQLite "Limit" directive for deleting records
        // so, we have to select a valid subscription ID and then delete that
        long id = getSingleSubscriptionID(locationSubscription);

        // if we didn't get a valid ID from the Select query, return
        if( id == -1 )
            return id;

        // we did get a valid ID from the Select query, delete it
        return deleteRecord(
                Tables[TABLE_LOCATION_SUBSCRIPTIONS],
                "ID=?",
                new String[]{ String.valueOf(id) });
    }

    protected long deleteAllSubscriptions() {
        return deleteRecord(
                Tables[TABLE_LOCATION_SUBSCRIPTIONS],
                null,   // no where clause
                null);  // no where args
    }

    private long deleteRecord( String table, String whereClause, String[] whereArgs ) {
        long result = -1;
        db = this.getWritableDatabase();

        // attempt delete
        try {
            result = db.delete(table, whereClause, whereArgs);
        } catch( Exception e ) {
            e.printStackTrace();
        }

        // return result
        return result;
    }
}
