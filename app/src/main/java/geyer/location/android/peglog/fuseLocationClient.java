package geyer.location.android.peglog;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.Calendar;
import java.util.Objects;

public class fuseLocationClient extends Service {

    //values for location provider
    FusedLocationProviderClient fusedLocationProviderClient;
    LocationRequest locationRequest;

    //values for determining if Runnables are running
    private boolean fileSizeAssessmentUnderway;
    private boolean diagnosisRunnableRunning;

    //handles the operating of Runnables
    private Handler generalHandler;

    //sharedPreferences values
    private SharedPreferences servicePreferences;
    private SharedPreferences.Editor editor;

    //broadcast receiver
    BroadcastReceiver generalReceiver;
    Boolean requireInitializationOfGeneralReceiver;

    private static final String TAG = "fuseLocationClient";

    /**
     * Directs code flow
     */

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //ensures service is running in foreground
        startForegroundOperations();
        //initializes global values
        initializeComponents();

        initializeBroadcastReceiver();
        Log.i(TAG, "called on start command");


        Bundle b = intent.getExtras();
        if(b != null){
            Log.i(TAG, "bundle not null");
            if(b.getBoolean("phone restarted")){
                storeErrorInSQL("Phone restarted");
            }else{
                //documents that the service is operational to internal memory and shared preferences
                documentServiceStart();
            }
        }else{
            Log.i(TAG, " bundle equals null");
        }
        //determines if a google play update is required in order to access location updates
        if (detectGooglePlayUpdateRequired()) {
            sendMessageToMain("To participate in the study, you are required to update your google play account");
        }else{
            //initialize the location updates
            initializeLocationUpdate();
        }
        return START_STICKY;
    }



    /**
     * Documents the start of the foreground service
     */

    //This method across SDK versions calls for a foreground service
    private void startForegroundOperations() {
        if (Build.VERSION.SDK_INT >= 26) {
            if (Build.VERSION.SDK_INT > 26) {
                String CHANNEL_ONE_ID = "sensor.example. geyerk1.inspect.screenservice";
                String CHANNEL_ONE_NAME = "Screen service";
                NotificationChannel notificationChannel = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                            CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_MIN);
                    notificationChannel.setShowBadge(true);
                    notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                    NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    manager.createNotificationChannel(notificationChannel);
                }

                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_location);
                Notification notification = new Notification.Builder(getApplicationContext())
                        .setChannelId(CHANNEL_ONE_ID)
                        .setContentTitle("Recording data")
                        .setContentText("Peg Log is collecting location data")
                        .setSmallIcon(R.drawable.ic_location)
                        .setLargeIcon(icon)
                        .build();

                Intent notificationIntent = new Intent(getApplicationContext(), geyer.location.android.peglog.MainActivity.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                notification.contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

                startForeground(101, notification);
            } else {
                startForeground(101, updateNotification());
            }
        } else {
            Intent notificationIntent = new Intent(this, MainActivity.class);

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);

            Notification notification = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.peg_icon)
                    .setContentTitle("Recording data")
                    .setContentText("Peg Log is collecting location data")
                    .setContentIntent(pendingIntent).build();

            startForeground(101, notification);
        }

    }

    private Notification updateNotification() {

        return new NotificationCompat.Builder(this)
                .setContentTitle("Recording data")
                .setTicker("Ticker")
                .setContentText("Peg log is collecting location data")
                .setSmallIcon(R.drawable.ic_location)
                .setOngoing(true).build();
    }

    /**
     * Initializes the global components
     */

    //values employed are initialized
    private void initializeComponents() {
        //booleans which handle if runnable is operating
        diagnosisRunnableRunning = false;
        fileSizeAssessmentUnderway = false;
        //handler for the Runnable
        generalHandler = new Handler();
        //sharedPreferences
        servicePreferences = getSharedPreferences("Data collection", Context.MODE_PRIVATE);
        editor = servicePreferences.edit();
        editor.apply();

        //load the native libraries for the SQL cipher
        SQLiteDatabase.loadLibs(this);

        requireInitializationOfGeneralReceiver = true;
    }

    //documents in shared preferences that service has started
    private void documentServiceStart() {
        Log.i(TAG, "started running");
        if(!servicePreferences.getBoolean("fuseLocationClient running", false)){
            storeErrorInSQL("Documenting start of recording");
            editor.putBoolean("fuseLocationClient running", true);
            editor.apply();
        }
    }

    //attempts to initialize fuseLocationProviderClient and call location updates.
    //on failure, diagnostics are run, errors logged and sent to main
    private void initializeLocationUpdate() {
        try {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
            getLocation();
        } catch (Exception e) {

            Log.e(TAG, "Can't initialize location updates: " + e);
            sendMessageToMain("Can't initialize location updates: " + e);
            storeErrorInSQL("Can't initialize location updates: " + e);
            generalHandler.postDelayed(reinitializeLocation,  1000);
        }
        Log.i(TAG, "started");
    }

    //initializes the relevant permission updates and if screen is off and if date changed
    private void initializeBroadcastReceiver() {
        generalReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    switch (Objects.requireNonNull(intent.getAction())) {
                        case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                            storeErrorInSQL("Airplane mode changed");
                            Log.i(TAG, "airplane Mode Changed");
                            generalHandler.postDelayed(reinitializeLocation, 1000);
                            break;
                        case Intent.ACTION_SHUTDOWN:
                            storeErrorInSQL("phone shutting down");
                            break;
                        case Intent.ACTION_REBOOT:
                            storeErrorInSQL("phone rebooting");
                            break;
                    }
                }
            }
        };
        IntentFilter permissionReceiverFilter = new IntentFilter();
        permissionReceiverFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        permissionReceiverFilter.addAction(Intent.ACTION_SHUTDOWN);
        permissionReceiverFilter.addAction(Intent.ACTION_REBOOT);
        registerReceiver(generalReceiver, permissionReceiverFilter);
    }

    //attempts to set up location recording
    //on failure, diagnostics are run
    private void getLocation() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(Constants.UPDATE_INTERVAL_IN_MILLI_SECONDS)
                .setFastestInterval(Constants.UPDATE_INTERVAL_IN_MILLI_SECONDS)
                .setPriority(Constants.ACCURACY_LEVEL);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
            generalHandler.postDelayed(reinitializeLocation, 1000);
        }else{
            try{
                fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
            }catch (Exception x){
                Log.e(TAG, "Point getLocation Error requesting location updates. Error: " + x);
                sendMessageToMain("Error requesting location updates. Error: " + x);
                storeErrorInSQL("Error requesting location updates. Error: " + x);
                generalHandler.postDelayed(reinitializeLocation,  1000);
            }
        }
    }

    /**
     * RUNNABLES
     */

    //runs diagnostics in a minute, if not already running
    //half minute delay is added to bottle neck various calls for the error handling to be run.
    private final Runnable reinitializeLocation = new Runnable() {
        @Override
        public void run() {
            if(!diagnosisRunnableRunning){
                diagnosisRunnableRunning = true;
                Log.i(TAG, "reinitialize location running");
                generalHandler.postDelayed(startRunningDiagnostics, 30 * 1000);
            }
        }
    };

    private final Runnable startRunningDiagnostics = new Runnable() {
        @Override
        public void run() {
            runDiagnosticsMethods();
            diagnosisRunnableRunning = false;
        }
    };



    //this runnable is called every 4 minutes after the location data is first called in order to establish that the data file is growing
    //if the data file does not grow after 8 minutes then diagnostics are run.
    private Runnable determineFileSize = new Runnable() {
        @Override
        public void run() {
            fileSizeAssessmentUnderway = true;

            SQLiteDatabase rDB = FeedReaderDbHelper.getInstance(fuseLocationClient.this).getReadableDatabase(servicePreferences.getString("password", "not to be used"));

            Cursor cursor = rDB.rawQuery("SELECT * FROM '" + FeedReaderContract.FeedEntry.TABLE_NAME + "';", null);

            Long pastSizeOfDB = servicePreferences.getLong("file length", 0);
            int newSizeOfDB = cursor.getCount();

            cursor.close();

            if (pastSizeOfDB < newSizeOfDB) {
                Log.i(TAG, "Determine file size - Increase in file size. File size - " + newSizeOfDB);
                editor.putLong("file length", newSizeOfDB);
                editor.putInt("number of failed updates", 0);
                editor.apply();
            } else {
                editor.putInt("number of failed updates", servicePreferences.getInt("number of failed updates", 0) + 1);
                editor.apply();
                Log.i(TAG, "Determine file size - No increase in file size File size - " + newSizeOfDB);
                if (servicePreferences.getInt("number of failed updates", 0) > 1) {
                    Log.e(TAG, "Determine file size - consistent failing in increasing file size");
                    generalHandler.postDelayed(reinitializeLocation,  1000);
                }
            }
            generalHandler.postDelayed(determineFileSize, (Constants.UPDATE_INTERVAL_IN_MILLI_SECONDS));
        }
    };

    //generates location data and stores them as a message, on the first instance of the data being called the title of the dataframes is stored
    LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                storeInSQL(location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getTime());
            }
        }
    };

    /**
     * Handling data storage
     */

    //stores the data into the internal database
    private void storeInSQL(double myLatitude, double myLongitude, float myAccuracy, long myTime) {

        SQLiteDatabase db = FeedReaderDbHelper.getInstance(this).getWritableDatabase(servicePreferences.getString("password", "not to be used"));

        ContentValues values = new ContentValues();
        values.put(FeedReaderContract.FeedEntry.LATITUDE, myLatitude);
        values.put(FeedReaderContract.FeedEntry.LONGITUDE, myLongitude);
        values.put(FeedReaderContract.FeedEntry.ACCURACY, myAccuracy);
        values.put(FeedReaderContract.FeedEntry.TIMESTAMP, myTime);

        db.insert(FeedReaderContract.FeedEntry.TABLE_NAME, null, values);

        Cursor cursor = db.rawQuery("SELECT * FROM '" + FeedReaderContract.FeedEntry.TABLE_NAME + "';", null);
        Log.d(TAG, "Update: " + myLatitude + " " + myLongitude + " " + myAccuracy + " " + myTime);
        cursor.close();
        db.close();


        sendMessageToMain("Data collection on going");
        if (!fileSizeAssessmentUnderway) {
            generalHandler.postDelayed(determineFileSize, 0);
        }
    }

    //stores the data into the internal database
    private void storeErrorInSQL(String error) {
        String messageToRelay = error.replace(" ", "-");
        errorDatabase locationDatabaseStore = new errorDatabase(this);
        locationDatabaseStore.open();
        locationDatabaseStore.addEntry(messageToRelay, System.currentTimeMillis());
        locationDatabaseStore.close();

        sendMessageToMain("Error handling on going");
        if (!fileSizeAssessmentUnderway) {
            generalHandler.postDelayed(determineFileSize, 0);
        }
    }

    /**
     * ERROR HANDLING
     */

    //determines if there are issues with signal, connection to internet, permissions or if google play requires update
    private void runDiagnosticsMethods() {

        // string to store error
        String error = "";

        //initialize components
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        Boolean connectedToNetwork = true;

        //airplane mode on?
        if(isAirplaneModeOn(this)){
            error += "A,";
            connectedToNetwork = false;
        }else{
            //can we determine if there is no signal
            if (tel != null) {
                Log.i(TAG, "Network connection: " + tel.getNetworkOperator());
                if(!accessToSignal(tel)){
                    error += "nC,";
                    connectedToNetwork = false;
                }
            }else{
                Log.i(TAG, "Network connection: NULL");
                error += "EC,";
            }

            //is there internet connection
            try {
                ConnectivityManager connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                assert connectivityManager != null;
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                if(networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()){
                    Log.i(TAG, "Network connected ");
                }else{
                    error = "I,";
                    connectedToNetwork = false;
                }

            } catch (Exception e) {
                Log.e(TAG, "Connection manager - Issue identifying state of connectivity: " + e);
            }
        }

        if(connectedToNetwork){
            checkPermissions();
        }else{
            sendMessageToMain("error: " + error);
            storeErrorInSQL(error);
        }

        initializeLocationUpdate();
    }

    private boolean accessToSignal(TelephonyManager tel) {
        return tel.getNetworkOperator() != null && !tel.getNetworkOperator().equals("");
    }

    private static boolean isAirplaneModeOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }


    private void checkPermissions() {
        String errorPerm = "S,";
        boolean runTimeLocationPermitted = true,
                locationPermissionGiven = true;

        //check runtime location permission given
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runTimeLocationPermitted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        //check if general location permission is given
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            locationPermissionGiven = locationSensingDetected();
        }

        //general location permission?
        if (!locationPermissionGiven) {
            errorPerm += "nP,";
            notificationHelper nh = new notificationHelper(this);
            nh.createNotification("PEG log requires that you enable location permissions",
                    "Please turn on general location permissions in order for data collection to continue");
        } else {
            errorPerm += "P,";
        }

        //runtime location permission provided?
        if (!runTimeLocationPermitted) {
            errorPerm += "nRP,";
        } else {
            errorPerm += "RP,";
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        //is GPS enabled?
        //is GPS enabled?
        if (locationManager != null) {
            boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER),
            //is network enabled?
            networkEnable = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            //document all permissions provided
            if (runTimeLocationPermitted && gpsEnabled && networkEnable && locationPermissionGiven) {
                errorPerm += "nO,";
            }else{
                if (!gpsEnabled) {
                    errorPerm += "nG,";
                } else {
                    errorPerm += "G,";
                }

                if (!networkEnable) {
                    errorPerm += "nN,";
                } else {
                    errorPerm += "N,";
                }
            }

        }

            storeErrorInSQL(errorPerm);
            sendMessageToMain(errorPerm);
            Log.e("Diagnostics", errorPerm);
        }
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        private boolean locationSensingDetected() {

        boolean locationPermissionEnabled = false;
        try {
            int locationMode = Settings.Secure.getInt(getApplicationContext().getContentResolver(), Settings.Secure.LOCATION_MODE);
            locationPermissionEnabled = locationMode != Settings.Secure.LOCATION_MODE_OFF;
            Log.i(TAG, "Location permission given: " + locationPermissionEnabled);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Location permission was inaccessible. Error: " + e);

        }
        return locationPermissionEnabled;
    }

    private boolean detectGooglePlayUpdateRequired() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode == ConnectionResult.SUCCESS) {
            return false;
        } else {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                //have to record the error in an activity
                //use below code
                //googleApiAvailability.getErrorDialog(activity, resultCode, 2404);
                return true;
            }
            return true;
        }
    }

    /**
     * RELAYING DATA TO MAIN ACTIVITY AND OTHER NECESSARY FUNCTIONS
     */

    //relays messages to the main activity
    public void sendMessageToMain(String toRelay) {
        Intent intent = new Intent("changeInService");
        intent.putExtra("locationDataCollectionBegan", true);
        intent.putExtra("Status", toRelay);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.i(TAG, "data sent to main");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //unregisters receiver when destroyed
    @Override
    public void onDestroy() {
        Log.i(TAG, "On destroy called");
        unregisterReceiver(generalReceiver);

        if(fileSizeAssessmentUnderway){
            generalHandler.removeCallbacks(determineFileSize);
        }
        if(diagnosisRunnableRunning){
            generalHandler.removeCallbacks(startRunningDiagnostics);
            generalHandler.removeCallbacks(reinitializeLocation);
        }

        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        super.onDestroy();
    }
}
