package com.example.ljd.myscreencaptureservice;


import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.example.ljd.myscreencaptureservice.rtspserver.SessionBuilder;
import com.example.ljd.myscreencaptureservice.rtspserver.rtsp.RtspServer;
import com.example.ljd.myscreencaptureservice.rtspserver.video.VideoQuality;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private MediaProjectionManager mMediaProjectionManager;
    public static MediaProjection mMediaProjection;
    ToggleButton tbtScreenCaptureService;

    private RtspServer mRtspServer;

    private ScreenCaptureService.MyBinder mBinder;
    private boolean SERVICE_HAS_BIND = false;
    private boolean SERVICE_IS_START = false;
    private boolean SC_IS_RUN = false;

    private static final int CAPTURE_CODE = 115;
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 123;
    private static final int INTERNET_REQUEST_CODE = 124;
    private int mScreenDensity;
    private int mScreenWidth;
    private int mScreenHeight;

    private String IPAddress = "192.168.1.33";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tbtScreenCaptureService = (ToggleButton) findViewById(R.id.tbt_screen_capture_service);

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        ReadRunState();
        if (!SC_IS_RUN) {
            startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), CAPTURE_CODE);
        }
        GetWindowInfo();

        RtspServer.setContext(getApplicationContext());
        myBindService();

        tbtScreenCaptureService.setChecked(SC_IS_RUN);
        tbtScreenCaptureService.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (SC_IS_RUN) {
                    ReadRunState();
                    if (SC_IS_RUN) {
                        mBinder.StopScreenCapture();
                        SC_IS_RUN = false;
                        SaveRunState(SERVICE_IS_START, SERVICE_HAS_BIND, SC_IS_RUN);
                        Toast.makeText(MainActivity.this, "屏幕录制服务停止运行", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "屏幕录制服务并没有开启，操作无效", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    ReadRunState();
                    if (!SC_IS_RUN) {
                        if (Build.VERSION.SDK_INT >= 23) {
                            //如果API-23编译，使用Android运行时权限
                            AskForPermission();
                        }
                        myShareScreen();
                        Toast.makeText(MainActivity.this, "屏幕录制服务开始运行", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "屏幕录制服务已经开启，操作无效", Toast.LENGTH_SHORT).show();
                    }

                }
                tbtScreenCaptureService.setChecked(SC_IS_RUN);
            }

        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v("onActivityResult","onActivityResult");
        if (requestCode == CAPTURE_CODE) {
            if (resultCode != RESULT_OK) {
                return;
            }else{
                mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode,data);

            }
            SetSessionBuilder();
        }
    }
    private void AskForPermission(){
        Log.v("AskForPermission()","AskForPermission()");
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED){
            Log.v("AskForPermission()","requestPermissions");
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
        }else{
            Log.v("onActivityResult","myThread.start(); start");
        }
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED){
            Log.v("AskForPermission()","requestPermissions");
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.INTERNET},INTERNET_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.v("PermissionsResult","onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case WRITE_EXTERNAL_STORAGE_REQUEST_CODE:
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Log.v("PermissionsResult","myThread.start(); start");
                }else{
                    Log.i("PermissionsResult","WRITE_EXTERNAL_STORAGE permission denied");
                }
                break;
            default:
                break;
        }
    }


    //绑定服务时调用
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBinder = (ScreenCaptureService.MyBinder)service;
        }
        //解绑时不会调用
        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
    private RtspServer.CallbackListener mRtspCallbackListener = new RtspServer.CallbackListener() {

        @Override
        public void onError(RtspServer server, Exception e, int error) {
            // We alert the user that the port is already used by another app.
            if (error == RtspServer.ERROR_BIND_FAILED) {

            }
        }

        @Override
        public void onMessage(RtspServer server, int message) {
        }

    };

    private void GetWindowInfo(){
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager)getApplication().getSystemService(getApplication().WINDOW_SERVICE);
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mScreenWidth = metrics.widthPixels/10;
        mScreenHeight = metrics.heightPixels/10;
        //mScreenDensity = 30;
