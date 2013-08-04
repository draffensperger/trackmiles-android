package org.financetool.financetooltracker;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.location.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.auth.*;
import com.google.android.gms.common.GooglePlayServicesUtil;

public class MainActivity extends PreferenceActivity {
	private static final String DATABASE_NAME = "FTLOCATIONDB";	
	private static final int AUTH_REQUEST_CODE = 1;
	private LocationLoggerService.LocalBinder locationLoggerBinder = null;
	private ServiceConnection locationLoggerServiceConn = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	    
		final Button uploadButton = (Button) findViewById(R.id.sync_button);
		uploadButton.setOnClickListener(new View.OnClickListener() {
	        public void onClick(View v) {
	        	
	        	
	        }
	    });
		
		final TextView info_label = (TextView) findViewById(R.id.info_label);
		info_label.setMovementMethod(LinkMovementMethod.getInstance());
		
		addPreferencesFromResource(R.xml.preferences);
		
		/*
		 * 
		 * 
		For login:
		new Intent(getBaseContext(), AuthActivity.class),
	        			AUTH_REQUEST_CODE);
		 */	   
	    	
	    startLocationLogger();
	}
	
	private void startLocationLogger() {
		Intent serviceIntent = new Intent(this, LocationLoggerService.class);
		
		if (!isServiceRunning(LocationLoggerService.class.getCanonicalName())) {
	      startService(serviceIntent);
	    }
	    
		locationLoggerServiceConn = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName className, 
					IBinder service) {
				
				locationLoggerBinder = 
						((LocationLoggerService.LocalBinder)service);
			}
			public void onServiceDisconnected(ComponentName className) {
				locationLoggerBinder = null;
			}			
		};
		
		bindService(serviceIntent, locationLoggerServiceConn, 0);
	}
	
	@Override
	protected void onDestroy() {
	    super.onDestroy();
	    if (locationLoggerServiceConn != null) {
	    	unbindService(locationLoggerServiceConn);
	    }
	}
	
	protected void onActivityResult(final int requestCode, final int resultCode,
	         final Intent data) {
	     if (requestCode == AUTH_REQUEST_CODE && resultCode == RESULT_OK) {	    	 
	         if (locationLoggerBinder != null) {
	        	 locationLoggerBinder.setAuthInfo(
	        			 new Account(data.getStringExtra("accountName"),
	        					 	 data.getStringExtra("accountType")),
	        			 data.getStringExtra("authToken"));
	         } else {
	        	 // TODO: Handle situation where for some reason the service
	        	 // hasn't been started yet or is null.
	         }
	     }
	 }
	
	private String getLocationDataCSV() {
		StringBuffer buf = new StringBuffer();
		
		try {
			SQLiteDatabase db;
			db = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.OPEN_READWRITE, null);
			Cursor c = db.rawQuery(
					"SELECT GMTTIMESTAMP,PROVIDER,LATITUDE,LONGITUDE,ALTITUDE,ACCURACY,"
						+ "SPEED,BEARING FROM LOCATIONS ORDER BY GMTTIMESTAMP DESC", null);
			if (c != null) {
	      while (c.moveToNext()) {
	      	buf.append(c.getString(0).replace("'", "''"));
	      	buf.append(",");
	      	buf.append(c.getString(1).replace("'", "''"));
	      	buf.append(",");
	      	buf.append(c.getDouble(2));
	      	buf.append(",");
	      	buf.append(c.getDouble(3));
	      	buf.append(",");
	      	buf.append(c.getDouble(4));
	      	buf.append(",");
	      	buf.append(c.getDouble(5));
	      	buf.append(",");
	      	buf.append(c.getDouble(6));
	      	buf.append(",");
	      	buf.append(c.getDouble(7));
	      	buf.append("\r\n");
	      }
	      c.close();
			}
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	private boolean isServiceRunning(String className) {
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (className.equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}	
}
