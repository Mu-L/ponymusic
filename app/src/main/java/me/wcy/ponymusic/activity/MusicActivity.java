package me.wcy.ponymusic.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import butterknife.Bind;
import me.wcy.ponymusic.R;
import me.wcy.ponymusic.adapter.FragmentAdapter;
import me.wcy.ponymusic.fragment.LocalMusicFragment;
import me.wcy.ponymusic.fragment.OnlineMusicFragment;
import me.wcy.ponymusic.fragment.PlayFragment;
import me.wcy.ponymusic.model.MusicInfo;
import me.wcy.ponymusic.service.PlayService;
import me.wcy.ponymusic.utils.CoverLoader;
import me.wcy.ponymusic.utils.MusicUtils;

public class MusicActivity extends BaseActivity implements View.OnClickListener, PlayService.OnPlayerEventListener {
    @Bind(R.id.toolbar)
    Toolbar mToolbar;
    @Bind(R.id.tabs)
    TabLayout mTabLayout;
    @Bind(R.id.viewpager)
    ViewPager mViewPager;
    @Bind(R.id.fl_playbar)
    FrameLayout flPlayBar;
    @Bind(R.id.iv_playbar_cover)
    ImageView ivPlayBarCover;
    @Bind(R.id.tv_playbar_title)
    TextView tvPlayBarTitle;
    @Bind(R.id.tv_playbar_artist)
    TextView tvPlayBarArtist;
    @Bind(R.id.iv_playbar_play)
    ImageView ivPlayBarPlay;
    @Bind(R.id.iv_playbar_next)
    ImageView ivPlayBarNext;
    @Bind(R.id.pb)
    ProgressBar pb;
    private LocalMusicFragment mLocalMusicFragment;
    private OnlineMusicFragment mOnlineMusicFragment;
    private PlayFragment mPlayFragment;
    private PlayService mPlayService;
    private PlayServiceConnection mPlayServiceConnection;
    private boolean isPlayingFragmentShow = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music);

        Intent intent = new Intent();
        intent.setClass(this, PlayService.class);
        mPlayServiceConnection = new PlayServiceConnection();
        bindService(intent, mPlayServiceConnection, Context.BIND_AUTO_CREATE);

        setSupportActionBar(mToolbar);
        setupViewPager();
    }

    @Override
    protected void setListener() {
        flPlayBar.setOnClickListener(this);
        ivPlayBarPlay.setOnClickListener(this);
        ivPlayBarNext.setOnClickListener(this);
    }

    private void setupViewPager() {
        mLocalMusicFragment = new LocalMusicFragment();
        mOnlineMusicFragment = new OnlineMusicFragment();
        FragmentAdapter adapter = new FragmentAdapter(getSupportFragmentManager());
        adapter.addFragment(mLocalMusicFragment, "本地音乐");
        adapter.addFragment(mOnlineMusicFragment, "在线音乐");
        mViewPager.setAdapter(adapter);
        mTabLayout.setupWithViewPager(mViewPager);
    }

    /**
     * execute in worker thread
     *
     * @param progress 播放进度
     */
    @Override
    public void onPublish(int progress) {
        pb.setProgress(progress);
        if (mPlayFragment != null) {
            mPlayFragment.onPublish(progress);
        }
    }

    @Override
    public void onChange(int position) {
        onPlay(position);
        if (mPlayFragment != null) {
            mPlayFragment.onChange(position);
        }
    }

    @Override
    public void onPlayerPause() {
        ivPlayBarPlay.setImageResource(R.drawable.ic_playbar_btn_play);
        if (mPlayFragment != null) {
            mPlayFragment.onPlayerPause();
        }
    }

    @Override
    public void onPlayerResume() {
        ivPlayBarPlay.setImageResource(R.drawable.ic_playbar_btn_pause);
        if (mPlayFragment != null) {
            mPlayFragment.onPlayerResume();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fl_playbar:
                showPlayingFragment();
                break;
            case R.id.iv_playbar_play:
                play();
                break;
            case R.id.iv_playbar_next:
                next();
                break;
        }
    }

    public void onPlay(int position) {
        if (MusicUtils.sMusicList.isEmpty()) {
            return;
        }

        MusicInfo musicInfo = MusicUtils.sMusicList.get(position);
        Bitmap cover = CoverLoader.getInstance().loadThumbnail(musicInfo.getCoverUri());
        ivPlayBarCover.setImageBitmap(cover);
        tvPlayBarTitle.setText(musicInfo.getTitle());
        tvPlayBarArtist.setText(musicInfo.getArtist());
        if (getPlayService().isPlaying()) {
            ivPlayBarPlay.setImageResource(R.drawable.ic_playbar_btn_pause);
        } else {
            ivPlayBarPlay.setImageResource(R.drawable.ic_playbar_btn_play);
        }
        pb.setMax((int) musicInfo.getDuration());
        pb.setProgress(0);

        mLocalMusicFragment.onItemPlay(position);
    }

    private void play() {
        if (getPlayService().isPlaying()) {//正在播放
            getPlayService().pause();
        } else {
            if (getPlayService().isPause()) {//暂停
                getPlayService().resume();
            } else {//还未开始播放
                getPlayService().play(getPlayService().getPlayingPosition());
            }
        }
    }

    private void next() {
        getPlayService().next();
    }

    public PlayService getPlayService() {
        return mPlayService;
    }

    private class PlayServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mPlayService = ((PlayService.PlayBinder) service).getService();
            mPlayService.setOnPlayEventListener(MusicActivity.this);
            onChange(mPlayService.getPlayingPosition());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }

    private void showPlayingFragment() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (mPlayFragment == null) {
            mPlayFragment = new PlayFragment();
            ft.replace(android.R.id.content, mPlayFragment);
            ft.addToBackStack(null);
        } else {
            ft.show(mPlayFragment);
        }
        ft.commit();
        isPlayingFragmentShow = true;
    }

    private void hidePlayingFragment() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.hide(mPlayFragment);
        ft.commit();
        isPlayingFragmentShow = false;
    }

    @Override
    public void onBackPressed() {
        if (mPlayFragment != null && isPlayingFragmentShow) {
            hidePlayingFragment();
        } else {
            //moveTaskToBack(false);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        unbindService(mPlayServiceConnection);
        super.onDestroy();
    }
}