package org.financetool.financetooltracker;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.financetool.financetooltracker.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.ActivityManager;
import android.app.Service;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

public class TrackerService extends Service implements LocationListener {
	private static final String DATABASE_NAME = "FTLOCATIONDB";
	private static final String LOCATION_UPLOAD_URL 
		= "https://trackmiles.herokuapp.com/api/v1/locations/bulk_create";
	private final DateFormat timestampFormat = new SimpleDateFormat("yyyyMMddHHmmss");
	
	private LocationManager lm;
	private LocationListener locationListener;
	
	private LinkedBlockingDeque<Location> locationsToSave;		
	private boolean keepWaitingForLocations = false;	
	private TrackerService outerThisForThread = this;
	
	private long lastSavedLocationTime = 0;
	private String lastLocationProvider;
	//private long timeBetweenLocations = 8000;
	//private long timeBetweenUploads = 60 * 60 * 1000;
	private long timeBetweenLocations = 1000;
	private long timeBetweenSaves = 1000;
	private long timeBetweenUploads = 1000;
	
	private PreferenceUtil prefs;
	
	public static void startAndBind(Context context, ServiceConnection conn) {
		Intent intent = new Intent(context, TrackerService.class);
		if (!isRunning(context)) {
			context.startService(intent);
		}
		context.bindService(intent, conn, 0);
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
				
				//yield();
				try {
					sleep(timeBetweenSaves);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			saveLocationsFromQueue();
			uploadSavedLocations();
			
			db.close();
		}
		
		private void uploadSavedLocations() {
			// TODO: Implement the actual upload code, delete from the SQLite DB.
			// TODO: Only upload if in Wifi?						
			
			String authToken = prefs.getAuthToken();
			if (authToken != null) {								
				try {
					JSONArray locsJSON = getSavedLocationsJSON();
					if (locsJSON.length() > 0) {					
						JSONObject json = new JSONObject();
						json.put("google_token", authToken);					
						json.put("locations", locsJSON);
						lastUploadedTime = System.currentTimeMillis();
						
						DefaultHttpClient client = new DefaultHttpClient();
						HttpPost post = new HttpPost(LOCATION_UPLOAD_URL);
						
						post.setEntity(new ByteArrayEntity(json.toString().getBytes("UTF8")));
						post.setHeader("Content-Type", "application/json");
						HttpResponse response = client.execute(post);	
						int code = response.getStatusLine().getStatusCode();
						if (code == HttpStatus.SC_OK) {						
							String result = EntityUtils.toString(response.getEntity());						
							JSONObject jsonResult = new JSONObject(result);
							long numCreated = jsonResult.getLong("num_created_locations");
							if (numCreated == locsJSON.length()) {
								clearSavedLocations();
							}
						} else if (code == HttpStatus.SC_UNAUTHORIZED) {
							// TODO: Try to get the auth token reset
						} else {
							// TODO: Do some error logging of other messages
						}
					}					
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ClientProtocolException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}				
			}
		}
		
		private void clearSavedLocations() {
			try {
				db = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.OPEN_READWRITE, null);			
				db.execSQL("DELETE FROM LOCATIONS");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		private JSONArray getSavedLocationsJSON() {			
			JSONArray locations = new JSONArray();
			
			try {
				SQLiteDatabase db;
				db = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.OPEN_READWRITE, null);
				Cursor c = db.rawQuery(
						"SELECT GMTTIMESTAMP,PROVIDER,LATITUDE,LONGITUDE,ALTITUDE,ACCURACY,"
							+ "SPEED,BEARING FROM LOCATIONS ORDER BY GMTTIMESTAMP DESC", null);
				if (c != null) {
					while (c.moveToNext()) {
						JSONObject json = new JSONObject();
						json.put("recorded_time", c.getString(0));
						json.put("provider", c.getString(1));						
						addDoubleIfNotNull(c, 2, "latitude", json);
						addDoubleIfNotNull(c, 3, "longitude", json);
						addDoubleIfNotNull(c, 4, "altitude", json);
						addDoubleIfNotNull(c, 5, "accuracy", json);
						addDoubleIfNotNull(c, 6, "speed", json);
						addDoubleIfNotNull(c, 7, "bearing", json);
						locations.put(json);
					}
					c.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return locations;
		}
		
		private void addDoubleIfNotNull(Cursor c, int column, 
				String name, JSONObject json) throws JSONException {
			if (!c.isNull(column)) {
				json.put(name, c.getDouble(column));
			}
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
