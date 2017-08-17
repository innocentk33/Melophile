package com.vpaliy.melophile.ui.track;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.graphics.Palette;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.target.ImageViewTarget;
import com.ohoussein.playpause.PlayPauseView;
import com.vpaliy.melophile.App;
import com.vpaliy.melophile.R;
import com.vpaliy.melophile.di.component.DaggerViewComponent;
import com.vpaliy.melophile.di.module.PresenterModule;
import com.vpaliy.melophile.playback.MusicPlaybackService;
import com.vpaliy.melophile.ui.base.BaseFragment;
import com.vpaliy.melophile.ui.playlist.PlaylistFragment;
import com.vpaliy.melophile.ui.utils.Constants;

import butterknife.OnClick;
import de.hdodenhof.circleimageview.CircleImageView;
import android.support.annotation.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import jp.wasabeef.blurry.Blurry;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.bumptech.glide.gifdecoder.GifHeaderParser.TAG;

public class TrackFragment extends BaseFragment {

    @BindView(R.id.background)
    protected ImageView background;

    @BindView(R.id.end_time)
    protected TextView endTime;

    @BindView(R.id.start_time)
    protected TextView startTime;

    @BindView(R.id.circle)
    protected CircleImageView smallImage;

    @BindView(R.id.artist)
    protected TextView artist;

    @BindView(R.id.track_name)
    protected TextView trackName;

    @BindView(R.id.progressView)
    protected ProgressBar progress;

    @BindView(R.id.play_pause)
    protected PlayPauseView playPause;

    private String id;


    private static final long PROGRESS_UPDATE_INTERNAL = 100;
    private static final long PROGRESS_UPDATE_INITIAL_INTERVAL = 10;

    private final ScheduledExecutorService mExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> mScheduleFuture;

    private PlaybackStateCompat lastState;
    private Handler handler=new Handler();

    private MediaBrowserCompat browserCompat;
    private MediaBrowserCompat.ConnectionCallback connectionCallback=new MediaBrowserCompat.ConnectionCallback(){
        @Override
        public void onConnected()  {
            super.onConnected();
            MediaSessionCompat.Token token=browserCompat.getSessionToken();
            try {
                MediaControllerCompat mediaController =new MediaControllerCompat(getActivity(), token);
                // Save the controller
                mediaController.registerCallback(controllerCallback);
                MediaControllerCompat.setMediaController(getActivity(), mediaController);
                PlaybackStateCompat stateCompat=mediaController.getPlaybackState();
                updatePlaybackState(stateCompat);
                MediaMetadataCompat metadataCompat=mediaController.getMetadata();
                if(metadataCompat!=null){
                    updateDuration(metadataCompat);
                }
            }catch (RemoteException ex){
                ex.printStackTrace();
            }
        }
    };

