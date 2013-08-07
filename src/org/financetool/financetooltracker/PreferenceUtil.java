package org.financetool.financetooltracker;

import com.google.android.gms.auth.GoogleAuthUtil;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class PreferenceUtil {
	private SharedPreferences prefs;
	private AccountManager accountManager;
	private SharedPreferences.Editor editor;
		
	public PreferenceUtil(Context context) {
		accountManager = AccountManager.get(context);
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		editor = prefs.edit();
	}
	
	public Account getAuthAccountOrDefault() {
		Account account = getAuthAccount();
		if (account != null) {
			return account;
		} else {
			return getDefaultAccount();
		}
	}
	
	public boolean isAuthAccountConnected() {
		return prefs.getBoolean("connected", false);
	}
	
	public boolean shouldUseGPS() {
		return prefs.getBoolean("use_gps", false);
	}
	
	public boolean shouldTrackLocation() {
		return prefs.getBoolean("track_location", false);
	}
	
	public void setAuthAccount(Account account) {
		editor.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
		editor.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
		editor.apply();
	}
	
	private Account getDefaultAccount() {
		Account[] googleAccounts = getGoogleAccounts();
		if (googleAccounts.length > 0) {
			return googleAccounts[0];
		}
		return null;
	}
	
	public Account getAuthAccount() {		
		String name = prefs.getString(AccountManager.KEY_ACCOUNT_NAME, null);
		String type = prefs.getString(AccountManager.KEY_ACCOUNT_TYPE, null);
		if (name != null && type != null) {
			Account[] accounts = accountManager.getAccountsByType(type);
			for (Account account : accounts) {
				if (account.name.equals(name)) {
					return account;
				}
			}
		}
		return null;
	}
	
	public void setAuthToken(String token) {
		editor.putString(AccountManager.KEY_AUTHTOKEN, token);
		editor.apply();
	}
	
	public String getAuthToken() {
		return prefs.getString(AccountManager.KEY_AUTHTOKEN, null);
	}
	
	private Account[] getGoogleAccounts() {
		return accountManager.getAccountsByType(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
	}
}
