package com.hf.slowmodemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;

public class VideoViewActivity extends AppCompatActivity {

    private static final int MSG_UPDATE_PROGRESS = 1;
    private SurfaceView mSurfaceView;
    private Button mPlayPauseButton;
    private Spinner mSpeed;
    private SeekBar mSeekBar;
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mFps;

    private float[] mSpeedEntryValue = null;

    MediaPlayer mMediaPlayer = null;

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_PROGRESS:
                    int cur = mMediaPlayer.getCurrentPosition();
                    mCurrentTime.setText(String.valueOf(((float) cur) / 1000.0f));
                    mSeekBar.setProgress(cur);
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, 1000);
                    break;
                default:
                    ;
            }
            return false;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_view);

        mSurfaceView = (SurfaceView) findViewById(R.id.surface);
        mPlayPauseButton = (Button) findViewById(R.id.play_pause);
        mSpeed = (Spinner) findViewById(R.id.speed);
        mSeekBar = (SeekBar) findViewById(R.id.seekBar);
        mCurrentTime = (TextView) findViewById(R.id.current_time);
        mTotalTime = (TextView) findViewById(R.id.total_time);
        mFps = (TextView) findViewById(R.id.fps);

        initSurfaceWithPermission();

        mPlayPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                } else {
                    play();
                }
            }
        });

        mSpeed.setSelection(getResources().getInteger(R.integer.speed_default));
        mSpeed.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSpeed();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void updateSpeed() {
        float speed = getSpeedByPosition(mSpeed.getSelectedItemPosition());
        setSpeed(speed);
    }

    private float getSpeedByPosition(int position) {
        if (mSpeedEntryValue == null) {
            int[] rawValue = getResources().getIntArray(R.array.speed_entry_value);
            mSpeedEntryValue = new float[rawValue.length];
            for (int i=0; i<rawValue.length; i++) {
                mSpeedEntryValue[i] = ((float)rawValue[i]) / 100.0f;
            }
        }
        return mSpeedEntryValue[position];
    }

    private void setSpeed(float speed) {
        PlaybackParams pp = mMediaPlayer.getPlaybackParams();
        pp.setSpeed(speed);
        mMediaPlayer.setPlaybackParams(pp);
    }

    private void play() {
        mMediaPlayer.start();
        mPlayPauseButton.setText("Pause");
        mHandler.removeMessages(MSG_UPDATE_PROGRESS);
        mHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
    }

    private void pause() {
        mMediaPlayer.pause();
        mPlayPauseButton.setText("Play");
        mHandler.removeMessages(MSG_UPDATE_PROGRESS);
    }

    private void initSurfaceWithPermission() {
        if (PackageManager.PERMISSION_GRANTED == checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            initSurface();
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }
    }

    private void initSurface() {
        getMediaInfo();

        mFps.setText("FPS: " + mFrameRage);

        mMediaPlayer = new MediaPlayer();
        try {
            mMediaPlayer.setDataSource(this, getIntent().getData());
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);

        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                updateSpeed();
                int cur = mMediaPlayer.getCurrentPosition();
                int max = mMediaPlayer.getDuration();
                mCurrentTime.setText(String.valueOf(((float) cur) / 1000.0f));
                mTotalTime.setText(String.valueOf(((float) max) / 1000.0f));
                mSeekBar.setMax(max);
                mSeekBar.setProgress(cur);
                play();
            }
        });

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mPlayPauseButton.setText("Play");
            }
        });

        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.i("==MyTest==", "surfaceCreated()");
                mMediaPlayer.setDisplay(mSurfaceView.getHolder());
                try {
                    mMediaPlayer.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mPlayPauseButton.setEnabled(true);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initSurface();
            }
        }
    }

    private float mFrameRage;
    private int mWidth;
    private int mHeight;
    private void getMediaInfo() {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(this, getIntent().getData(), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int count = extractor.getTrackCount();
        long maxDuration = 0;
        for (int i=0; i<count; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            if (format.containsKey(MediaFormat.KEY_MIME)
                    && format.containsKey(MediaFormat.KEY_WIDTH)
                    && format.containsKey(MediaFormat.KEY_HEIGHT)
                    && format.containsKey(MediaFormat.KEY_DURATION)) {
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    long duration = format.getLong(MediaFormat.KEY_DURATION);
                    if (duration > maxDuration) {
                        try {
                            mFrameRage = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                        } catch (Exception e) {
                            // this key may be float
                            mFrameRage = format.getFloat(MediaFormat.KEY_FRAME_RATE);
                        }
                        mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                        mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);;
                        maxDuration = duration;
                    }
                }
            }
        }
    }
}
