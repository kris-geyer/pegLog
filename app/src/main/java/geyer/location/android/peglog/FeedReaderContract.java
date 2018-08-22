package geyer.location.android.peglog;

import android.provider.BaseColumns;

public class FeedReaderContract {
    public FeedReaderContract(){}

    public  static abstract class FeedEntry implements BaseColumns {
        public static final String TABLE_NAME = "location_log";
        public static final String COLUMN_NAME_ENTRY_ID = "column_id";
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";
        public static final String ACCURACY = "accuracy";
        public static final String TIMESTAMP = "timestamp";
    }
}
