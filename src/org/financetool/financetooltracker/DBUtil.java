package org.financetool.financetooltracker;

import java.util.ArrayList;
import java.util.ArrayList;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;

public class DBUtil {
	private SQLiteDatabase db;
	
	public DBUtil(Context context) {
		db = new DBOpenHelper(context).getWritableDatabase();
	}
			
	public void saveLocation(Location loc) {
		db.execSQL(
			"INSERT INTO locations (time,provider,latitude," +
					"longitude,altitude,accuracy,speed,bearing) " +
			"VALUES (?,?,?,?,?,?,?,?)",
			new Object[] {
			loc.getTime(), 
			loc.getProvider(), 
			loc.getLatitude(),
			loc.getLongitude(),
			loc.hasAccuracy() ? loc.getAltitude() : null,
			loc.hasAccuracy() ? loc.getAccuracy() : null,
			loc.hasSpeed() ? loc.getSpeed() : null,
			loc.hasBearing() ? loc.getBearing() : null					
			}
		);
	}
	
	public int getNumSavedLocations() {
		Cursor c = db.rawQuery("SELECT COUNT(*) FROM locations", null);
		if (c != null && c.moveToFirst()) {
			return c.getInt(0);
		} else {
			return -1;
		}		
	}
	
	public ArrayList<Location> getSortedLocationsBatch(int batchSize) { 
		return getLocationsForQuery(
				"SELECT provider,time,latitude,longitude,altitude," +
				"accuracy,speed,bearing FROM locations " +
				"ORDER BY time LIMIT ?",
				new String[] {Integer.toString(batchSize)});
	}
	
	public ArrayList<Location> getSavedLocations() {
		return getLocationsForQuery(
			"SELECT provider,time,latitude,longitude,altitude," +
			"accuracy,speed,bearing FROM locations", null);		
	}
	
	private ArrayList<Location> getLocationsForQuery(String sql, String[] args) {
		ArrayList<Location> locs = new ArrayList<Location>();
		Cursor c = db.rawQuery(sql, args);
		if (c != null) {
			while (c.moveToNext()) {
				int i = 0;
				Location l = new Location(c.getString(i++));
				l.setTime(c.getLong(i++));
				l.setLatitude(c.getDouble(i++));
				l.setLongitude(c.getDouble(i++));
				if (!c.isNull(i)) l.setAltitude(c.getDouble(i++));
				if (!c.isNull(i)) l.setAccuracy(c.getFloat(i++));
				if (!c.isNull(i)) l.setSpeed(c.getFloat(i++));
				if (!c.isNull(i)) l.setBearing(c.getFloat(i++));					
				locs.add(l);
			}
			c.close();
		}
		return locs;	
	}
	
	public void clearLocationsUpTo(long maxTime) {
		db.execSQL("DELETE FROM LOCATIONS WHERE time <= ?", 
					new String[] {Long.toString(maxTime)});
	}
	
	public void clearSavedLocations() {						
		db.execSQL("DELETE FROM LOCATIONS");	
	}
	
	public void closeDB() {
		db.close();
	}
	
	private class DBOpenHelper extends SQLiteOpenHelper {
		private static final int DATABASE_VERSION = 1;
		private static final String DATABASE_NAME = "FTLOCATIONDB";
		
		public DBOpenHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE IF NOT EXISTS locations " + 
					"(time INTEGER, provider VARCHAR, latitude REAL," +
					"longitude REAL, altitude REAL, accuracy REAL, speed REAL, " + 
					"bearing REAL)");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {			
		}
		
	}
}
