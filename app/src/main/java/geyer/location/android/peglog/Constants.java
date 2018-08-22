package geyer.location.android.peglog;

import com.google.android.gms.location.LocationRequest;

public final class Constants {

    /**
     * VALUES TO MANIPULATE
     */

    // Milliseconds per second
    private static final int MILLISECONDS_PER_MINUTE = 1000*60;
    //The location update frequency, in minutes
    static final int UPDATE_INTERNAL_IN_MINUTES = 5;

    static final int FASTEST_UPDATE_INTERNAL_IN_MINUTES = 4;

    static final long UPDATE_INTERVAL_IN_MILLI_SECONDS = MILLISECONDS_PER_MINUTE * UPDATE_INTERNAL_IN_MINUTES;
    // The fastest update frequency, in seconds
    static final int FASTEST_INTERVAL_IN_MINLLI_SECONDS = MILLISECONDS_PER_MINUTE * FASTEST_UPDATE_INTERNAL_IN_MINUTES;

    /**
     * FILE NAMES
     */

    //File name of the error log
    static final String ERROR_FILE_NAME = "ErrorData.pdf";
    //Fle name of the location data
    static final String TO_ENCRYPT_FILE = "locationData.pdf";
    //setting the level of accuracy that the application works
    static final int ACCURACY_LEVEL = LocationRequest.PRIORITY_HIGH_ACCURACY;
}
