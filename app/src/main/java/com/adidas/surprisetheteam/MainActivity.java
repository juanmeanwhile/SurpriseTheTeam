package com.adidas.surprisetheteam;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.fence.AwarenessFence;
import com.google.android.gms.awareness.fence.DetectedActivityFence;
import com.google.android.gms.awareness.fence.FenceState;
import com.google.android.gms.awareness.fence.FenceUpdateRequest;
import com.google.android.gms.awareness.fence.HeadphoneFence;
import com.google.android.gms.awareness.fence.LocationFence;
import com.google.android.gms.awareness.snapshot.LocationResult;
import com.google.android.gms.awareness.state.HeadphoneState;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.ResultCallbacks;
import com.google.android.gms.common.api.Status;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "MainActivity";
    private static final String FENCE_RECEIVER_ACTION = "action_fence";
    private static final int MY_PERMISSION_LOCATION = 1314;
    private GoogleApiClient mGoogleClient;

    private PendingIntent mPendingIntent;
    private TextView mMessage;
    private boolean mGoogleClientConnected = false;
    private Location mLocation = null;
    private Button mButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMessage = (TextView) findViewById(R.id.message);
        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setUpFences(mLocation);
            }
        });
        mButton.setEnabled(false);

        mGoogleClient = new GoogleApiClient.Builder(this).enableAutoManage(this, this).addApi(Awareness.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        // Set up the PendingIntent that will be fired when the fence is triggered.
                        Intent intent = new Intent(FENCE_RECEIVER_ACTION);
                        mPendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, intent, 0);

                        getLocation();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                    }
                }).build();
        mGoogleClient.connect();
    }

    @Override
    public void onStart() {
        super.onStart();

        Intent intent = new Intent(FENCE_RECEIVER_ACTION);
        registerReceiver(mMyFenceReceiver, new IntentFilter(FENCE_RECEIVER_ACTION));
    }

    @Override
    public void onStop() {
        super.onStop();

        unregisterReceiver(mMyFenceReceiver);
    }

    private void getLocation() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_LOCATION);
            return;
        } else {
            Awareness.SnapshotApi.getLocation(mGoogleClient).setResultCallback(new ResultCallback<LocationResult>() {
                @Override
                public void onResult(@NonNull LocationResult locationResult) {
                    if (!locationResult.getStatus().isSuccess()) {
                        Log.e(TAG, "Could not get location.");
                        return;
                    }
                    mLocation = locationResult.getLocation();
                    Log.i(TAG, "Lat: " + mLocation.getLatitude() + ", Lon: " + mLocation.getLongitude());

                    mButton.setEnabled(true);
                }
            });
        }
    }

    private void setUpFences(Location location) {

        // Create the primitive fences.
        AwarenessFence walkingFence = DetectedActivityFence.during(DetectedActivityFence.WALKING);
        //noinspection MissingPermission
        AwarenessFence enteringShop = LocationFence.entering(location.getLatitude(), location.getLongitude(), 20d);
        AwarenessFence headphoneFence = HeadphoneFence.during(HeadphoneState.PLUGGED_IN);

        // Create a combination fence to AND primitive fences.
        AwarenessFence walkingWithHeadphones = AwarenessFence.and(
                headphoneFence, enteringShop
        );

        registerFence("myFence", walkingWithHeadphones);

        registerReceiver(mMyFenceReceiver, new IntentFilter(FENCE_RECEIVER_ACTION));
    }

    private BroadcastReceiver mMyFenceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            FenceState fenceState = FenceState.extract(intent);

            if (TextUtils.equals(fenceState.getFenceKey(), "myFence")) {
                switch(fenceState.getCurrentState()) {
                    case FenceState.TRUE:
                        mMessage.setText("Headphones and meters!");
                        Log.i(TAG, "Headphones are plugged in.");
                        showNotification(true);
                        break;
                    case FenceState.FALSE:
                        mMessage.setText("Out of fence");
                        Log.i(TAG, "Headphones are NOT plugged in.");
                        showNotification(false);
                        break;
                    case FenceState.UNKNOWN:
                        mMessage.setText("unknown");
                        Log.i(TAG, "The headphone fence is in an unknown state.");
                        break;
                }
            }
        }
    };

    protected void registerFence(final String fenceKey, final AwarenessFence fence) {
        Awareness.FenceApi.updateFences(
                mGoogleClient,
                new FenceUpdateRequest.Builder()
                        .addFence(fenceKey, fence, mPendingIntent)
                        .build())
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if(status.isSuccess()) {
                            Log.i(TAG, "Fence was successfully registered.");
                            //queryFence(fenceKey);
                        } else {
                            Log.e(TAG, "Fence could not be registered: " + status);
                        }
                    }
                });
    }

    protected void unregisterFence(final String fenceKey) {
        Awareness.FenceApi.updateFences(
                mGoogleClient,
                new FenceUpdateRequest.Builder()
                        .removeFence(fenceKey)
                        .build()).setResultCallback(new ResultCallbacks<Status>() {
            @Override
            public void onSuccess(@NonNull Status status) {
                Log.i(TAG, "Fence " + fenceKey + " successfully removed.");
            }

            @Override
            public void onFailure(@NonNull Status status) {
                Log.i(TAG, "Fence " + fenceKey + " could NOT be removed.");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSION_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    getLocation();

                } else {
                    Log.i(TAG, "Location permission denied.  Weather snapshot skipped.");
                }
            }
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(this, "Problem with google services", Toast.LENGTH_SHORT).show();
    }

    public void showNotification(boolean fenceIn) {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_whatshot_white_24dp)
                        .setContentTitle("Fence " + (fenceIn?"in":"out"))
                        .setContentText("Your enter the fence!");

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNotificationManager.notify(0, mBuilder.build());
    }
}
