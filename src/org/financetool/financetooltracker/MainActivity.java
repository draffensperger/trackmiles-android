package org.financetool.financetooltracker;

import java.util.List;

import android.os.Bundle;
import android.location.*;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
	private static final String DATABASE_NAME = "FTLOCATIONDB";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
    setContentView(R.layout.activity_main);
    
    final Button uploadButton = (Button) findViewById(R.id.uploadDataButton);
    uploadButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
        	
        	
        }
    });
    
    final Button showDataButton = (Button) findViewById(R.id.showDataButton);
    showDataButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
        	String data = getLocationDataCSV();
        	final TextView locationBox = (TextView) findViewById(R.id.locationBox);
        	locationBox.setText(data);
        }
    });
    
    if (!isServiceRunning(LocationLoggerService.class.getCanonicalName())) {
      Intent startServiceIntent = new Intent(this, 
      		LocationLoggerService.class);
      startService(startServiceIntent);
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
