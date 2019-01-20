package com.example.moriyama.wearmobile;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.TimingLogger;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.app.Dialog;

import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.IOException;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import android.Manifest;

import static com.github.mikephil.charting.charts.Chart.LOG_TAG;


public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, MessageApi.MessageListener, EasyPermissions.PermissionCallbacks {
    private static final String TAG = MainActivity.class.getName();
    private GoogleApiClient mGoogleApiClient;
    TextView xTextView;
    TextView yTextView;
    TextView zTextView;
    LineChart mChart;
    int x, y, z;

    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private TextView SizeText;
    private EditText editText;
    private String fileName = "/test.txt";

    private Button buttonSave;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;


    static final int queue_count = 4;
    static final int data_size = 100;

    private static final String BUTTON_TEXT = "Call Google Sheets API";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {SheetsScopes.SPREADSHEETS};

    private List res_data = new ArrayList<>(); // 加速度センサーのデータを保持するための変数
    private List res_data2 = new ArrayList<>();
    private List res_data3 = new ArrayList<>();
    private List res_data4 = new ArrayList<>();

    private List queue = new ArrayList<>();


    private int line_count = 1;
    private int count = 0;

    private List AC_DATA = new ArrayList<>();
    private List H_DATA = new ArrayList<>();

    String[] names = new String[]{"x-value", "y-value", "z-value"};
    int[] colors = new int[]{Color.RED, Color.GREEN, Color.BLUE};

    int now_len = 1;
    boolean status = false;
    private long startTime;
    private long stopTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        xTextView = findViewById(R.id.xValue);
        yTextView = findViewById(R.id.yValue);
        zTextView = findViewById(R.id.zValue);
        startTime = System.currentTimeMillis();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Log.d(TAG, "onConnectionFailed:" + connectionResult.toString());
                    }
                })
                .addApi(Wearable.API)
                .build();
        mChart = findViewById(R.id.lineChart);

        mChart.setDescription(null); // 表のタイトルを空にする
        mChart.setData(new LineData()); // 空のLineData型インスタンスを追加


        LinearLayout activityLayout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        activityLayout.setLayoutParams(lp);
        activityLayout.setOrientation(LinearLayout.VERTICAL);
        activityLayout.setPadding(16, 16, 16, 16);

        ViewGroup.LayoutParams tlp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        final Button button1 = new Button (this);
        button1.setText("Start");
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (status) {
                    button1.setText("Start");
                    status = !status;
                }
                else{
                    button1.setText("Stop");
                    status = !status;
                }
            }
        });

        final Button button2 = new Button (this);
        button2.setText("Clear");
        button2.setPadding(16, 16, 16, 16);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AC_DATA.clear();
                H_DATA.clear();
                SizeText.setText(
                        "AC_SIZE: " + AC_DATA.size() + "   H_DATA:" + H_DATA.size() );
            }
        });

        buttonSave = new Button(this);
        buttonSave.setText("save data");
        buttonSave.setPadding(16, 16, 16, 16);
        buttonSave.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View arg0) {
                // 現在ストレージが書き込みできるかチェック
                if(isExternalStorageWritable()){
                    String fileName = editText.getText().toString();
                    File file = new File("/storage/emulated/0/Download/"+fileName+".csv");
                    File file2 = new File("/storage/emulated/0/Download/"+fileName+"_h.csv");

                    try(FileOutputStream fileOutputStream =
                                new FileOutputStream(file, false);
                        OutputStreamWriter outputStreamWriter =
                                new OutputStreamWriter(fileOutputStream, "UTF-8");
                        BufferedWriter bw =
                                new BufferedWriter(outputStreamWriter);
                    ) {
                        for (int i=0;i<AC_DATA.size();i++){
                            bw.write(String.valueOf(AC_DATA.get(i)).substring(1,String.valueOf(AC_DATA.get(i)).length()-1));
                            bw.newLine();
                        }
                        bw.flush();
                        mOutputText.setText("Success: write storage");
                    } catch (Exception e) {
                        mOutputText.setText("Error: write storage");
                        e.printStackTrace();
                    }
                    try(FileOutputStream fileOutputStream =
                                new FileOutputStream(file2, false);
                        OutputStreamWriter outputStreamWriter =
                                new OutputStreamWriter(fileOutputStream, "UTF-8");
                        BufferedWriter bw =
                                new BufferedWriter(outputStreamWriter);
                    ) {
                        for (int i=0;i<H_DATA.size();i++){
                            bw.write(String.valueOf(H_DATA.get(i)).substring(1,String.valueOf(H_DATA.get(i)).length()-1));
                            bw.newLine();
                        }
                        bw.flush();
                        mOutputText.setText("Success: write storage");
                    } catch (Exception e) {
                        mOutputText.setText("Error: write storage");
                        e.printStackTrace();
                    }
                }
                else
                    mOutputText.setText("Can't write External storage");
            };
        });
        editText = new EditText(this);
        editText.setText("test");
        editText.setPadding(16, 16, 16, 16);

        SizeText = new TextView(this);
        SizeText.setLayoutParams(tlp);
        SizeText.setPadding(16, 16, 16, 16);
        SizeText.setText(
                "AC_SIZE: " + AC_DATA.size() + "   H_DATA:" + H_DATA.size() );

        mOutputText = new TextView(this);
        mOutputText.setLayoutParams(tlp);
        mOutputText.setPadding(16, 16, 16, 16);
        mOutputText.setVerticalScrollBarEnabled(true);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());
        mOutputText.setText(
                "Click the \'" + BUTTON_TEXT + "\' button to test the API.");
        activityLayout.addView(SizeText);
        activityLayout.addView(button1);
        activityLayout.addView(button2);
        activityLayout.addView(editText);
        activityLayout.addView(buttonSave);
        activityLayout.addView(mOutputText);


        setContentView(activityLayout);

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }


    private void getResultsFromApi(List queue2) {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new MakeRequestTask(mCredential, queue2, count).execute();
        }
    }

    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                //getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    //getResultsFromApi();
                    break;
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        //getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    //getResultsFromApi(null);
                    break;
                }
                break;
        }
    }

    List dec_queue(int num) {
        int aa = num % queue_count;
        if (aa == 0)
            return res_data;
        else if (aa == 1)
            return res_data2;
        else if (aa == 2)
            return res_data3;
        else
            return res_data4;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }


    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (null != mGoogleApiClient && mGoogleApiClient.isConnected()) {
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //if (id == R.id.action_settings) {
        //    return true;
        //}
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        getResultsFromApi(null);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        xTextView.setText(messageEvent.getPath());
        String msg = messageEvent.getPath();
        String[] value = msg.split(",", 0);

        String[] value2 = value[0].split(">", 0);

        xTextView.setText(String.valueOf(value2[0]));
        yTextView.setText(String.valueOf(value2[1]));
        zTextView.setText(String.valueOf(value2[2]));

        Log.d(TAG, "VVV" + value.length);
        stopTime = System.currentTimeMillis();

        long time = stopTime - startTime;
        int second = (int) (time / 1000);
        int comma = (int) (time % 1000);


        LineData data = mChart.getLineData();
        queue = dec_queue(count);
        //Log.d(TAG, "VVV" + queue.size() + "," + count);
        if (data != null && status) {
            for (int i = 0; i < 3; i++) {
                ILineDataSet set = data.getDataSetByIndex(i);
                if (set == null) {
                    set = createSet(names[i], colors[i]);
                    data.addDataSet(set);
                }

                data.addEntry(new Entry(set.getEntryCount(), Float.parseFloat(value2[i])), i);
                data.notifyDataChanged();
            }

            mChart.notifyDataSetChanged();
            mChart.setVisibleXRangeMaximum(50);
            mChart.moveViewToX(data.getEntryCount());

            if (count % 1 == 0 && value2[0].length() != 0) {
                for (int i = 0; i < value.length; i++) {
                    String[] value3 = value[i].split(">", 0);
                    /*if (queue.size() < data_size) {
                        queue.add(Arrays.asList(
                                line_count,
                                String.valueOf(value3[0]),
                                String.valueOf(value3[1]),
                                String.valueOf(value3[2])
                        ));

                    }*/
                    if (Integer.valueOf(value3[0])==0) {
                        AC_DATA.add(Arrays.asList(String.valueOf(value3[4]),
                                String.valueOf(value3[1]),
                                String.valueOf(value3[2]),
                                String.valueOf(value3[3])));
                    }
                    else {
                        H_DATA.add(Arrays.asList(String.valueOf(value3[2]),
                                String.valueOf(value3[1])));
                    }

                }
                SizeText.setText(
                        "AC_SIZE: " + AC_DATA.size() + "   H_DATA:" + H_DATA.size() );

                /*if (queue.size() >= data_size) {
                    getResultsFromApi(queue);
                    count += 1;

                }*/


            }
        }
    }

    private LineDataSet createSet(String label, int color) {
        LineDataSet set = new LineDataSet(null, label);
        set.setLineWidth(2.5f);
        set.setColor(color);
        set.setDrawCircles(false);
        set.setDrawValues(false);

        return set;
    }


    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.sheets.v4.Sheets mService = null;
        private Exception mLastError = null;
        private Object objLock = new Object();
        private List queue3;
        private int sline;

        MakeRequestTask(GoogleAccountCredential credential, List queue2, int ss) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("wearmobile")
                    .build();
            queue3 = queue2;
            int aa = ss % queue_count;
            sline = 1 + (aa * data_size);
        }

        /**
         * Background task to call Google Sheets API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {

            try {
                putDataFromApi();
                //return getDataFromApi();
                return null;
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }

        }


        /**
         * Fetch a list of names and majors of students in a sample spreadsheet:
         * https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
         *
         * @return List of names and majors
         * @throws IOException
         */
        private List<String> getDataFromApi() throws IOException {
            String spreadsheetId = "1pmIFv6CUjUXB2GVtp3UghllLlEnegSwoah56Fc-k-fc";
            String range = "シート1!A" + String.valueOf(now_len) + ":D" + String.valueOf(now_len);
            List<String> results = new ArrayList<String>();
            ValueRange response = this.mService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            List<List<Object>> values = response.getValues();
            while (values != null) {
                range = "シート1!A" + String.valueOf(now_len) + ":D" + String.valueOf(now_len);
                response = this.mService.spreadsheets().values()
                        .get(spreadsheetId, range)
                        .execute();
                values = response.getValues();
                now_len += data_size;
            }
            now_len -= data_size;
            return results;
        }

        private void putDataFromApi() throws IOException {
            List values = queue3;
            String spreadsheetId = "1pmIFv6CUjUXB2GVtp3UghllLlEnegSwoah56Fc-k-fc";
            //String spreadsheetId = "1XxrUxjqNiSh4IkZc2gZiTJIJj2gY4snGtc9P-l_7ddQ";
            String VV = values.get(0).toString().substring(1);
            now_len = Integer.parseInt(VV.split(",")[0]);
            now_len = 1;
            //Log.d(TAG, "VVV"+now_len);
            String line = "シート1!A" + String.valueOf(now_len) + ":D";
            String range = line + (now_len + data_size);
            ValueRange valueRange = new ValueRange();
            valueRange.setValues(values);
            this.mService.spreadsheets().values()
                    .update(spreadsheetId, range, valueRange)
                    .setValueInputOption("RAW")
                    .execute();
            //line_count += res_data.size();
            values.clear();
        }


        @Override
        protected void onPreExecute() {
            //mOutputText.setText("");
            startTime = System.currentTimeMillis();
            //mProgress.show();

        }

        @Override
        protected void onPostExecute(List<String> output) {
            //mProgress.hide();
            stopTime = System.currentTimeMillis();

            long time = stopTime - startTime;
            int second = (int) (time / 1000);
            int comma = (int) (time % 1000);
            Log.d(TAG, ("Write time: " + second + "秒" + comma));
            if (output == null || output.size() == 0) {
                mOutputText.setText("No results returned.");
            } else {
                output.add(0, "Data retrieved using the Google Sheets API:");
                mOutputText.setText(TextUtils.join("\n", output));
            }
        }

        @Override
        protected void onCancelled() {
            //mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }
    }
}