//        mScreenWidth = 100;
//        mScreenHeight = 600;
        Log.v(TAG,"mScreenWidth is :"+mScreenWidth+";mScreenHeight is :"+mScreenHeight+"mScreenDensity is :"+mScreenDensity);
    }
    private void SetSessionBuilder(){
        Log.v(TAG,"SetSessionBuilder()");
        SessionBuilder.getInstance()
                .setMediaProjection(mMediaProjection)
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setDestination(IPAddress)
                .setVideoQuality(new VideoQuality(mScreenWidth,mScreenHeight,30,8000000,mScreenDensity)).build();
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(RtspServer.KEY_PORT, String.valueOf(1234));
        editor.commit();

    }
    private void myShareScreen(){
        Intent intent = new Intent(MainActivity.this, ScreenCaptureService.class);
        myStartService(intent);
        mBinder.StartScreenCapture();
        SC_IS_RUN = true;
        SaveRunState(SERVICE_IS_START,SERVICE_HAS_BIND,SC_IS_RUN);
    }

    @Override
    protected void onDestroy() {
        myUnbindService();
        myStopService();
        super.onDestroy();

    }

    private void myBindService(){
        ReadRunState();
        Intent bindIntent = new Intent(MainActivity.this, ScreenCaptureService.class);
        bindService(bindIntent, connection, BIND_AUTO_CREATE);
        SERVICE_HAS_BIND = true;
        SaveRunState(SERVICE_IS_START,SERVICE_HAS_BIND,SC_IS_RUN);
        Log.v(TAG,"bindService");
    }

    private void myUnbindService(){
        ReadRunState();
        if(SERVICE_HAS_BIND){
            unbindService(connection);
            SERVICE_HAS_BIND =false;
            Log.v(TAG,"unbindService");
        }
        SaveRunState(SERVICE_IS_START,SERVICE_HAS_BIND,SC_IS_RUN);
    }

    private void myStartService(Intent startIntent){
        ReadRunState();
        startService(startIntent);
        SERVICE_IS_START = true;
        SaveRunState(SERVICE_IS_START,SERVICE_HAS_BIND,SC_IS_RUN);
        Log.v(TAG,"startService");
    }

    private void myStopService(){
        ReadRunState();
        if((SERVICE_IS_START)&&(!SC_IS_RUN)) {
            Intent stopIntent = new Intent(MainActivity.this, ScreenCaptureService.class);
            stopService(stopIntent);
            SERVICE_IS_START = false;
            releaseEncoder();
            Log.v(TAG, "stopService");
        }
        SaveRunState(SERVICE_IS_START,SERVICE_HAS_BIND,SC_IS_RUN);
    }

    private void SaveRunState(boolean bSERVICE_IS_START,boolean bSERVICE_HAS_BIND,boolean bSC_IS_RUN){
        SC_IS_RUN = bSC_IS_RUN;
        SERVICE_HAS_BIND = bSERVICE_HAS_BIND;
        SERVICE_IS_START = bSERVICE_IS_START;
        //实例化SharedPreferences对象（第一步）
        SharedPreferences mySharedPreferences= getSharedPreferences(TAG, MODE_PRIVATE);
        //实例化SharedPreferences.Editor对象（第二步）
        SharedPreferences.Editor editor = mySharedPreferences.edit();
        //用putString的方法保存数据
        editor.putBoolean("SC_IS_RUN",SC_IS_RUN);
        editor.putBoolean("SERVICE_HAS_BIND",SERVICE_HAS_BIND);
        editor.putBoolean("SERVICE_IS_START",SERVICE_IS_START);
        //提交当前数据
        editor.commit();
        Log.v("SaveRunState","SERVICE_IS_START = "+SERVICE_IS_START+";SERVICE_HAS_BIND = "+SERVICE_HAS_BIND+";SC_IS_RUN = "+SC_IS_RUN);
    }

    private void ReadRunState(){
        //同样，在读取SharedPreferences数据前要实例化出一个SharedPreferences对象
        SharedPreferences sharedPreferences= getSharedPreferences(TAG, MODE_PRIVATE);
        // 使用getString方法获得value，注意第2个参数是value的默认值
        SC_IS_RUN = sharedPreferences.getBoolean("SC_IS_RUN",false);
        SERVICE_HAS_BIND = sharedPreferences.getBoolean("SERVICE_HAS_BIND",false);
        SERVICE_IS_START = sharedPreferences.getBoolean("SERVICE_IS_START",false);
        //Log.v("ReadRunState","SERVICE_IS_START = "+SERVICE_IS_START+";SERVICE_HAS_BIND = "+SERVICE_HAS_BIND+";SC_IS_RUN = "+SC_IS_RUN);
    }


    private void releaseEncoder() {
        Log.d(TAG, "releasing encoder objects");

        if(mMediaProjection!=null){
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

}
