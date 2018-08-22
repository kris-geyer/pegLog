package geyer.location.android.peglog;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

/**
 * The code contained within this activity is divided up between what is up to the participant to call for and
 * what the application will call for. All aspects which the participant will call for are referred to as UI.
 * If the section is not marked UI then assume that this is necessary functions for the application to operate.
 */

public class MainActivity extends Activity implements View.OnClickListener {

    //components for handling data which is not deleted with activity related to the operation of the app
    SharedPreferences mainPreferences;
    SharedPreferences.Editor editor;

    //handler for detecting if initializing broadcastReceiver is required.
    Handler handler;
    Boolean broadcastReceiverInitializationRequired;

    //ID for particular permission calls.
    private static final int MY_PERMISSION_REQUEST_ALL_PERMISSIONS = 101;
    private static final int MY_PERMISSION_REQUEST_FINE_LOCATION = 102;
    private static final int MY_PERMISSION_REQUEST_READ_PHONE_STATE = 103;

    //used to relay the status of data collection
    TextView statusTV;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    //initialize ui components
        initializeVisibleComponents();
    //initialize components required for central operations
        initializeInvisibleComponents();
    //initialize the application for background recording.
        if(!mainPreferences.getBoolean("password generated", false)) {
            informUser();
        }else{
            readyToRecord();
        }

    }

    /**
     * UI section
     */

    private void initializeVisibleComponents() {
        //ui components initialization
        Button emailMe = findViewById(R.id.emailBtn);
        emailMe.setOnClickListener(this);

        Button report = findViewById(R.id.btnReport);
        report.setOnClickListener(this);

        Button newP = findViewById(R.id.btnNewP);
        newP.setOnClickListener(this);

        Button showInst = findViewById(R.id.btnShowInstructions);
        showInst.setOnClickListener(this);

        statusTV = findViewById(R.id.statusTV);

        //load the native libraries for the SQL cipher
        SQLiteDatabase.loadLibs(this);
    }


    private void initializeInvisibleComponents() {
        //initializing shared preferences
        mainPreferences = getSharedPreferences("Data collection", Context.MODE_PRIVATE);
        editor = mainPreferences.edit();
        editor.apply();

        broadcastReceiverInitializationRequired = true;

        handler = new Handler();

        Intent intent = new Intent("changeInService");
        intent.putExtra("assessingInitialization",true );
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        //initializing the receiver that gets input from background processes
        handler.postDelayed(accountForInitialization, 500);
    }

    Runnable accountForInitialization = new Runnable() {
        @Override
        public void run() {
            if(broadcastReceiverInitializationRequired){
                LocalBroadcastManager.getInstance(MainActivity.this).registerReceiver(dataCollectionInitiated, new IntentFilter("changeInService"));
            }
        }
    };


    //handles button presses
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.emailBtn:
                startActivity(new Intent(this, uploadProgress.class));
                break;
            case R.id.btnReport:
                reportLastDataPoint();
                break;
            case R.id.btnNewP:
                newPassword();
                break;
            case R.id.btnShowInstructions:
                informUser();
                break;
        }
    }

    //surveys the SQLite database in order to return the last known data entry for the location database
    private void reportLastDataPoint() {

        try{
            String selectQuery = "SELECT * FROM " + FeedReaderContract.FeedEntry.TABLE_NAME;

            // Open
            SQLiteDatabase db = FeedReaderDbHelper.getInstance(this).getReadableDatabase(mainPreferences.getString("password", "not to be used"));
            // I know this passphrase can be figured out by decompiling.

            // Cursor with query
            Cursor c = db.rawQuery(selectQuery, null);


            int iLat = c.getColumnIndex(FeedReaderContract.FeedEntry.LATITUDE);
            int iLong = c.getColumnIndex(FeedReaderContract.FeedEntry.LONGITUDE);
            int iAcc = c.getColumnIndex(FeedReaderContract.FeedEntry.ACCURACY);
            int iTime = c.getColumnIndex(FeedReaderContract.FeedEntry.TIMESTAMP);

            c.moveToLast();
            StringBuilder toToast = new StringBuilder();
            toToast.append("Latitude: ").append(c.getDouble(iLat)).append("\n");
            toToast.append("Longitude: ").append(c.getDouble(iLong)).append("\n");
            toToast.append("Accuracy (meters): ").append(c.getLong(iAcc)).append("\n");
            toToast.append("Timestamp (unix): ").append(c.getFloat(iTime)).append("\n");


            c.close();
            db.close();

            Toast.makeText(this, toToast, Toast.LENGTH_SHORT).show();
        }catch(Exception e){
            Toast.makeText(this, "Error detected, please contact researcher", Toast.LENGTH_SHORT).show();

        }
    }

    private void informUser() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("peg log").setMessage("This application records and stores your current location every " + Constants.UPDATE_INTERNAL_IN_MINUTES  + " minutes.\n" +
                "\n" +
                "The data is stored securely on your smartphone. \n" +
                "\n" +
                "To stop data collection and delete all associated files, simply uninstall the application. \n" +
                "\n" +
                "You will now be asked to provide a password to secure your data and approve relevant permissions.\n" +
                "\n" +
                "One of these permissions concerns the ability to ‘make and manage phone calls’. This is required so that the application can log when and why location data is not recorded as expected. For example, this might happen when no cellular signal is available. No other information concerning your personal communication patterns will be recorded. \n" +
                "\n" +
                "For more information, the complete privacy policy can be viewed below, otherwise press OK to continue.")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                     readyToRecord();
                    }
                })
                .setNegativeButton("View privacy policy", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Uri uri = Uri.parse("https://psychsensorlab.com/privacy-agreement-for-apps/");
                        Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(launchBrowser);
                    }
                });


        builder.create()
                .show();
    }

    /**
     * initializing permissions and recording
     */

    //this method ensures that all the appropriate user behaviours are carried out before logging begins including (in order):
        //generating a password
        //giving permissions:
            //location
            //phone state
    private void readyToRecord() {
        if(mainPreferences.getBoolean("password generated", false)){
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M){
                if(locationSensingDetected()){
                    collectData();
                }else{
                    requestLocationsSettingChange();
                }
            }
            else {
                initializeApp();
            }
        }else{
            requestPassword();
        }
    }

    private boolean locationSensingDetected() {

        boolean locationPermissionEnabled = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                int locationMode = Settings.Secure.getInt(getApplicationContext().getContentResolver(), Settings.Secure.LOCATION_MODE);
                locationPermissionEnabled = locationMode != Settings.Secure.LOCATION_MODE_OFF;

                Log.i("Main", "Location permission given: " + locationPermissionEnabled);
            } catch (Settings.SettingNotFoundException e) {
                Log.e("Main", "Location permission was inaccessible. Error: " + e);
            }
        }
        Log.i("Main", "setting location found to be enabled: " + locationPermissionEnabled);
        return locationPermissionEnabled;
    }

    private void requestLocationsSettingChange() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        // Setting Dialog Title
        alertDialog.setTitle("GPS is settings");

        // Setting Dialog Message
        alertDialog.setMessage("Location services is not enabled. Do you want to go to settings menu?");

        // On pressing Settings button
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(intent, 202);
            }
        });

        // on pressing cancel button
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                readyToRecord();
            }
        });
        alertDialog.show();
    }

    //requests the password from the participant. Gets called again if the password is not longer than 8 characters
    private void requestPassword() {
        LayoutInflater inflater = this.getLayoutInflater();
        //create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("password")
                .setMessage("Please specify a password that is 6 characters in length. This can include letters and/or numbers.")
                .setView(inflater.inflate(R.layout.alert_dialog_et, null))
                .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                    Dialog d = (Dialog) dialogInterface;
                                EditText password = d.findViewById(R.id.etPassword);
                                if(checkPassword(password.getText())){
                                   editor.putBoolean("password generated", true);
                                       editor.putString("password", String.valueOf(password.getText()));
                                       editor.putString("pdfPassword", String.valueOf(password.getText()));
                                   editor.apply();
                                   readyToRecord();
                                }else{
                                    readyToRecord();
                                    Toast.makeText(MainActivity.this, "The password entered was not long enough", Toast.LENGTH_SHORT).show();
                                }
                            }

                    private boolean checkPassword(Editable text) {
                        return text.length() > 5;
                    }
                });
        builder.create()
                .show();
    }

    private void newPassword(){
        LayoutInflater inflater = this.getLayoutInflater();
        //create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("password")
                .setMessage("Please specify a password that is 6 characters in length. This can include letters and/or numbers.")
                .setView(inflater.inflate(R.layout.alert_dialog_et, null))
                .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Dialog d = (Dialog) dialogInterface;
                        EditText password = d.findViewById(R.id.etPassword);
                        if(checkPassword(password.getText())){
                            editor.putString("pdfPassword", String.valueOf(password.getText()));
                            editor.apply();
                            readyToRecord();
                        }else{
                            newPassword();
                            Toast.makeText(MainActivity.this, "The password entered was not long enough", Toast.LENGTH_SHORT).show();
                        }
                    }

                    private boolean checkPassword(Editable text) {
                        return text.length() > 5;
                    }
                });
        builder.create()
                .show();
    }

    //Identifies which permissions have been given and requests the dangerous permissions which have not.
    public void initializeApp() {
        //determines if both permissions are required
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED){
            //requests both permissions
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE},
                    MY_PERMISSION_REQUEST_ALL_PERMISSIONS);
        }
        //if just the location permission isn't granted
        else if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            //requests the location permission
            ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSION_REQUEST_FINE_LOCATION);
        }
        //if just the phone state permission isn't granted
        else if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED){
            //requests the phone state permission
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_PHONE_STATE},
                        MY_PERMISSION_REQUEST_READ_PHONE_STATE);
        }
        //if all relevant permissions are granted then start the services
        else{
            if(locationSensingDetected()){
                collectData();
            }else{
                requestLocationsSettingChange();
            }
        }
    }

    //handle the result of the requests for permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSION_REQUEST_ALL_PERMISSIONS:
                if(grantResults.length > 0){
                    if(grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED){
                        collectData();
                    }else{
                        Toast.makeText(this, "Location permissions and phone state permissions are required to participate in the experiment", Toast.LENGTH_SHORT).show();
                        initializeApp();
                    }
                }else{
                    initializeApp();
                }
                break;
            case MY_PERMISSION_REQUEST_FINE_LOCATION:
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    initializeApp();
                }else{
                    Toast.makeText(this, "To participate in the study you must give location permission", Toast.LENGTH_SHORT).show();
                    initializeApp();
                }
                break;
            case MY_PERMISSION_REQUEST_READ_PHONE_STATE:
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    initializeApp();
                }else{
                    Toast.makeText(this, "To participate in the study you must give read phone state permission. This is required so we can identify what might be wrong with the location sensing", Toast.LENGTH_SHORT).show();
                    initializeApp();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case 202:
                readyToRecord();
                break;
        }
    }

    //starts the background operations
    private void collectData() {
        if(locationSensingDetected()){
            //detecting if background operations have previously been requested to run.
            if(!isMyServiceRunning(fuseLocationClient.class)){
                Intent activityIntent = new Intent(this, fuseLocationClient.class);
                Bundle b=new Bundle();
                b.putBoolean("phone restarted", false);
                activityIntent.putExtras(b);
                //start the background operations
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    startService(activityIntent);
                }else {
                    startForegroundService(activityIntent);
                }
            }
        }else{
            requestLocationsSettingChange();
        }

    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    //relays data sent from background operations to the user.
    private BroadcastReceiver dataCollectionInitiated = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra("locationDataCollectionBegan", false)){
                String toRelay = intent.getStringExtra("Status");
                String toShow ="Status: " + "\n" +
                            toRelay;

                    statusTV.setText(toShow);
                    statusTV.setGravity(Gravity.CENTER);
            }else if(intent.getBooleanExtra("assessingInitialization", false)){
                Log.i("Main", "broadcast receiver is already initialized");
                broadcastReceiverInitializationRequired = false;
            }

        }
    };


    //final call before activity is destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}

