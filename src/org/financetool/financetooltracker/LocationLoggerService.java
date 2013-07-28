package org.financetool.financetooltracker;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingDeque;

import org.financetool.financetooltracker.R;

import android.accounts.Account;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

public class LocationLoggerService extends Service implements LocationListener {
	public static final String DATABASE_NAME = "FTLOCATIONDB";
	private final DateFormat timestampFormat = new SimpleDateFormat("yyyyMMddHHmmss");
	
	private LocationManager lm;
	private LocationListener locationListener;
	
	private LinkedBlockingDeque locationsToSave;		
	private boolean keepWaitingForLocations = false;	
	private LocationLoggerService outerThisForThread = this;
	
	private long lastSavedLocationTime = 0;
	private String lastLocationProvider;
	private long timeBetweenLocations = 8000;
	private long timeBetweenUploads = 60 * 60 * 1000;
	
	private Account authAccount = null;
	private String authToken = null;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	    // We want this service to continue running until it is explicitly
	    // stopped, so return sticky.
	    return START_STICKY;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();				
		locationsToSave = new LinkedBlockingDeque(500);				
		
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		//lm.requestLocationUpdates(lm.PASSIVE_PROVIDER, timeBetweenLocations, 5.0f, this);
		lm.requestLocationUpdates(lm.GPS_PROVIDER, 0, 5.0f, this);
		lm.requestLocationUpdates(lm.NETWORK_PROVIDER, timeBetweenLocations, 5.0f, this);
    
		keepWaitingForLocations = true;    
		new SaveAndUploadThread().start();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();		
		keepWaitingForLocations = false;
		lm.removeUpdates(locationListener);
	}
	
	public void onLocationChanged(Location loc) {
		long time = System.currentTimeMillis();
		if ((time - lastSavedLocationTime > timeBetweenLocations) ||
				loc.getProvider() != lastLocationProvider) {
			
			lastSavedLocationTime = time;
			lastLocationProvider = loc.getProvider();
			locationsToSave.offer(loc);
		}		
	}
	public void onProviderDisabled(String provider) {
	}
	public void onProviderEnabled(String provider) {
	}
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}
			
	private final IBinder binder = new LocalBinder();
	
	public class LocalBinder extends Binder {
		public void setAuthInfo(Account account, String token) {
			LocationLoggerService.this.authAccount = account;
			LocationLoggerService.this.authToken = token;
		}
    }
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	private class SaveAndUploadThread extends Thread {
		private SQLiteDatabase db;
		
		private long lastUploadedTime = 0;
		private ArrayList<Location> locsBuffer = new ArrayList();
		
		@Override
		public void run() {
			db = outerThisForThread.openOrCreateDatabase(DATABASE_NAME, 
					SQLiteDatabase.OPEN_READWRITE, null);
			db.execSQL("DROP TABLE IF EXISTS LOCATIONS;");
			db.execSQL("CREATE TABLE IF NOT EXISTS LOCATIONS " + 
					"(GMTTIMESTAMP VARCHAR, PROVIDER VARCHAR, LATITUDE REAL," +
					"LONGITUDE REAL, ALTITUDE REAL, ACCURACY REAL, SPEED REAL, " + 
					"BEARING REAL);");
						
			while (keepWaitingForLocations) {
				saveLocationsFromQueue();
				
				if (System.currentTimeMillis() - lastUploadedTime > timeBetweenUploads) {
					uploadSavedLocations();
				}
				
				yield();
			}
			saveLocationsFromQueue();
			uploadSavedLocations();
			
			db.close();
		}
		
		private void uploadSavedLocations() {
			// TODO: Implement the actual upload code, delete from the SQLite DB.
			
			// TODO: Only upload if in Wifi?
			
			
			
			lastUploadedTime = System.currentTimeMillis();
		}
		
		private void saveLocationsFromQueue() {
			locationsToSave.drainTo(locsBuffer);
			
			for (Location loc: locsBuffer) {
				saveLocation(loc);
			}
			locsBuffer.clear();
		}
		
		private void saveLocation(Location loc) {
			try {
					GregorianCalendar greg = new GregorianCalendar();
					TimeZone tz = greg.getTimeZone();
					int offset = tz.getOffset(System.currentTimeMillis());
					greg.add(Calendar.SECOND, (offset/1000) * -1);
					StringBuffer queryBuf = new StringBuffer();
					queryBuf.append("INSERT INTO LOCATIONS " +
							"(GMTTIMESTAMP,PROVIDER,LATITUDE,LONGITUDE,ALTITUDE,ACCURACY," +
							"SPEED,BEARING) VALUES (" +
							"'"+timestampFormat.format(greg.getTime())+"',"+
							"'"+loc.getProvider()+"',"+
							loc.getLatitude()+","+
							loc.getLongitude()+","+
							(loc.hasAltitude() ? loc.getAltitude() : "NULL")+","+
							(loc.hasAccuracy() ? loc.getAccuracy() : "NULL")+","+
							(loc.hasSpeed() ? loc.getSpeed() : "NULL")+","+
							(loc.hasBearing() ? loc.getBearing() : "NULL")+");");				
					db.execSQL(queryBuf.toString());
			} catch (Exception e) {
				Log.e("Error saving location for FinanceTool Tracker: ", e.toString());
			}
		}
	}
}
