package org.financetool.financetooltracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import android.app.ActivityManager;
import android.app.Service;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class TrackerService extends Service implements LocationListener {
	// Track locations every 5 seconds that are at least 5 meters apart	
	private static final long MIN_TIME_IN_MS_BTW_LOCS = 5 * 1000;	
	private static final float MIN_DIST_IN_M_BTW_LOCS = 5.0f;
	
	// Save in batches of 32 locations or after 20 minutes since the last 
	// location received.
	private static final int SAVE_BATCH_SIZE = 32;
	public static final long SAVE_IF_NO_LOC_RCVD_FOR_TIME_MS = 20 * 60 * 1000;
	
	public static String TAG = MainActivity.TAG; 
	
	private LocationManager lm;	
	private DBUtil db;
	private ServerUtil server;
	private PreferenceUtil prefs;
	
	private Location lastLocation;
	private LinkedBlockingDeque<Location> locationsToSave;
	private static final Location SAVE_NOW_SIGNAL = new Location("");
	private boolean keepWaitingForLocationsToSave = true;

	public static void bindAndStartIfUnstarted(Context c, ServiceConnection sc) {	
		c.bindService(start(c), sc, 0);
	}
	
	public static Intent start(Context c) {
		Intent intent = new Intent(c, TrackerService.class);
		if (!isRunning(c)) {
			c.startService(intent);
		}
		return intent;
	}
	
	public static boolean isRunning(Context c) {
		String className = TrackerService.class.getCanonicalName();
		ActivityManager manager 
			= (ActivityManager) c.getSystemService(ACTIVITY_SERVICE);	    
		for (RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (className.equals(info.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	    return START_STICKY;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		prefs = new PreferenceUtil(getApplicationContext());	
		db = new DBUtil(getApplicationContext());
		server = new ServerUtil(getApplicationContext());
		
		locationsToSave = new LinkedBlockingDeque<Location>();		
		
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		updateTrackingFromPrefs();
        
		new SaveAndUploadThread().start();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		sendThreadDoneSignal();
		lm.removeUpdates(this);
	}
	
	private void sendThreadDoneSignal() {
		keepWaitingForLocationsToSave = false;
		locationsToSave.offer(SAVE_NOW_SIGNAL);
	}
	
	private boolean shouldRecordLocation(Location l) {
		// Record it if it's the first location, it is a more accurate location
		// or it has been enough time and distance since the last one.
		return
			lastLocation == null
			|| (lastLocation.getProvider().equals(LocationManager.NETWORK_PROVIDER)
				&& (l.getProvider().equals(LocationManager.GPS_PROVIDER)
					|| l.getProvider().equals(LocationManager.PASSIVE_PROVIDER)))
			|| (System.currentTimeMillis() - lastLocation.getTime() 
					>= MIN_TIME_IN_MS_BTW_LOCS
				&& lastLocation.distanceTo(l) >=  MIN_DIST_IN_M_BTW_LOCS);
	}
	
	public void onLocationChanged(Location loc) {
		if (shouldRecordLocation(loc)) {
			lastLocation = loc;
			locationsToSave.offer(loc);
		}
	}
	public void onProviderDisabled(String provider) {
	}
	public void onProviderEnabled(String provider) {
	}
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}
	
	private void requestLocations(String provider, long minTime) {
		lm.requestLocationUpdates(provider, minTime, 
				MIN_DIST_IN_M_BTW_LOCS, this);
	}
	
	private void updateTrackingFromPrefs() {
		lm.removeUpdates(this);
		if (prefs.shouldTrackLocation()) {
			requestLocations(LocationManager.NETWORK_PROVIDER, 
					MIN_TIME_IN_MS_BTW_LOCS);
			if (prefs.shouldUseGPS()) {
				requestLocations(LocationManager.GPS_PROVIDER, 0);
			} else {
				requestLocations(LocationManager.PASSIVE_PROVIDER, 0);
			}
		}				
	}
			
	private final IBinder binder = new LocalBinder();
	
	public class LocalBinder extends Binder {
		public void uploadNow() {	
		}
		public void updateTrackingFromPrefs() {
			TrackerService.this.updateTrackingFromPrefs();
		}
    }
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	private class SaveAndUploadThread extends Thread {		
		@Override
		public void run() {
			while (keepWaitingForLocationsToSave) {
				uploadOrSaveLocations(collectLocationsToSave());											
			}		
			db.closeDB();
		}
		
		private ArrayList<Location> collectLocationsToSave() {
			ArrayList<Location> locs = new ArrayList<Location>();
			do {				
				try {
					Location l = locationsToSave.poll(SAVE_IF_NO_LOC_RCVD_FOR_TIME_MS, 
							TimeUnit.MILLISECONDS);
					
					if (l == null) {
						// If we have passed the maximum time between uploads
						// then return if we have locations to save, otherwise
						// keep waiting.
						if (!locs.isEmpty()) return locs;
					} else if (l == SAVE_NOW_SIGNAL) {
						return locs;
					} else {
						locs.add(l);
					}
				} catch (InterruptedException e) {
					Log.e(TAG, e.toString(), e);
				}
			} while (locs.size() < SAVE_BATCH_SIZE);
			
			return locs;
		}		

		private void uploadOrSaveLocations(ArrayList<Location> locs) {
			if (!server.isNetworkAvailable()) {
				saveLocationsToDB(locs);
			} else {				
				Collection<Location> toUpload = db.getSavedLocations();
				boolean hadSavedLocations = toUpload.size() > 0;
				toUpload.addAll(locs);
				if (server.uploadLocations(toUpload)) {
					if (hadSavedLocations) {
						db.clearSavedLocations();
					}
				} else {
					saveLocationsToDB(locs);
				}
			}
		}
		
		private void saveLocationsToDB(ArrayList<Location> locs) {
			for (Location l : locs) {
				db.saveLocation(l);
			}
		}
	}
}
