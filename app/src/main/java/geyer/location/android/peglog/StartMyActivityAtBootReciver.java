package geyer.location.android.peglog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/**
 * When the phone is restarted this broadcast receiver will restart the background operations
 */

public class StartMyActivityAtBootReciver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent activityIntent = new Intent(context, fuseLocationClient.class);
            Bundle b=new Bundle();
            b.putBoolean("phone restarted", true);
            activityIntent.putExtras(b);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                context.startService(activityIntent);
            }else {
                context.startForegroundService(activityIntent);
            }
        }
    }
}