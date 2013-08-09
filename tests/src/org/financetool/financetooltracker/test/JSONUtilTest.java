package org.financetool.financetooltracker.test;

import java.util.ArrayList;

import org.financetool.financetooltracker.JSONUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.location.Location;
import junit.framework.TestCase;

public class JSONUtilTest extends TestCase {
	protected Location l1;
	protected Location l2;
	protected JSONUtil jsonUtil;
	protected String l1json;
	protected String l2json;
		
	protected long FIRST_S_OF_2000_UTC = (365L*30L + 7L)*24L*60L*60L*1000L;
	protected long LAST_S_OF_1999_UTC = FIRST_S_OF_2000_UTC - 1000L;
	
	protected void setUp() {
		l1 = new Location("network");
		l1.setTime(LAST_S_OF_1999_UTC);
		l1.setLatitude(56.3321600402007013);
		l1.setLongitude(78.3321600402007013);
		
		l1json = "{provider:\"network\",time:2000010158023," +
				"latitude:\"56.3321600402007013\",longitude:\"78.3321600402007013\"}";
		
		l2 = new Location("gps");
		l2.setTime(FIRST_S_OF_2000_UTC);
		l2.setLatitude(33.12345678);
		l2.setLongitude(39.12345678);
		l2.setSpeed(42.32f);
		l2.setAccuracy(11.0f);
		l2.setBearing(42.0f);
		l2.setAltitude(139.389042129);
		
		l2json = "";
		
		jsonUtil = new JSONUtil();		
	}
	
	public void testLocationToJSON() throws JSONException {		
		double dDouble = 0.0000000000001;
		double dFloat = 0.00000001;
		
		JSONObject json = jsonUtil.locationToJSON(l1);
		assertEquals(4, json.length());
		assertEquals(l1.getProvider(), json.getString("provider"));
		assertEquals("19991231235959", json.getString("recorded_time"));
		assertEquals(l1.getLatitude(), json.getDouble("latitude"), dDouble);
		assertEquals(l1.getLongitude(), json.getDouble("longitude"), dDouble);				
		
		json = jsonUtil.locationToJSON(l2);
		assertEquals(8, json.length());
		assertEquals(l2.getProvider(), json.getString("provider"));
		assertEquals("20000101000000", json.getString("recorded_time"));		
		assertEquals(l2.getLatitude(), json.getDouble("latitude"), dDouble);
		assertEquals(l2.getLongitude(), json.getDouble("longitude"), dDouble);
		assertEquals(l2.getSpeed(), json.getDouble("speed"), dFloat);
		assertEquals(l2.getAccuracy(), json.getDouble("accuracy"), dFloat);
		assertEquals(l2.getBearing(), json.getDouble("bearing"), dFloat);
		assertEquals(l2.getAltitude(), json.getDouble("altitude"), dDouble);
		
	}
	
	public void testGetLocationsAsJSON() {
		ArrayList<Location> locs = new ArrayList<Location>();
		locs.add(l1);
		locs.add(l2);
		JSONArray json = jsonUtil.getLocationsAsJSON(locs);
		assertEquals(2, json.length());		
	}
}
