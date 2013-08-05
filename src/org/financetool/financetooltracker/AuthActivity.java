package org.financetool.financetooltracker;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.GooglePlayServicesUtil;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class AuthActivity extends Activity {
	private static final int PICK_ACCOUNT_REQUEST = 1;
	private static final int AUTH_ERROR_DIALOG = 2;
	private static final int AUTH_RECOVERY = 2;
	private static final int RESULT_AUTH_FAILED = RESULT_FIRST_USER;
	private PreferenceUtil prefs;
	
	public static final String AUTH_SCOPE = 
			"oauth2:https://www.googleapis.com/auth/userinfo.email " + 
			"https://www.googleapis.com/auth/userinfo.profile ";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// TODO: Show which account they already have logged in and default
		// it to that one. Otherwise default it to the first account in the list.
		// Maybe even just select the cru.org account or maybe see if there is
		// only one Google Account and use that one.			
						
		prefs = new PreferenceUtil(getApplicationContext());
		
		Intent intent = AccountPicker.newChooseAccountIntent(
				prefs.getAuthAccountOrDefault(), null, 
				new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE},
			    false, getString(R.string.ui_account_picker_description), 
			    null, null, null);
		startActivityForResult(intent, PICK_ACCOUNT_REQUEST);	
	}		
	
	protected void onActivityResult(final int requestCode, final int resultCode,
	         final Intent data) {
		
		// TODO: Handle the case where they cancel the selection dialog.
		if (requestCode == PICK_ACCOUNT_REQUEST && resultCode == RESULT_OK) {
			new AuthTask().execute(
					data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME));
	   }
	}
	
	private class AuthTask extends AsyncTask<String, Void, String> {
		private String accountName;

		@Override
		protected String doInBackground(String... accountNameArgs) {
			accountName = accountNameArgs[0];
			
			String token = null;
			try {
			     token = GoogleAuthUtil.getToken(getApplicationContext(), 
			    		 	accountName, 
							AUTH_SCOPE);
			     
			     // TODO: Call the server to check that the token is valid
			     // and invalidate it if necessary.
			 } catch (GooglePlayServicesAvailabilityException playEx) {
				 // TODO: Finish the activity with an error message in this
				 // case.
			     Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
			         playEx.getConnectionStatusCode(),
			         AuthActivity.this,
			         AUTH_ERROR_DIALOG);
			 } catch (UserRecoverableAuthException recoverableException) {
				 // TODO: Handle the case where the user cancels the recovery
				 // dialog.
			     Intent recoveryIntent = recoverableException.getIntent();
			     startActivityForResult(recoveryIntent, AUTH_RECOVERY); 
			 } catch (GoogleAuthException authEx) {
				 // TODO: Handle the unexpected exception in a better way
				 authEx.printStackTrace();
			 } catch (IOException ioEx) {
				 // TODO: I think in this case you are supposed to retry auth
				 ioEx.printStackTrace();
			 }
			 return token;
		}

		protected void onPostExecute(String authToken) {
			AlertDialog.Builder dlgAlert  = 
					new AlertDialog.Builder(AuthActivity.this);
			dlgAlert.setMessage("Logged in, authToken: " + authToken);
			dlgAlert.setTitle("AuthToken");
			dlgAlert.setPositiveButton("OK", null);
			dlgAlert.setCancelable(true);
			dlgAlert.create().show();
			
			String accountType = GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE;
			prefs.setAuthAccount(new Account(accountName, accountType));
			
			Intent returnIntent = new Intent();
			
			if (authToken != null) {
				returnIntent.putExtra("accountType", accountType);
				returnIntent.putExtra("accountName", accountName);
				returnIntent.putExtra("authToken", authToken);
				setResult(RESULT_OK, returnIntent);
			} else {
				setResult(RESULT_AUTH_FAILED, returnIntent);
			}
			finish();
			AuthActivity.this.finish();
		}
	}
}
