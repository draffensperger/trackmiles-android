package org.financetool.financetooltracker;

import java.io.IOException;
import java.util.Collection;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableNotifiedException;

import android.content.Context;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class ServerUtil {
	public static String TAG = MainActivity.TAG; 
	
	private PreferenceUtil prefs; 
	private String apiBaseURL;
	private Context context;
	
	public ServerUtil(Context context) {
		prefs = new PreferenceUtil(context);
		apiBaseURL = context.getString(R.string.api_base_url);
		this.context = context;
	}
	
	public boolean uploadLocations(Collection<Location> locs) {
		try {
			if (locs.size() > 0) {
				JSONObject arg = new JSONObject();			
					arg.put("locations", JSONUtil.getLocationsAsJSON(locs));			
				JSONObject json = callAPI("locations/bulk_create", arg);
				if (json != null) {
					return json.getLong("num_created_locations") == locs.size();					
				}
			}
		} catch (JSONException e) {	
			Log.e(TAG, e.toString(), e);
		}
		return false;
	}
	
	private JSONObject callAPI(String cmd, JSONObject arg) {
		return callAPI(cmd, arg, true);
	}
	
	private JSONObject callAPI(String cmd, JSONObject arg, boolean retryIfUnauthorized) {
		if (!isNetworkAvailable()) {
			return null;
		}
		if (!prefs.isAuthAccountConnected()) {
			return null;
		}
		
		String authToken = prefs.getAuthToken();				
		if (authToken == null) {
			if (requestNewAuthToken() != null && retryIfUnauthorized) {
				return callAPI(cmd, arg, false);
			} else {
				return null;
			}
		}
										
		try {									
			arg.put("google_token", authToken);										
			
			DefaultHttpClient client = new DefaultHttpClient();
			HttpPost post = new HttpPost(apiBaseURL + cmd);
			
			post.setEntity(new ByteArrayEntity(arg.toString().getBytes("UTF8")));
			post.setHeader("Content-Type", "application/json");
			HttpResponse response = client.execute(post);	
			int code = response.getStatusLine().getStatusCode();
			String result = EntityUtils.toString(response.getEntity());
			if (code == HttpStatus.SC_OK) {																
				return new JSONObject(result);						
			} else if (code == HttpStatus.SC_UNAUTHORIZED) {
				if (requestNewAuthToken() != null && retryIfUnauthorized) {
					return callAPI(cmd, arg, false);
				}
			} else {
				String msg = "Bad server response, code: " + code + 
						", response: " + result + ", cmd: " + cmd + 
						", arg: " + arg.toString();
				Log.e(TAG, msg);
			}								
		} catch (JSONException e) {
			Log.e(TAG, e.toString(), e);
		} catch (ClientProtocolException e) {
			Log.e(TAG, e.toString(), e);
		} catch (IOException e) {
			Log.e(TAG, e.toString(), e);
		}
		
		return null;
	}
	
	private boolean isNetworkAvailable() {
		NetworkInfo netInfo = 
			((ConnectivityManager) 
				context.getSystemService(Context.CONNECTIVITY_SERVICE))
	     	.getActiveNetworkInfo();
	    return netInfo != null && netInfo.isConnected();
	}
	
	private String requestNewAuthToken() {
		String token = prefs.getAuthToken();		
		GoogleAuthUtil.invalidateToken(context, token);
		token = null;
		try {
			token = GoogleAuthUtil.getTokenWithNotification(context, 
					prefs.getAuthAccount().name, 
					context.getString(R.string.auth_scope), null);
		} catch (UserRecoverableNotifiedException e) {
		    // Notification has already been pushed, continue wo/ token.
		} catch (IOException e) {
			 Log.i(TAG, "Transient auth error: " + e.getMessage(), e);
		} catch (GoogleAuthException e) {
			Log.e(TAG, "Unrecoverable auth exception: " + e.getMessage(), e);
		}
		prefs.setAuthToken(token);
		return token;
	}
}