    private MediaControllerCompat.Callback controllerCallback=new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            updatePlaybackState(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            if(metadata!=null) {
                updateDuration(metadata);
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        if(browserCompat!=null) {
            browserCompat.connect();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if(browserCompat!=null){
            browserCompat.disconnect();
        }
        if(MediaControllerCompat.getMediaController(getActivity())!=null){
            MediaControllerCompat.getMediaController(getActivity()).unregisterCallback(controllerCallback);
        }
    }



    private void startSeekBarUpdate(){
        mScheduleFuture = mExecutorService.scheduleAtFixedRate(()-> handler.post(TrackFragment.this::updateProgress),
                PROGRESS_UPDATE_INITIAL_INTERVAL, PROGRESS_UPDATE_INTERNAL, TimeUnit.MILLISECONDS);
    }

    private void stopSeekBarUpdate(){
        if(mScheduleFuture!=null) mScheduleFuture.cancel(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSeekBarUpdate();
        mExecutorService.shutdown();
    }

    @OnClick(R.id.play_pause)
    public void playPause(){
        MediaControllerCompat controllerCompat=MediaControllerCompat.getMediaController(getActivity());
        PlaybackStateCompat stateCompat=controllerCompat.getPlaybackState();
        if(stateCompat!=null){
            MediaControllerCompat.TransportControls controls=
                    controllerCompat.getTransportControls();
            switch (stateCompat.getState()){
                case PlaybackStateCompat.STATE_PLAYING:
                case PlaybackStateCompat.STATE_BUFFERING:
                    controls.pause();
                    break;
                case PlaybackStateCompat.STATE_NONE:
                case PlaybackStateCompat.STATE_PAUSED:
                case PlaybackStateCompat.STATE_STOPPED:
                    controls.play();
                    break;
                default:
                    Log.d(TAG, "onClick with state "+stateCompat.getState());
            }
        }
    }

    public void updatePlaybackState(PlaybackStateCompat stateCompat){
        if(stateCompat==null) return;
        lastState=stateCompat;
        switch (stateCompat.getState()){
            case PlaybackStateCompat.STATE_PLAYING:
                playPause.setVisibility(VISIBLE);
                if(playPause.isPlay()){
                    playPause.change(false,true);
                }
                startSeekBarUpdate();
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                // mControllers.setVisibility(VISIBLE);
                // mLoading.setVisibility(INVISIBLE);
                playPause.setVisibility(VISIBLE);
                if(!playPause.isPlay()){
                    playPause.change(true,true);
                }
                stopSeekBarUpdate();
                break;
            case PlaybackStateCompat.STATE_NONE:
            case PlaybackStateCompat.STATE_STOPPED:
                playPause.setVisibility(VISIBLE);
                if(playPause.isPlay()){
                    playPause.change(false,true);
                }
                stopSeekBarUpdate();
                break;
            case PlaybackStateCompat.STATE_BUFFERING:
                playPause.setVisibility(INVISIBLE);
                stopSeekBarUpdate();
                break;
            default:
                Log.d(TAG, "Unhandled state "+stateCompat.getState());
        }
    }

    @OnClick(R.id.next)
    public void playNext(){
        MediaControllerCompat controllerCompat=MediaControllerCompat.getMediaController(getActivity());
        MediaControllerCompat.TransportControls controls=
                controllerCompat.getTransportControls();
        controls.skipToNext();
    }

    @OnClick(R.id.prev)
    public void playPrev(){
        MediaControllerCompat controllerCompat=MediaControllerCompat.getMediaController(getActivity());
        MediaControllerCompat.TransportControls controls=
                controllerCompat.getTransportControls();
        controls.skipToPrevious();
    }

    private void updateProgress() {
        if(lastState==null) return;
        long currentPosition = lastState.getPosition();
        if (lastState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            long timeDelta = SystemClock.elapsedRealtime() -
                    lastState.getLastPositionUpdateTime();
            currentPosition += (int) timeDelta * lastState.getPlaybackSpeed();
        }
        progress.setProgress((int) currentPosition);
        startTime.setText(DateUtils.formatElapsedTime(progress.getProgress() / 1000));
    }


    public static TrackFragment newInstance(Bundle args){
        TrackFragment fragment=new TrackFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void initializeDependencies() {
        DaggerViewComponent.builder()
                .presenterModule(new PresenterModule())
                .applicationComponent(App.appInstance().appComponent())
                .build().inject(this);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getActivity().supportPostponeEnterTransition();
        browserCompat=new MediaBrowserCompat(getActivity(),
                new ComponentName(getActivity(), MusicPlaybackService.class),
                connectionCallback,null);
        extractId(savedInstanceState);
    }

    private void extractId(Bundle bundle){
        if(bundle==null) bundle=getArguments();
        id=bundle.getString(Constants.EXTRA_ID);
        Log.d(PlaylistFragment.class.getSimpleName(),"ID:"+id);
        showArt(bundle.getString(Constants.EXTRA_DATA));
    }

    public void showArt(String artUrl){
        Glide.with(getContext())
                .load(artUrl)
                .asBitmap()
                .priority(Priority.IMMEDIATE)
                .into(new ImageViewTarget<Bitmap>(smallImage) {
                    @Override
                    protected void setResource(Bitmap resource) {
                        smallImage.setImageBitmap(resource);
                        Blurry.with(getContext())
                                .async()
                                .from(resource)
                                .into(background);
                        getActivity().supportStartPostponedEnterTransition();
                    }
                });
    }

    private void updateDuration(MediaMetadataCompat metadataCompat){
        Log.d(TAG,"updatedDuration is called()");
        int duration=(int)metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        endTime.setText(DateUtils.formatElapsedTime(duration/1000));
        startTime.setText("0");
        progress.setMax(duration);
        //
        trackName.setText(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_TITLE));
        artist.setText(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_ARTIST));

        String imageUrl=metadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
    }


    @Override
    protected int layoutId() {
        return R.layout.fragment_player;
    }
}
