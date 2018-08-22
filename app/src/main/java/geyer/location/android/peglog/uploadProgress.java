package geyer.location.android.peglog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Kristoffer Geyer on 19/07/2018.
 * This is an activity that acts as a loading doc for the document, it is possible that the data
 * upload will be sufficiently fast that this isn't a requirement. However, it is nice to have.
 *
 * This simple activity uploads the error records into a password protected PDF format relaying
 * the progress to the progress bar continually throughout the upload process. The same if
 * subsequently done for the location data.
 *
 * This app may be seen as exclusively UI as nothing occurs without the participant attempting to
 * upload the data.
 */

public class uploadProgress extends Activity {

    //UI components
    ProgressBar progressBar;
    TextView progressStatus;

    //components for handling data which is not deleted with activity related to the operation of the app
    SharedPreferences upPrefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.upload_screen);
        initializeVisibleComponents();
        initializeInvisibleComponents();
        writePDFTable();
    }

    private void initializeVisibleComponents() {
        progressBar = findViewById(R.id.pbUpload);
        progressBar.setProgress(1);
        progressStatus = findViewById(R.id.tvProgress);
    }

    private void initializeInvisibleComponents() {
        upPrefs = getSharedPreferences("Data collection", Context.MODE_PRIVATE);

        //load the native libraries for the SQL cipher
        SQLiteDatabase.loadLibs(this);
    }

    //determines flow of activity
        //handles any errors and relays them to the user.
        //if upload went smoothly then the app selection interface should appear
    private void writePDFTable() {
        boolean okToUpload = true;
        try {
            makePdf();
            makeErrorPdf();
        } catch (IOException e) {
            okToUpload = false;
            e.printStackTrace();
            progressStatus.setText("IO Error: " + e.getLocalizedMessage());
        } catch (DocumentException e) {
            okToUpload = false;
            e.printStackTrace();
            progressStatus.setText("Document Error: " + e.getLocalizedMessage());
        }finally {
            if(okToUpload){
                email();
            }else{
                Toast.makeText(this, "Please inform the researcher about the above error", Toast.LENGTH_LONG).show();
            }

        }
    }

    private void makePdf() throws IOException, DocumentException {
    //creates document
        Document document = new Document();
        //getting destination
        File path = this.getFilesDir();
        File file = new File(path, Constants.TO_ENCRYPT_FILE);
        // Location to save
        PdfWriter writer =PdfWriter.getInstance(document, new FileOutputStream(file));
        writer.setEncryption("concretepage".getBytes(), upPrefs.getString("pdfPassword", "hufusm1234123").getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
        writer.createXmpMetadata();
        // Open to write
        document.open();

        //add to document
        document.setPageSize(PageSize.A4);
        document.addCreationDate();

        String selectQuery = "SELECT * FROM " + FeedReaderContract.FeedEntry.TABLE_NAME;
        SQLiteDatabase db = FeedReaderDbHelper.getInstance(this).getReadableDatabase(upPrefs.getString("password", "not to be used"));

        Cursor c = db.rawQuery(selectQuery, null);

        int iLat = c.getColumnIndex(FeedReaderContract.FeedEntry.LATITUDE);
        int iLong = c.getColumnIndex(FeedReaderContract.FeedEntry.LONGITUDE);
        int iAcc = c.getColumnIndex(FeedReaderContract.FeedEntry.ACCURACY);
        int iTime = c.getColumnIndex(FeedReaderContract.FeedEntry.TIMESTAMP);

        ArrayList<Double> databaseLatitudes = new ArrayList<>();
        ArrayList<Double> databaseLongitude = new ArrayList<>();
        ArrayList<Float> databaseAccuracy = new ArrayList<>();
        ArrayList<Long> databaseTimestamp = new ArrayList<>();


        for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
            databaseLatitudes.add(c.getDouble(iLat));
            databaseLongitude.add(c.getDouble(iLong));
            databaseAccuracy.add(c.getFloat(iAcc));
            databaseTimestamp.add(c.getLong(iTime));
        }

        c.close();
        db.close();

        //makes a table with four columns
        PdfPTable table = new PdfPTable(4);

        //attempts to add the columns
        try{
            for (int i = 0; i < databaseLatitudes.size(); i++){
                table.addCell("" + databaseLatitudes.get(i));
                table.addCell("" + databaseLongitude.get(i));
                table.addCell("" + databaseAccuracy.get(i));
                table.addCell("" + databaseTimestamp.get(i));
                if(i!=0){
                    float currentProgress = (float) i/databaseLatitudes.size();
                    currentProgress = currentProgress*100;
                    documentProgress(currentProgress);
                }
            }
        }catch(Exception e){
            Log.e("file construct", "error " + e);
        }finally{
            documentProgress(100);
            document.add(table);
            document.addAuthor("Kris");
            document.close();

            databaseLatitudes.clear();
            databaseLongitude.clear();
            databaseAccuracy.clear();
            databaseTimestamp.clear();
        }
    }

    private void documentProgress(float currentProgress) {
        Log.i("progress", "" + currentProgress);
        progressBar.setProgress((int) currentProgress);
        progressStatus.setText(currentProgress +"%");
    }

    private void makeErrorPdf () throws IOException, DocumentException{
        Document errorDocument = new Document();
        //getting destination
        File path = this.getFilesDir();
        File file = new File(path, Constants.ERROR_FILE_NAME);
        // Location to save
        PdfWriter writer =PdfWriter.getInstance(errorDocument, new FileOutputStream(file));
        writer.setEncryption("concretepage".getBytes(), upPrefs.getString("pdfPassword", "hufusm1234123").getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.STANDARD_ENCRYPTION_40);
        writer.createXmpMetadata();
        // Open to write
        errorDocument.open();

        //add to document
        errorDocument.setPageSize(PageSize.A4);
        errorDocument.addCreationDate();
        //retrieving error data from SQLite database
        errorDatabase errorsDatabase = new errorDatabase(this);
        errorsDatabase.open();

        ArrayList<String> Errors = errorsDatabase.getErrors();
        ArrayList<Long> TimeStamp = errorsDatabase.getTimes();

        errorsDatabase.close();

        //generating table with two columns
        PdfPTable table = new PdfPTable(2);

        try{
            for (int i = 0; i < Errors.size(); i++){
                table.addCell(Errors.get(i));
                table.addCell("" + TimeStamp.get(i));
                if(i!=0){
                    //documents current progress
                    float currentProgress = (float) i/Errors.size();
                    currentProgress = currentProgress*100;
                    documentProgress(currentProgress);
                }
            }
        }catch(Exception e){
            Log.e("file construct", "error " + e);
        }finally{
            try {
                documentProgress(100);
                errorDocument.add(table);
            }catch (Exception e){
                Log.e("upload progress", "file upload error: " + e );
            }
            errorDocument.addAuthor("Kris");
            errorDocument.close();
        }
    }


    //relays the email
    private void email() {

        //documenting intent to send multiple
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("text/plain");

        //getting directory for internal files
        String directory = (String.valueOf(this.getFilesDir()) + File.separator);
        Log.i("Directory", directory);

        //initializing files reference
        File toExportFile = new File(directory + File.separator + Constants.ERROR_FILE_NAME);
        File toExportFileError = new File(directory + File.separator + Constants.TO_ENCRYPT_FILE);

        //list of files to be uploaded
        ArrayList<Uri> files = new ArrayList<>();

        //if target files are identified to exist then they are packages into the attachments of the email
        try {
            if(toExportFile.exists()){
                files.add(FileProvider.getUriForFile(this, "geyer.location.android.peglog.fileprovider", toExportFile));
            }
            if(toExportFileError.exists()){
                Log.i("toExportFileError", "true");
                files.add(FileProvider.getUriForFile(this, "geyer.location.android.peglog.fileprovider", toExportFileError));
            }
            //adds the file to the intent to send multiple data points
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            this.startActivity(intent);
        }
        catch (Exception e){
            Log.e("File upload error1", "Error:" + e);
        }
    }
}
