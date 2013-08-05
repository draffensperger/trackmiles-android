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
	
	public void setAuthAccount(Account account) {
		editor.putString("auth_account_name", account.name);
		editor.putString("auth_account_type", account.type);
		editor.commit();
	}
	
	private Account getDefaultAccount() {
		Account[] googleAccounts = getGoogleAccounts();
		if (googleAccounts.length > 0) {
			return googleAccounts[0];
		}
		return null; 
	}
	
	private Account getAuthAccount() {		
		String name = prefs.getString("auth_account_name", null);
		String type = prefs.getString("auth_account_type", null);
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
	
	private Account[] getGoogleAccounts() {
		return accountManager.getAccountsByType(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
	}
}
