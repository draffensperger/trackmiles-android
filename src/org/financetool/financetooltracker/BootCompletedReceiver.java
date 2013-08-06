package org.financetool.financetooltracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompletedReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
      Intent startServiceIntent = new Intent(context, 
      		TrackerService.class);
      context.startService(startServiceIntent);
  }
}