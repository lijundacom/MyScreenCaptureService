package com.example.ljd.myscreencaptureservice;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class ScreenCaptureService extends Service {

    public static final String TAG = "ScreenCaptureService";

    private MyBinder mBinder = new MyBinder();
    private int mScreenDensity;
    private int mScreenWidth;
    private int mScreenHeight;
    private static final String MIME_TYPE = "video/avc";
    private int mVideoTrackIndex;
    private int framerate = 15;
    private boolean isRun = false;
    MediaCodec.BufferInfo info;
    MediaFormat format;
    MediaCodec mediaCodec;
    private boolean VERBOS = true;
    private Surface mSurface;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MyThread myThread;

    private MediaMuxer mMediaMuxer;
    private static final String SDCARD_PATH  = Environment.getExternalStorageDirectory().getPath();
    private String FileName;

    private long totalSize = 0;

    public ScreenCaptureService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification.Builder builder = new Notification.Builder(this);
        Intent notificationIntent = new Intent(this,MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,0,notificationIntent,0);
        builder.setContentIntent(pendingIntent);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setTicker("屏幕录像前台服务开始");
        builder.setContentTitle("屏幕录像前台服务");
        builder.setContentText("屏幕录像前台服务正在运行");
        Notification notification = builder.build();
        startForeground(1,notification);

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager)getApplication().getSystemService(getApplication().WINDOW_SERVICE);
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
        Log.v(TAG,"mScreenWidth is :"+mScreenWidth+";mScreenHeight is :"+mScreenHeight);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }



    class MyBinder extends Binder {
        public void StartScreenCapture(){
            //setUpMediaProjection();

            //这两部先后顺序绝对不能乱，先prepareEncoder();再virtualDisplay();
            prepareEncoder();
            virtualDisplay();

            myThread = new MyThread();
            myThread.start();
            Log.v(TAG,"StartScreenCapture()");
            return;
        }

        public void StopScreenCapture(){
            isRun = false;
            Log.v(TAG,"StopScreenCapture()");
        }

    }
    class MyThread extends Thread{
        public void run(){
            try {
                Log.v("doNext","process()");
                process();
                //Thread.sleep(100);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected boolean process() throws IOException {
        Log.v("process()","process() start");
        isRun = true;
        File destDir = new File(SDCARD_PATH+"/ScreenCapture/");
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyyMMddhhmmss");
        FileName = sDateFormat.format(new java.util.Date());
        Log.v(TAG,"destDir is :"+destDir.toString());
        mMediaMuxer = new MediaMuxer(destDir.toString()+"/"+FileName+".mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        try{
            while(isRun){

                drainEncoder(false);
                Thread.sleep(200);
            }
            drainEncoder(true);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        finally {
            Log.v(TAG,"totalSize is :"+totalSize);
            releaseEncoder();
        }

        return true;
    }
    private void prepareEncoder(){
        info = new MediaCodec.BufferInfo();
        format = MediaFormat.createVideoFormat(MIME_TYPE,mScreenWidth,mScreenHeight);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 125000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        try {
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        mSurface = mediaCodec.createInputSurface();//mSurface必须用OpenGL绘制， lockCanvas会出错
        mediaCodec.start();
        Log.d(TAG, "encoder output format not changed: " + format);
        //没有设置format的SPS和PPS，所以format不能用
        //后面设置了
    }
    private void releaseEncoder() {
        Log.d(TAG, "releasing encoder objects");
        if (mMediaMuxer != null) {
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaMuxer = null;
        }
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }

        if(mVirtualDisplay!=null){
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if(mMediaProjection!=null){
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }
    private void drainEncoder(boolean endOfStream) {
        final int TIMEOUT_USEC = 10000;
        //Log.d(TAG, "drainEncoder(" + endOfStream + ")");

        if (endOfStream) {
            Log.d(TAG, "sending EOS to encoder");
            mediaCodec.signalEndOfInputStream();
        }
        /*encoderOutputBuffers是一个ByteBuffer的数组
        * ByteBuffer本身是一个数组
        * 所以encoderOutputBuffers是一个二维数组
        * 类似乒乓缓存一样，mediaCodec向这组数组中轮流写数据
        * */
        ByteBuffer[] encoderOutputBuffers = mediaCodec.getOutputBuffers();
        while (true) {

            int encoderStatus = mediaCodec.dequeueOutputBuffer(info, TIMEOUT_USEC);//dequeue出队，encoderStatus返回是写入多缓存的哪个缓存
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                //Log.v("drainEncoder","encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER");
                //-1
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    Log.d(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                //Log.v("drainEncoder","encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                // not expected for an encoder
                //-3
                encoderOutputBuffers = mediaCodec.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                //Log.v("drainEncoder","encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");
                /*
                * MediaCodec在一开始调用dequeueOutputBuffer()时会返回一次INFO_OUTPUT_FORMAT_CHANGED消息。
                * 我们只需在这里获取该MediaCodec的format，并注册到MediaMuxer里。
                * 这是一个技巧，大家都这么用
                * */
                MediaFormat newFormat = mediaCodec.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + newFormat);

                /*
                //从newFormat中提取SPS和PPS，设置进format
                //这样就可以用之前的format，format中包含码率、关键帧等信息
                //newFormat不包含
                ByteBuffer csd0 = newFormat.getByteBuffer("csd-0");
                ByteBuffer csd1 = newFormat.getByteBuffer("csd-1");
                format.setByteBuffer("csd-0",csd0);
                format.setByteBuffer("csd-1",csd1);
                mVideoTrackIndex = mMediaMuxer.addTrack(format);
                */
                mVideoTrackIndex = mMediaMuxer.addTrack(newFormat);//可以直接传入newFormat
                mMediaMuxer.start();
                // mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                //Log.v("drainEncoder","encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                //Log.v("drainEncoder","encoderStatus > 0");
                /*encodedData是二维数组encoderOutputBuffers中的第encoderStatus行
                *
                * */
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    info.size = 0;
                }

                if (info.size != 0) {
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
//                    encodedData.position(info.offset);
//                    encodedData.limit(info.offset + info.size);
                    mMediaMuxer.writeSampleData(mVideoTrackIndex, encodedData, info);
                    //从encodedData中可直接获取数据,而且是经过H.264压缩后的数据
                    //Log.d(TAG, "sent " + info.size + " bytes to muxer");
                }
                totalSize+=info.size;
                mediaCodec.releaseOutputBuffer(encoderStatus, false);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    //如果
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        Log.d(TAG, "end of stream reached");
                    }
                    break;      // out of while
                }
            }
        }
    }
    private void virtualDisplay() {
        mMediaProjection = MainActivity.mMediaProjection;
        if( mMediaProjection == null){
            Log.v("ScreenCaptureService","mMediaProjection is null");
        }
        Log.v(TAG, "virtual displayed");

        mMediaProjection.createVirtualDisplay("屏幕捕捉",mScreenWidth, mScreenHeight,
                mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface,null,null);

    }



}
