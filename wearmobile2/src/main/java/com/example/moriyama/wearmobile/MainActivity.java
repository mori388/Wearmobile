package com.example.moriyama.wearmobile;

import android.Manifest;
import android.os.Bundle;
import android.app.Activity;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.wearable.activity.WearableActivity;
import android.content.pm.PackageManager;
import android.widget.TextView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.WindowManager;

import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;

import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;



public class MainActivity extends Activity implements SensorEventListener, LocationListener{

    private final String TAG = MainActivity.class.getName();
    private TextView mTextView;
    private SensorManager mSensorManager;
    private GoogleApiClient mGoogleApiClient;
    private String mNode;
    private float x,y,z,h=0;
    int count = 0, h_count = 0;
    final int data_size = 50;
    final int h_size = 5;
    float[] ts = new float[data_size];
    float[] xs = new float[data_size];
    float[] ys = new float[data_size];
    float[] zs = new float[data_size];
    float[] h_rate = new float[h_size];
    float[] th_rate = new float[h_size];
    private long sTime;
    String SEND_DATA;
    private LocationManager mLocationManager;
    private String bestProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mTextView = (TextView) findViewById(R.id.text);
        mTextView.setTextSize(30.0f);

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        sTime = System.currentTimeMillis();
        //initLocationManager();
        //locationStart();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        Log.d(TAG, "onConnected");
                        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                            @Override
                            public void onResult(NodeApi.GetConnectedNodesResult nodes) {
                                if (nodes.getNodes().size() > 0) {
                                    mNode = nodes.getNodes().get(0).getId();
                                }
                            }
                        });
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.d(TAG, "onConnectionSuspended");

                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Log.d(TAG, "onConnectionFailed : " + connectionResult.toString());
                    }
                })
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        Sensor sensor2 = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, sensor2, SensorManager.SENSOR_DELAY_NORMAL);
        mGoogleApiClient.connect();
    }


    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (count >= data_size) {
                count = 0;
                x = event.values[0];
                y = event.values[1];
                z = event.values[2];
                //t = (System.currentTimeMillis()-sTime)/1000;
                //Log.d(TAG, "DATA" +(xs[0]));
                mTextView.setText(String.format("X : %f\nY : %f\nZ : %f\nH : %f", x, y, z, h));
                SEND_DATA = "";
                for (int i = 0; i < xs.length; i++) {
                    SEND_DATA += 0 + ">" + xs[i] + ">" + ys[i] + ">" + zs[i] + ">" + ts[i] + ",";
                }
                if (mNode != null) {
                    Wearable.MessageApi.sendMessage(mGoogleApiClient, mNode, SEND_DATA, null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult result) {
                            if (!result.getStatus().isSuccess()) {
                                Log.d(TAG, "ERROR : failed to send Message" + result.getStatus());
                            }
                        }
                    });
                }
            } else {
                xs[count] = event.values[0];
                ys[count] = event.values[1];
                zs[count] = event.values[2];
                ts[count] = (float) (System.currentTimeMillis() - sTime) / 1000;
                count++;
            }
        } else if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            if (h_count >= h_size) {
                h = event.values[0];
                h_count = 0;
                //t = (System.currentTimeMillis()-sTime)/1000;
                mTextView.setText(String.format("X : %f\nY : %f\nZ : %f\nH : %f", x, y, z, h));
                SEND_DATA = "";
                for (int i = 0; i < h_rate.length; i++) {
                    SEND_DATA += 1 + ">" + h_rate[i] + ">" + th_rate[i] + ",";
                }
                if (mNode != null) {
                    Wearable.MessageApi.sendMessage(mGoogleApiClient, mNode, SEND_DATA, null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult result) {
                            if (!result.getStatus().isSuccess()) {
                                Log.d(TAG, "ERROR : failed to send Message" + result.getStatus());
                            }
                        }
                    });
                }
            } else {
                h_rate[h_count] = event.values[0];
                th_rate[h_count] = (float) (System.currentTimeMillis() - sTime) / 1000;
                h_count++;
            }
        }
    }

    private void checkPermission() {
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // パーミッションの許可を取得する
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1000);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d("DEBUG", "called onLocationChanged");
        Log.d("DEBUG", "lat : " + location.getLatitude());
        Log.d("DEBUG", "lon : " + location.getLongitude());
    }
    private void initLocationManager() {
        // インスタンス生成
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // 詳細設定
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_HIGH);
        criteria.setSpeedRequired(false);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setCostAllowed(true);
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);

        bestProvider = mLocationManager.getBestProvider(criteria, true);
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d("DEBUG", "called onStatusChanged");
        switch (status) {
            case LocationProvider.AVAILABLE:
                Log.d("DEBUG", "AVAILABLE");
                break;
            case LocationProvider.OUT_OF_SERVICE:
                Log.d("DEBUG", "OUT_OF_SERVICE");
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                Log.d("DEBUG", "TEMPORARILY_UNAVAILABLE");
                break;
            default:
                Log.d("DEBUG", "DEFAULT");
                break;
        }
    }
    private void locationStart() {
        checkPermission();
        mLocationManager.requestLocationUpdates(bestProvider, 60000, 3, this);
    }
    @Override
    public void onProviderDisabled(String provider) {
        Log.d("DEBUG", "called onProviderDisabled");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d("DEBUG", "called onProviderEnabled");
    }

}
