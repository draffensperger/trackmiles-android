package org.financetool.financetooltracker;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.location.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.auth.*;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.GooglePlayServicesUtil;

public class MainActivity extends PreferenceActivity implements 
	SharedPreferences.OnSharedPreferenceChangeListener {
	
	public static String TAG = "Mile Tracker";
	
	private TrackerService.LocalBinder trackerBinder = null;
	private ServiceConnection trackerConn = null;
	private PreferenceUtil prefs;
	private CheckBoxPreference connected;

	private static final int CHOOSE_ACCOUNT = 1;
	private static final int AUTH_RECOVERY = 2;
	private static final int AUTH_ERROR_DIALOG = 3;	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		prefs = new PreferenceUtil(getApplicationContext());

		final TextView info_label = (TextView) findViewById(R.id.info_label);
		info_label.setMovementMethod(LinkMovementMethod.getInstance());

		final Button uploadButton = (Button) findViewById(R.id.sync_button);
		uploadButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				trackerBinder.uploadNow();
			}
		});

		addPreferencesFromResource(R.xml.preferences);
				
		
		prefs.addListener(this);
		
		connected = (CheckBoxPreference) findPreference("connected");
		connected.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference pref, Object value) {
				if ((Boolean) value) {
					showChooseAccountActivity();	
				} else {
					showLogoutDialog();
				}
				return false;
			}
		});
		updateConnectedDescription();

		bindTrackerStartIfUnstarted();
	}
	
	protected void onStart() {
		super.onStart();
	}
	
	protected void onStop() {
		super.onStop();
		
		// If the user hasn't logged in yet, the app may be stopped 
		// because they are selecting an account in which case we shouldn't
		// finish the app. Otherwise, finish the app when the user clicks away.
		if (prefs.getAuthToken() != null) {
			finish();
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	
	private void bindTrackerStartIfUnstarted() {				
		trackerConn = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName cn, IBinder service) {
				trackerBinder = ((TrackerService.LocalBinder) service);
			}
			public void onServiceDisconnected(ComponentName className) {
				trackerBinder = null;
			}
		};
		TrackerService.bindAndStartIfUnstarted(this, trackerConn);
	}	
	
	private void unbindTracker() {
		if (trackerConn != null) {
			try {
				unbindService(trackerConn);
				trackerConn = null;
			} catch (Exception e) {
				Log.e(TAG, e.toString(), e);
			}
		}
	}	
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs,
			String key) {
		if (key.equals("track_location") || key.equals("use_gps")) {
			if (trackerBinder != null) {
				trackerBinder.updateTrackingFromPrefs();
			}
		}
	}
	
	private void showLogoutDialog() {
		DialogInterface.OnClickListener yes = new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		    	prefs.setAuthToken(null);
		    	updateConnectedDescription();
		    }
		};
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.logout_title))
			.setMessage(getString(R.string.logout_message))
			.setPositiveButton(getString(R.string.logout_yes), yes)
		    .setNegativeButton(getString(R.string.logout_no), null)
		    .show();
	}
	
	void updateConnectedDescription() {
		boolean hasAuthToken = prefs.getAuthToken() != null; 
		connected.setChecked(hasAuthToken);
		if (hasAuthToken) {
			connected.setTitle(getString(R.string.ui_connected_label_with_account));
			connected.setSummary(
					String.format(getString(R.string.ui_connected_with_account), 
							prefs.getAuthAccount().name));
		} else {
			connected.setTitle(getString(R.string.ui_connected_label));
			connected.setSummary(getString(R.string.ui_connected_desc));
		}
	}
	
	void updateConnectedLoggingIn() {
		connected.setTitle(getString(R.string.ui_connected_label_logging_in));
		connected.setSummary(
			String.format(getString(R.string.ui_connected_logging_in), 
						prefs.getAuthAccount().name));
	}
	
	private void showChooseAccountActivity() {
		Intent intent = AccountPicker.newChooseAccountIntent(
				prefs.getAuthAccountOrDefault(),
				null, new String[] { GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE },
				false, getString(R.string.ui_account_picker_description),
				null, null, null);
		startActivityForResult(intent, CHOOSE_ACCOUNT);
	}
	
	private void showLoginTempErrorDialog() {
		new AlertDialog.Builder(this)
			.setMessage(getString(R.string.login_err_temp_msg))
			.setTitle(getString(R.string.login_err_temp_title))
			.setPositiveButton(getString(R.string.ok), null)
			.setCancelable(true)
			.create().show();
	}
	
	private void showLoginFetalErrorDialog() {
		new AlertDialog.Builder(this)
			.setMessage(getString(R.string.login_err_fetal_msg))
			.setTitle(getString(R.string.login_err_fetal_title))
			.setPositiveButton(getString(R.string.ok), null)
			.setCancelable(true)
			.create().show();
	}

	protected void onActivityResult(int request, int result, Intent data) {
		switch (request) {
		case CHOOSE_ACCOUNT:
			if (result == RESULT_OK) {
				Account account = new Account(
						data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME), 
						data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE));
				prefs.setAuthAccount(account);
				updateConnectedLoggingIn();
				new AuthTask().execute();
			}
			break;
		case AUTH_RECOVERY:
			if (result == RESULT_OK) {
				updateConnectedLoggingIn();
				new AuthTask().execute();
			}
			break;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private class AuthTask extends AsyncTask<String, Void, String> {
		private boolean showFetalErrorDialog = false;
		private boolean showTempErrorDialog = false;
		
		@Override
		protected String doInBackground(String... accountNameArgs) {
			MainActivity activity = MainActivity.this;
			
			String accountName = prefs.getAuthAccount().name;

			String token = null;
			try {
				token = GoogleAuthUtil.getToken(getApplicationContext(),
							accountName, 
							MainActivity.this.getString(R.string.auth_scope));
			} catch (GooglePlayServicesAvailabilityException e) {
				GooglePlayServicesUtil.getErrorDialog(
						e.getConnectionStatusCode(), activity, AUTH_ERROR_DIALOG).show();
			} catch (UserRecoverableAuthException e) {
				startActivityForResult(e.getIntent(), AUTH_RECOVERY);
			} catch (GoogleAuthException e) {				
				Log.e(TAG, "Unrecoverable auth exception: " + e.getMessage(), e);
				showFetalErrorDialog = true;
			} catch (IOException e) {
				Log.i(TAG, "Transient auth error: " + e.getMessage(), e);
				showTempErrorDialog = true;
			}
			return token;
		}

		protected void onPostExecute(String authToken) {
			MainActivity.this.prefs.setAuthToken(authToken);
			if (showFetalErrorDialog) showLoginFetalErrorDialog();
			if (showTempErrorDialog) showLoginTempErrorDialog();						
			updateConnectedDescription();
		}
	}
}
