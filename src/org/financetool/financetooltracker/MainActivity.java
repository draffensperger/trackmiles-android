package org.financetool.financetooltracker;

import java.util.List;

import android.os.Bundle;
import android.location.*;
import android.content.Context;
import android.app.Activity;
import android.view.Menu;
import android.widget.TextView;

public class MainActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
        
    LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
    List<String> providers = lm.getAllProviders();
    
    long minTime = 1000;
    float minDistance = 1.0f;
        
    LocationListener listener = new UpdateTextFieldLocationListener();
        
    for (String provider: providers) {
    	lm.requestLocationUpdates(provider, minTime, minDistance, listener);
    }
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	class UpdateTextFieldLocationListener implements LocationListener {
		public void onLocationChanged(Location location) {
			TextView textView = (TextView) findViewById(R.id.locationBox);
	    textView.setText(location.toString());
		}

		public void onProviderEnabled(String provider) {

		}

		public void onProviderDisabled(String provider) {

		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			// TODO Auto-generated method stub

		}
	}
}
