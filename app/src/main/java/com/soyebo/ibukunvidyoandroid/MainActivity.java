package com.soyebo.ibukunvidyoandroid;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Toast;

import com.soyebo.ibukunvidyoandroid.databinding.ActivityMainBinding;
import com.vidyo.VidyoClient.Connector.ConnectorPkg;
import com.vidyo.VidyoClient.Connector.Connector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends AppCompatActivity implements Connector.IConnect {

    private Connector mVidyoConnector = null;
    private boolean mHideConfig = false;
    private boolean mAutoJoin = false;
    private boolean mAllowReconnect = true;
    private boolean mCameraPrivacy = false;
    private boolean mMicrophonePrivacy = false;
    private boolean mEnableDebug = false;
    private boolean mAutoReconnect = false;
    private boolean mDisableVideo = false;
    private boolean mVideoWasDisabled = false;
    private boolean mVidyoCloudJoin = false;
    private final int PERMISSIONS_REQUEST_ALL = 1988;
    private CountDownTimer mReconnectTimer = null;
    private final String mPortalName = "leadway.platform.vidyo.io";
    private final String mDisplayName = "android";
    private final String mRoomKey = "wh6H4sRBg6";
    private final String mRoomPin = "";

    private final String DEFAULT_LOG_LEVELS_AND_CATEGORIES = "warning info@VidyoClient info@LmiPortalSession info@LmiPortalMembership info@LmiResourceManagerUpdates info@LmiPace info@LmiIce";
    private final String DEBUG_LOG_LEVELS_AND_CATEGORIES = "warning debug@VidyoClient all@LmiPortalSession all@LmiPortalMembership info@LmiResourceManagerUpdates info@LmiPace info@LmiIce all@LmiSignaling";


    private Logger mLogger = Logger.getInstance();
    private VidyoConnectorState mVidyoConnectorState = VidyoConnectorState.Disconnected;


    enum VidyoConnectorState {
        Connecting,
        Connected,
        Disconnecting,
        Disconnected,
        DisconnectedUnexpected,
        Failure,
        FailureInvalidResource
    }


    private static final String[] mPermissions = new String[] {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE
    };

    private static final Map<VidyoConnectorState, String> mStateDescription = new HashMap<VidyoConnectorState, String>() {{
        put(VidyoConnectorState.Connecting, "Connecting...");
        put(VidyoConnectorState.Connected, "Connected");
        put(VidyoConnectorState.Disconnecting, "Disconnecting...");
        put(VidyoConnectorState.Disconnected, "Disconnected");
        put(VidyoConnectorState.DisconnectedUnexpected, "Unexpected disconnection");
        put(VidyoConnectorState.Failure, "Connection failed");
        put(VidyoConnectorState.FailureInvalidResource, "Invalid Resource ID");
    }};

    private int remoteParticipants = 15;
    ActivityMainBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        ConnectorPkg.setApplicationUIContext(this);
        ConnectorPkg.initialize();
        mBinding.btnConnect.setOnClickListener(v -> ConnectToRoom(mBinding.conLayVideo, mPortalName, mDisplayName, mRoomKey, mRoomPin));
        if (Build.VERSION.SDK_INT > 22) {
            List<String> permissionsNeeded = new ArrayList<>();
            for (String permission : mPermissions) {
                // Check if the permission has already been granted.
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                    permissionsNeeded.add(permission);
            }
            if (permissionsNeeded.size() > 0) {
                // Request any permissions which have not been granted. The result will be called back in onRequestPermissionsResult.
                ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSIONS_REQUEST_ALL);
            } else {
                // Begin listening for video view size changes.
                this.startVideoViewSizeListener();
            }
        } else {
            // Begin listening for video view size changes.
            this.startVideoViewSizeListener();
        }
        String logLevel = mEnableDebug? DEBUG_LOG_LEVELS_AND_CATEGORIES : DEFAULT_LOG_LEVELS_AND_CATEGORIES;
        mVidyoConnector = new Connector(mBinding.conLayVideo,
                Connector.ConnectorViewStyle.VIDYO_CONNECTORVIEWSTYLE_Default,
                remoteParticipants,
                logLevel,
                "",
                0);
        mVidyoConnector.assignViewToCompositeRenderer(mBinding.conLayVideo, Connector.ConnectorViewStyle.VIDYO_CONNECTORVIEWSTYLE_Default, remoteParticipants);
        mBinding.btnSwitchCamera.setOnClickListener(v -> onSwitchCameraPressed());
        mBinding.btnMicrophone.setOnClickListener(v -> onMicrophoneButtonPressed());
        mBinding.btnConnect.setOnClickListener(v -> onConnectPressed());
        mBinding.btnCameraPrivacy.setOnClickListener(v -> onVideoPrivacyPressed());



    }

   private void changeState(VidyoConnectorState state) {
        mLogger.Log("changeState: " + state.toString());

        mVidyoConnectorState = state;

        // Execute this code on the main thread since it is updating the UI layout.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Set the status text in the toolbar.
                Toast.makeText(getBaseContext(), mStateDescription.get(mVidyoConnectorState), Toast.LENGTH_SHORT).show();

                // Depending on the state, do a subset of the following:
                // - update the toggle connect button to either start call or end call image: mToggleConnectButton
                // - display toolbar in case it is hidden: mToolbarLayout
                // - show/hide the connection spinner: mConnectionSpinner
                // - show/hide the input form: mControlsLayout
                switch (mVidyoConnectorState) {
                    case Connecting:
                        mBinding.btnConnect.setChecked(true);
                        mBinding.pbVidyo.setVisibility(View.VISIBLE);
                        break;

                    case Connected:
                        mBinding.btnConnect.setChecked(true);
//                        mControlsLayout.setVisibility(View.GONE);
                        mBinding.pbVidyo.setVisibility(View.INVISIBLE);
//                        mHighFrameRateShareSwitch.setVisibility(View.VISIBLE);

                        // Keep the device awake if connected.
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        break;

                    case Disconnecting:
                        // The button just switched to the callStart image.
                        // Change the button back to the callEnd image because do not want to assume that the Disconnect
                        // call will actually end the call. Need to wait for the callback to be received
                        // before swapping to the callStart image.
                        mBinding.btnConnect.setChecked(true);
//                        mHighFrameRateShareSwitch.setVisibility(View.INVISIBLE);
                        break;

                    case Disconnected:
                    case DisconnectedUnexpected:
                    case Failure:
                    case FailureInvalidResource:
                        mBinding.btnConnect.setChecked(false);
//                        mToolbarLayout.setVisibility(View.VISIBLE);
                        mBinding.pbVidyo.setVisibility(View.INVISIBLE);

                        // If a return URL was provided as an input parameter, then return to that application
//                        if (mReturnURL != null) {
//                            // Provide a callstate of either 0 or 1, depending on whether the call was successful
//                            Intent returnApp = getPackageManager().getLaunchIntentForPackage(mReturnURL);
//                            returnApp.putExtra("callstate", (mVidyoConnectorState == VidyoConnectorState.Disconnected) ? 1 : 0);
//                            startActivity(returnApp);
//                        }

                        // If the allow-reconnect flag is set to false and a normal (non-failure) disconnect occurred,
                        // then disable the toggle connect button, in order to prevent reconnection.
                        if (!mAllowReconnect && (mVidyoConnectorState == VidyoConnectorState.Disconnected)) {
                            mBinding.btnConnect.setEnabled(false);
                            Toast.makeText(getBaseContext(), "Call Ended", Toast.LENGTH_SHORT).show();
                            ;
                        }

                        if (!mHideConfig ) {
                            // Display the form.
//                            mControlsLayout.setVisibility(View.VISIBLE);
                        }

                        // Allow the device to sleep if disconnected.
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        break;
                }
            }
        });
    }


    private void startVideoViewSizeListener() {
        mLogger.Log("startVideoViewSizeListener");

        // Render the video each time that the video view (mVideoFrame) is resized. This will
        // occur upon activity creation, orientation changes, and when foregrounding the app.
        ViewTreeObserver viewTreeObserver = mBinding.conLayVideo.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // Specify the width/height of the view to render to.
                    mLogger.Log("showViewAt: width = " + mBinding.conLayVideo.getWidth() + ", height = " + mBinding.conLayVideo.getHeight());
                    mVidyoConnector.showViewAt(mBinding.conLayVideo, 0, 0, mBinding.conLayVideo.getWidth(), mBinding.conLayVideo.getHeight());
//                    mOnGlobalLayoutListener = this;
                }
            });
        } else {
            mLogger.Log("ERROR in startVideoViewSizeListener! Video will not be rendered.");
        }
    }

    private void ConnectToRoom(ConstraintLayout viewId, String portal, String displayName, String roomKey, String roomPin) {
        mVidyoConnector = new Connector(viewId, Connector.ConnectorViewStyle.VIDYO_CONNECTORVIEWSTYLE_Default, remoteParticipants, "warning all@VidyoConnector info@VidyoClient", "", 0);
        mVidyoConnector.showViewAt(viewId, 0, 0, viewId.getWidth(), viewId.getHeight());
        mVidyoConnector.connectToRoomAsGuest(portal, displayName, roomKey, roomPin, this);
    }

    @Override
    public void onSuccess() {

    }

    @Override
    public void onFailure(Connector.ConnectorFailReason connectorFailReason) {

    }

    @Override
    public void onDisconnected(Connector.ConnectorDisconnectReason connectorDisconnectReason) {

    }

    private void onSwitchCameraPressed(){
        mVidyoConnector.cycleCamera();
    }

    private void onMicrophoneButtonPressed(){
        mMicrophonePrivacy = mBinding.btnMicrophone.isChecked();
        mVidyoConnector.setMicrophonePrivacy(mMicrophonePrivacy);
    }

    public void onConnectPressed() {
        if (mBinding.btnConnect.isChecked()) {
            // Connect to either a Vidyo.io resource or a VidyoCloud Vidyo room.
            if (!mVidyoCloudJoin) {
                // Connect to a Vidyo.io resource.
                this.changeState(VidyoConnectorState.Connecting);

                if (!mVidyoConnector.connectToRoomAsGuest(
                        mPortalName,
                        mDisplayName,
                        mRoomKey,
                        mRoomPin,
                        this)) {
                    // Connect failed.
                    this.changeState(VidyoConnectorState.Failure);
                }

            mLogger.Log("VidyoConnectorConnect status = " + (mVidyoConnectorState == VidyoConnectorState.Connecting));
        } else {
            // The user is either connected to a resource or is in the process of connecting to a resource;
            // Call VidyoConnectorDisconnect to either disconnect or abort the connection attempt.
            if(mReconnectTimer != null) {
                mReconnectTimer.cancel();
                mReconnectTimer = null;
            }
            this.changeState(VidyoConnectorState.Disconnecting);
            mVidyoConnector.disconnect();
        }
    }

}

    public void onVideoPrivacyPressed(){
        mCameraPrivacy = mBinding.btnCameraPrivacy.isChecked();
        mVidyoConnector.setCameraPrivacy(mCameraPrivacy);
    }
}