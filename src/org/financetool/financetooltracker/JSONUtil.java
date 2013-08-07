package org.financetool.financetooltracker;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Collection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.location.Location;
import android.util.Log;

public class JSONUtil {	
	public static String TAG = MainActivity.TAG; 
	
	public static JSONArray getLocationsAsJSON(Collection<Location> locs) {
		JSONArray json = new JSONArray();
		try {
			for (Location l: locs) {			
					json.put(locationToJSON(l));			
			}
		} catch (JSONException e) {
			Log.e(TAG, e.toString(), e);
		}
		return json;
	}
	
	private static JSONObject locationToJSON(Location l) throws JSONException {
		JSONObject json = new JSONObject();
		json.put("provider", l.getProvider());
		json.put("recorded_time", formatTime(l.getTime()));
		json.put("latitude", l.getLatitude());
		json.put("longitude", l.getLongitude());
		if (l.hasAltitude()) json.put("altitude", l.getAltitude());
		if (l.hasAccuracy()) json.put("accuracy", l.getAccuracy());
		if (l.hasSpeed()) json.put("speed", l.getSpeed());
		if (l.hasBearing()) json.put("bearing", l.getBearing());		
		return json;
	}
	
	private static String formatTime(long time) {			
		return new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(time);
	}	
}
