package org.financetool.financetooltracker;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.location.*;
import android.content.Context;
import android.content.Intent;
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
    
    final Button loginButton = (Button) findViewById(R.id.loginButton);
    loginButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {        	
        	doLogin();
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
	
	private void doLogin() {
		AccountManager am = AccountManager.get(this);
    	Bundle options = new Bundle();

    	/*
    	 * 
    	 I need to pop something up so that the user can choose which google
    	 account to use.
    	 I need to use http://developer.android.com/reference/android/app/FragmentManager.html
    	 and http://developer.android.com/reference/android/app/DialogFragment.html
 protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_ACCOUNTS:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.select_google_account);
                final Account[] accounts = accountManager.getAccountsByType(GOOGLE_TYPE);
                final int size = accounts.length;
                String[] names = new String[size];
                for (int i = 0; i < size; i++) {
                    names[i] = accounts[i].name;
                }
                builder.setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        onAccountSelected(accounts[which]);
                        dialog.dismiss();
                    }
                });

                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                });
                return builder.create();
        }
        return null;
    }    	
    	 */
    	
    	Account[] accounts = am.getAccountsByType("com.google");
    	
    	AccountManagerFuture<Bundle> future = 
    	am.getAuthToken(
    		accounts[0],                     // Account retrieved using getAccountsByType()
    	    "Manage your tasks",            // Auth scope
    	    options,                        // Authenticator-specific options
    	    this,                           // Your activity
    	    new OnTokenAcquired(),          // Callback called when a token is successfully acquired
    	    null);
	}
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			// The user updated their login info, so try the login again.
			doLogin();
		}
	}
	
	private class OnTokenError implements Callback {
	    public void run(AccountManagerFuture<Bundle> result) {
	        // Get the result of the operation from the AccountManagerFuture.
	        try {
				Bundle bundle = result.getResult();
			} catch (OperationCanceledException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (AuthenticatorException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	     
	    }

		@Override
		public boolean handleMessage(Message msg) {
			// TODO Auto-generated method stub
			return false;
		}
	}
	
	private class OnTokenAcquired implements AccountManagerCallback<Bundle> {
	    @Override
	    public void run(AccountManagerFuture<Bundle> result) {
	        // Get the result of the operation from the AccountManagerFuture.
	        Bundle bundle = null;
			try {
				bundle = result.getResult();
			} catch (OperationCanceledException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (AuthenticatorException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        			
			
	        Intent launch = (Intent) bundle.get(AccountManager.KEY_INTENT);
	        if (launch != null) {
	            startActivityForResult(launch, 0);
	            return;
	        }
	    
	        // The token is a named value in the bundle. The name of the value
	        // is stored in the constant AccountManager.KEY_AUTHTOKEN.
	        String token = bundle.getString(AccountManager.KEY_AUTHTOKEN);
	        
	        String your_api_key = "";
	        String your_client_secret = "";
	        String your_client_id = "";
	        
	        URL url = null;
			try {
				url = new URL("https://www.googleapis.com/tasks/v1/users/@me/lists?key=" + your_api_key);
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        HttpURLConnection conn = null;
			try {
				conn = (HttpURLConnection) url.openConnection();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	        
			conn.addRequestProperty("client_id", your_client_id);
	        conn.addRequestProperty("client_secret", your_client_secret);
	        conn.setRequestProperty("Authorization", "OAuth " + token);
	        int code = -1;
			try {
				code = conn.getResponseCode();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        if (code == 401) {
	        	// Token expired
	        	AccountManager am = AccountManager.get(MainActivity.this);
	        	am.invalidateAuthToken("com.google", token);
	        }
	        
	    }
	}
}
