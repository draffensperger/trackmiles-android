package org.financetool.financetooltracker;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;

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
	public static String TAG = MainActivity.TAG; 
	
	private LocationManager lm;	
	private DBUtil db;
	private ServerUtil server;
	private PreferenceUtil prefs;
	
	private LinkedBlockingDeque<Location> locationsToSave;		
	private boolean keepWaitingForLocations = false;	
	private TrackerService outerThisForThread = this;
	
	private long lastSavedLocationTime = 0;
	private String lastLocationProvider;
	
	// TODO: Move these to the configuration file	
	private long timeBetweenLocations = 15 * 1000;
	private float distanceBetweenLocations = 25.0f; 	
	private long timeBetweenSaves = 60 * 1000;
	private long timeBetweenUploads = 15 * 60 * 1000;	
	
	public static void startAndBind(Context context, ServiceConnection conn) {		
		context.bindService(start(context), conn, 0);
	}
	
	public static Intent start(Context context) {
		Intent intent = new Intent(context, TrackerService.class);
		if (!isRunning(context)) {
			context.startService(intent);
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
	    // We want this service to continue running until it is explicitly
	    // stopped, so return sticky.
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
    
		keepWaitingForLocations = true;    
		new SaveAndUploadThread().start();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();		
		keepWaitingForLocations = false;
		lm.removeUpdates(this);
	}
	
	public void onLocationChanged(Location loc) {
		long time = System.currentTimeMillis();
		if ((time - lastSavedLocationTime >= timeBetweenLocations) ||
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
	
	private void requestLocations(String provider, long minTime) {
		lm.requestLocationUpdates(provider, minTime, 
				distanceBetweenLocations, this);
	}
	
	private void updateTrackingFromPrefs() {
		lm.removeUpdates(this);
		if (prefs.shouldTrackLocation()) {
			requestLocations(LocationManager.NETWORK_PROVIDER, 
					timeBetweenLocations);
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
		private long lastUploadedTime = 0;
		private ArrayList<Location> locsBuffer = new ArrayList<Location>();
		
		@Override
		public void run() {						
			while (keepWaitingForLocations) {
				saveLocationsFromQueue();
				
				if (System.currentTimeMillis() - lastUploadedTime > timeBetweenUploads) {					
					if (server.uploadLocations(db.getSavedLocations())) {
						db.clearSavedLocations();
						lastUploadedTime = System.currentTimeMillis();
					}
				}
				
				yield();
				try {
					sleep(timeBetweenSaves);
				} catch (InterruptedException e) {
					Log.e(TAG, e.toString(), e);
				}
			}
			saveLocationsFromQueue();
			server.uploadLocations(db.getSavedLocations());			
			db.closeDB();
		}	
		
		private void saveLocationsFromQueue() {
			locationsToSave.drainTo(locsBuffer);			
			for (Location loc: locsBuffer) {
				db.saveLocation(loc);
			}
			locsBuffer.clear();
		}		
	}
}
