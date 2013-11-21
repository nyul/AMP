package hu.bme.andh.tabplayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MediaPlayerActivity extends Activity implements
		OnCompletionListener, SeekBar.OnSeekBarChangeListener, KeyEvent.Callback {

	private ImageButton btnPlay;
	private ImageButton btnForward;
	private ImageButton btnBackward;
	private ImageButton btnNext;
	private ImageButton btnPrevious;
	private ImageButton btnPlaylist;
	private ImageButton btnRepeat;
	private ImageButton btnShuffle;
	private SeekBar songProgressBar;
	private TextView songTitleLabel;
	private TextView songCurrentDurationLabel;
	private TextView songTotalDurationLabel;
	// Media Player
	private MediaPlayer mp;
	// Handler to update UI timer, progress bar etc,.
	private Handler mHandler = new Handler();
	private FileManager songManager;
	private Utilities utils;
	private int seekForwardTime = 5000; // 5000 milliseconds
	private int seekBackwardTime = 5000; // 5000 milliseconds
	private int currentSongIndex = 0;
	private boolean isShuffle = false;
	private boolean isRepeat = false;
	private ArrayList<HashMap<String, String>> songsList = new ArrayList<HashMap<String, String>>();
	// SharedPreferences to make settings persistent
	private SharedPreferences prefs;
	private String prefName = "MyPref";
	private static final String SHUFFLE_STATE = "shuffle_State";
	private static final String REPEAT_STATE = "repeat_State";

	// for loading seeking time from previous orientation
	int seekto = 0;

	// Declare headsetSwitch variable
	private int headsetSwitch = 1;

	// phone state, if there's a call
	private boolean isPausedInCall = false;
	private PhoneStateListener phoneStateListener;
	private TelephonyManager telephonyManager;

	private boolean isRunning = false;
	private boolean isMusicPaused = true;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		// All player buttons
		btnPlay = (ImageButton) findViewById(R.id.btnPlay);
		btnForward = (ImageButton) findViewById(R.id.btnForward);
		btnBackward = (ImageButton) findViewById(R.id.btnBackward);
		btnNext = (ImageButton) findViewById(R.id.btnNext);
		btnPrevious = (ImageButton) findViewById(R.id.btnPrevious);
		btnPlaylist = (ImageButton) findViewById(R.id.btnPlaylist);
		btnRepeat = (ImageButton) findViewById(R.id.btnRepeat);
		btnShuffle = (ImageButton) findViewById(R.id.btnShuffle);
		songProgressBar = (SeekBar) findViewById(R.id.songProgressBar);
		songTitleLabel = (TextView) findViewById(R.id.songTitle);
		songCurrentDurationLabel = (TextView) findViewById(R.id.songCurrentDurationLabel);
		songTotalDurationLabel = (TextView) findViewById(R.id.songTotalDurationLabel);

		mHandler = (Handler) getLastNonConfigurationInstance();
		// If not, make a new one
		if (mHandler == null) {
			mHandler = new Handler();
		}

		// Media player
		mp = new MediaPlayer();

		if (savedInstanceState != null) {
			seekto = savedInstanceState.getInt("currPos");
			currentSongIndex = savedInstanceState.getInt("currentSongIndex");
			isMusicPaused = savedInstanceState.getBoolean("isMusicPaused");
		}

		songManager = new FileManager(getApplicationContext());
		utils = new Utilities();

		// Listeners
		songProgressBar.setOnSeekBarChangeListener(this); // Important
		mp.setOnCompletionListener(this); // Important

		// Register headset receiver
		registerReceiver(headsetReceiver, new IntentFilter(
				Intent.ACTION_HEADSET_PLUG));

		// Getting all songs list
		songsList = songManager.getPlayList();

		if ((songsList == null) || songsList.isEmpty()) {
			btnPlay.setEnabled(false);
			btnBackward.setEnabled(false);
			btnForward.setEnabled(false);
			btnNext.setEnabled(false);
			btnPrevious.setEnabled(false);
			alert();
		} else {
			btnPlay.setEnabled(true);
			btnBackward.setEnabled(true);
			btnForward.setEnabled(true);
			btnNext.setEnabled(true);
			btnPrevious.setEnabled(true);
		}
		
		
		
		MediaButtonIntentReceiver mMediaButtonReceiver = new MediaButtonIntentReceiver();
		IntentFilter mediaFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
		mediaFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
		registerReceiver(mMediaButtonReceiver, mediaFilter);

		mp.reset();
		if (songsList.size()>0){
			try {
				mp.setDataSource(songsList.get(0).get("songPath"));
				mp.prepare();
			} catch (Exception e){
			}
		}

		// Manage incoming phone calls during playback. Pause mp on incoming,
		// resume on hangup.
		// -----------------------------------------------------------------------------------
		// Get the telephony manager
		telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		phoneStateListener = new PhoneStateListener() {
			@Override
			public void onCallStateChanged(int state, String incomingNumber) {
				switch (state) {
				case TelephonyManager.CALL_STATE_OFFHOOK:
				case TelephonyManager.CALL_STATE_RINGING:
					if (mp != null && !isMusicPaused) {
						mp.pause();
						cancelNotification();
						isPausedInCall = true;
					}

					break;
				case TelephonyManager.CALL_STATE_IDLE:
					// Phone idle. Start playing.
					if (mp != null && !isMusicPaused) {
						if (isPausedInCall ) {
							isPausedInCall = false;
							mp.start();
							initNotification();
						}
					}
					break;
				}
			}
		};

		// Register the listener with the telephony manager
		telephonyManager.listen(phoneStateListener,
				PhoneStateListener.LISTEN_CALL_STATE);

		/**
		 * Play button click event plays a song and changes button to pause
		 * image pauses a song and changes button to play image
		 * */
		btnPlay.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				LinearLayout llayout = (LinearLayout) findViewById(R.id.songThumbnail);
				AnimationSet anim = new AnimationSet(false);
				
				RotateAnimation rot1 = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
	            rot1.setDuration(2000); //You can manage the time of the blink with this parameter
	            rot1.setRepeatMode(Animation.RESTART);
	            rot1.setRepeatCount(Animation.INFINITE);
	            rot1.setInterpolator(new LinearInterpolator());
	            
				RotateAnimation rot2 = new RotateAnimation(0, 360, Animation.RELATIVE_TO_PARENT, 0.5f, Animation.RELATIVE_TO_PARENT, 0.5f);
	            rot2.setDuration(2000); //You can manage the time of the blink with this parameter
	            rot2.setRepeatMode(Animation.RESTART);
	            rot2.setRepeatCount(Animation.INFINITE);
	            rot2.setInterpolator(new LinearInterpolator());
	            
	            
	            Animation alpha1  = new AlphaAnimation(0.2f, 0.8f);
	            alpha1.setDuration(1000); //You can manage the time of the blink with this parameter
	            alpha1.setRepeatMode(Animation.REVERSE);
	            alpha1.setRepeatCount(Animation.INFINITE);
	            
//	            AlphaAnimation alpha2  = new AlphaAnimation(0.0f, 1.0f);
//	            alpha2.setDuration(1000); //You can manage the time of the blink with this parameter
//	            alpha2.setStartOffset(1000);
//	            alpha2.setRepeatMode(Animation.);
//	            alpha2.setRepeatCount(Animation.INFINITE);
	            
	            ScaleAnimation sc1 = new ScaleAnimation(0.8f, 1.2f, 0.8f, 0.55f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
	            sc1.setDuration(1000);
	            sc1.setRepeatMode(Animation.REVERSE);
	            sc1.setRepeatCount(Animation.INFINITE);
	            
//	            ScaleAnimation sc2 = new ScaleAnimation(1.5f, 1.0f, 0.75f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
//	            sc2.setDuration(1000);
//	            sc2.setStartOffset(1000);
//	            sc2.setRepeatMode(Animation.RESTART);
//	            sc2.setRepeatCount(Animation.INFINITE);
	            
	            anim.addAnimation(alpha1);
//	            anim.addAnimation(alpha2);
	            anim.addAnimation(rot1);
	            anim.addAnimation(rot2);
	            anim.addAnimation(sc1);
//	            anim.addAnimation(sc2);
	            
				ImageView img = (ImageView)llayout.getChildAt(0);
				
				img.startAnimation(anim);
				img.setAlpha(1.0f);
				
				// check for already playing
				if (mp.isPlaying()) {
					System.out.println("playing");
					if (mp != null) {
						mp.pause();
						cancelNotification();
						// Changing button image to play button
						btnPlay.setImageResource(R.drawable.btn_play);
						isMusicPaused = true;
					}
				} else {
					// Resume song
					System.out.println("resume");
					if (mp != null) {
						System.out.println("mp notnull, getcp: " + mp.getCurrentPosition());
						if (mp.getCurrentPosition() == 0) {
							playSong(currentSongIndex);
						}
						System.out.println("before mp start");
						mp.start();
						System.out.println("after mp start");
						initNotification();
						isMusicPaused = false;
						// Changing button image to pause button
						btnPlay.setImageResource(R.drawable.btn_pause);
						updateProgressBar();
					} else System.out.println("mp null");
				}

			}
		});

		/**
		 * Forward button click event Forwards song specified seconds
		 * */
		btnForward.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// get current song position
				int currentPosition = mp.getCurrentPosition();
				// check if seekForward time is lesser than song duration
				if (currentPosition + seekForwardTime <= mp.getDuration()) {
					// forward song
					mp.seekTo(currentPosition + seekForwardTime);
				} else {
					// forward to end position
					mp.seekTo(mp.getDuration());
				}
			}
		});

		/**
		 * Backward button click event Backward song to specified seconds
		 * */
		btnBackward.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// get current song position
				int currentPosition = mp.getCurrentPosition();
				// check if seekBackward time is greater than 0 sec
				if (currentPosition - seekBackwardTime >= 0) {
					// forward song
					mp.seekTo(currentPosition - seekBackwardTime);
				} else {
					// backward to starting position
					mp.seekTo(0);
				}

			}
		});

		/**
		 * Next button click event Plays next song by taking currentSongIndex +
		 * 1
		 * */
		btnNext.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				playSong(getNextSongId());
			}
		});

		/**
		 * Back button click event Plays previous song by currentSongIndex - 1
		 * */
		btnPrevious.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				playSong(getPrevSongId());
			}
		});

		/**
		 * Button Click event for Repeat button Enables repeat flag to true
		 * */
		btnRepeat.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				if (isRepeat) {
					isRepeat = false;
					Toast.makeText(getApplicationContext(), "Repeat is OFF",
							Toast.LENGTH_SHORT).show();
					btnRepeat.setImageResource(R.drawable.btn_repeat);
				} else {
					// make repeat to true
					isRepeat = true;
					Toast.makeText(getApplicationContext(), "Repeat is ON",
							Toast.LENGTH_SHORT).show();
					// make shuffle to false
					isShuffle = false;
					btnRepeat.setImageResource(R.drawable.btn_repeat_focused);
					btnShuffle.setImageResource(R.drawable.btn_shuffle);
				}
			}
		});

		/**
		 * Button Click event for Shuffle button Enables shuffle flag to true
		 * */
		btnShuffle.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				if (isShuffle) {
					isShuffle = false;
					Toast.makeText(getApplicationContext(), "Shuffle is OFF",
							Toast.LENGTH_SHORT).show();
					btnShuffle.setImageResource(R.drawable.btn_shuffle);
				} else {
					// make repeat to true
					isShuffle = true;
					Toast.makeText(getApplicationContext(), "Shuffle is ON",
							Toast.LENGTH_SHORT).show();
					// make shuffle to false
					isRepeat = false;
					btnShuffle.setImageResource(R.drawable.btn_shuffle_focused);
					btnRepeat.setImageResource(R.drawable.btn_repeat);
				}
			}
		});

		/**
		 * Button Click event for Play list click event Launches list activity
		 * which displays list of songs
		 * */
		btnPlaylist.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				Intent i = new Intent(getApplicationContext(),
						PlayListActivity.class);
				startActivityForResult(i, 100);
			}
		});

	}

	/**
	 * Receiving song index from playlist view and play the song
	 * */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == 100) {
			currentSongIndex = data.getExtras().getInt("songIndex");
			// play selected song
			playSong(currentSongIndex);
		}

	}

	/**
	 * Function to play a song
	 * 
	 * @param songIndex
	 *            - index of song
	 * */
	public void playSong(int songIndex) {
		// Play song
		try {
			System.out.println("songindex: " + songIndex);
			mp.reset();
			mp.setDataSource(songsList.get(songIndex).get("songPath"));
			mp.prepare();
			mp.start();
			
			currentSongIndex = songIndex;
			isMusicPaused = false;
			if (seekto != 0) {
				mp.seekTo(seekto);
				seekto = 0;
			}

			initNotification();
			// currentSongIndex = songIndex;

			// Displaying Song title
			String songTitle = songsList.get(songIndex).get("songTitle");
			songTitleLabel.setText(songTitle);

			// Changing Button Image to pause image
			btnPlay.setImageResource(R.drawable.btn_pause);

			// set Progress bar values
			songProgressBar.setProgress(0);
			songProgressBar.setMax(100);

			// Updating progress bar
			updateProgressBar();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Update timer on seekbar
	 * */
	public void updateProgressBar() {
		if (isRunning)
			mHandler.postDelayed(mUpdateTimeTask, 100);
	}

	/**
	 * Background Runnable thread
	 * */
	private Runnable mUpdateTimeTask = new Runnable() {
		public void run() {

			if (isRunning && !isMusicPaused) {
				long totalDuration = mp.getDuration();
				long currentDuration = mp.getCurrentPosition();

				// Displaying Total Duration time
				songTotalDurationLabel.setText(""
						+ utils.milliSecondsToTimer(totalDuration));
				// Displaying time completed playing
				songCurrentDurationLabel.setText(""
						+ utils.milliSecondsToTimer(currentDuration));

				// Updating progress bar
				int progress = (int) (utils.getProgressPercentage(
						currentDuration, totalDuration));
				songProgressBar.setProgress(progress);

				// Running this thread after 100 milliseconds
				mHandler.postDelayed(this, 100);
			}
		}
	};

	/**
	 * 
	 * */
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromTouch) {

	}

	/**
	 * When user starts moving the progress handler
	 * */
	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		// remove message Handler from updating progress bar
		mHandler.removeCallbacks(mUpdateTimeTask);
	}

	/**
	 * When user stops moving the progress hanlder
	 * */
	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		mHandler.removeCallbacks(mUpdateTimeTask);
		int totalDuration = mp.getDuration();
		int currentPosition = utils.progressToTimer(seekBar.getProgress(),
				totalDuration);

		// forward or backward to certain seconds
		mp.seekTo(currentPosition);

		// update timer progress again
		updateProgressBar();
	}

	/**
	 * On Song Playing completed if repeat is ON play same song again if shuffle
	 * is ON play random song
	 * */
	@Override
	public void onCompletion(MediaPlayer arg0) {
		if (!isMusicPaused) {
			playSong(getNextSongId());
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mp.release();
		// Unregister headsetReceiver
		unregisterReceiver(headsetReceiver);

		if (phoneStateListener != null) {
			telephonyManager.listen(phoneStateListener,
					PhoneStateListener.LISTEN_NONE);
		}

		cancelNotification();
	}

	/**
	 * In onPause() Save the settings values into the Shared Preferences object
	 */
	@Override
	public void onPause() {
		super.onPause();
		isRunning = false;

		prefs = getSharedPreferences(prefName, MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(SHUFFLE_STATE, isShuffle);
		editor.putBoolean(REPEAT_STATE, isRepeat);

		editor.commit();

		//
	}

	/**
	 * In OnResume() restore the saved settings from Shared Preferences
	 */
	@Override
	protected void onResume() {
		super.onResume();
		isRunning = true;

		if (seekto > 0 && !isMusicPaused && !songsList.isEmpty()) {
			playSong(currentSongIndex);
		}
		prefs = getSharedPreferences(prefName, MODE_PRIVATE);
		isShuffle = prefs.getBoolean(SHUFFLE_STATE, false);
		isRepeat = prefs.getBoolean(REPEAT_STATE, false);

		if (isShuffle) {
			btnShuffle.setImageResource(R.drawable.btn_shuffle_focused);
		} else {
			btnShuffle.setImageResource(R.drawable.btn_shuffle);
		}

		if (isRepeat) {
			btnRepeat.setImageResource(R.drawable.btn_repeat_focused);
		} else {
			btnRepeat.setImageResource(R.drawable.btn_repeat);
		}
		
		if (!isMusicPaused){
			updateProgressBar();
		}
	}

	// If headset gets unplugged, stop music.
	private BroadcastReceiver headsetReceiver = new BroadcastReceiver() {
		private boolean headsetConnected = false;

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.hasExtra("state")) {
				if (headsetConnected && intent.getIntExtra("state", 0) == 0) {
					headsetConnected = false;
					headsetSwitch = 0;
				} else if (!headsetConnected
						&& intent.getIntExtra("state", 0) == 1) {
					headsetConnected = true;
					headsetSwitch = 1;
				}
			}

			switch (headsetSwitch) {
			case (0):
				headsetDisconnected();
				break;
			case (1):
				break;
			}
		}

	};

	private void headsetDisconnected() {
		if (!isMusicPaused){
			mp.pause();
			btnPlay.setImageResource(R.drawable.btn_play);
			cancelNotification();
		}
	}

	// Initialize notification
	public void initNotification() {
		
	    CharSequence title = "TabPlayer";
	    CharSequence text = "TabPlayer - Playing music";
	    CharSequence contentText = "Music is now playing...";


	    NotificationManager notificationManager = (NotificationManager) getApplicationContext()
	            .getSystemService(Context.NOTIFICATION_SERVICE);
	    Notification notification = new Notification(R.drawable.play_notif, text,
	            System.currentTimeMillis());

	    Intent notificationIntent = new Intent(getApplicationContext(), MediaPlayerActivity.class);
	    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
	    
	    notification.setLatestEventInfo(getApplicationContext(), title, contentText, contentIntent);
	    notificationManager.notify(10, notification);
	}
	
	private int getNextSongId() {
		// check for repeat is ON or OFF
		if (isRepeat) {
			return currentSongIndex;
		} else if (isShuffle) {
			// shuffle is on - play a random song
			Random rand = new Random();
			return  rand.nextInt((songsList.size() - 1) - 0 + 1);
		} else {
			if (currentSongIndex < (songsList.size() - 1)) {
				return (currentSongIndex + 1);
			} else {
				return 0;
			}
		}
	}
	private int getPrevSongId() {
		// check for repeat is ON or OFF
		if (isRepeat) {
			return currentSongIndex;
		} else if (isShuffle) {
			// shuffle is on - play a random song
			Random rand = new Random();
			return  rand.nextInt((songsList.size() - 1) - 0 + 1);
		} else {
			if (currentSongIndex > 0) {
				return (currentSongIndex - 1);
			} else {
				return songsList.size() - 1;
			}
		}
	}
	// Cancel Notification
	private void cancelNotification() {
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		mNotificationManager.cancel(10);
	}

	public void alert() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		// set title
		builder.setTitle("There's a problem...");

		// set dialog message
		builder.setMessage("Couldn't find any audio file to play. Please make sure to have your music in the folder /sdcard/Music");
		builder.setCancelable(false);
		builder.setNegativeButton("Quit App",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						// if this button is clicked, close
						// current activity
						MediaPlayerActivity.this.finish();
					}
				});
		builder.setPositiveButton("Close Dialog",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						// if this button is clicked, just close
						// the dialog box and do nothing
						dialog.cancel();
					}
				});

		// create alert dialog
		AlertDialog alertDialog = builder.create();

		// show it
		alertDialog.show();
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		Handler instance = mHandler;
		mHandler = null;
		return instance;
	}

	@Override
	protected void onStart() {
		super.onStart();
	}
	

	@Override
	protected void onStop() {
		super.onStop();

		prefs = getSharedPreferences(prefName, MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(SHUFFLE_STATE, isShuffle);
		editor.putBoolean(REPEAT_STATE, isRepeat);

		editor.commit();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("currPos", mp.getCurrentPosition());
		outState.putInt("currentSongIndex", currentSongIndex);
		outState.putBoolean("isMusicPaused", isMusicPaused);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.media_player, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		if (item.getItemId() == R.id.menu_exit) {
			MediaPlayerActivity.this.finish();
		}

		if (item.getItemId() == R.id.menu_playlist) {
			Intent i = new Intent(getApplicationContext(),
					PlayListActivity.class);
			startActivityForResult(i, 100);
		}

		if (item.getItemId() == R.id.menu_shuffle) {
			if (isShuffle) {
				isShuffle = false;
				Toast.makeText(getApplicationContext(), "Shuffle is OFF",
						Toast.LENGTH_SHORT).show();
				btnShuffle.setImageResource(R.drawable.btn_shuffle);
			} else {
				// make repeat to true
				isShuffle = true;
				Toast.makeText(getApplicationContext(), "Shuffle is ON",
						Toast.LENGTH_SHORT).show();
				// make shuffle to false
				isRepeat = false;
				btnShuffle.setImageResource(R.drawable.btn_shuffle_focused);
				btnRepeat.setImageResource(R.drawable.btn_repeat);
			}
		}

		if (item.getItemId() == R.id.menu_repeat) {
			if (isRepeat) {
				isRepeat = false;
				Toast.makeText(getApplicationContext(), "Repeat is OFF",
						Toast.LENGTH_SHORT).show();
				btnRepeat.setImageResource(R.drawable.btn_repeat);
			} else {
				// make repeat to true
				isRepeat = true;
				Toast.makeText(getApplicationContext(), "Repeat is ON",
						Toast.LENGTH_SHORT).show();
				// make shuffle to false
				isShuffle = false;
				btnRepeat.setImageResource(R.drawable.btn_repeat_focused);
				btnShuffle.setImageResource(R.drawable.btn_shuffle);
			}
		}

		return super.onOptionsItemSelected(item);
	}


	@Override
	public  boolean onKeyDown (int keyCode, KeyEvent event){
	    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
	        event.startTracking();
	        return true;
	    }
	    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
	        event.startTracking();
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}
	
	public boolean onKeyLongPress(int keyCode, KeyEvent event){
	    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
	    	if (!isMusicPaused){
	    		playSong(getNextSongId());
	    	}
	    	return true;
	    }
	    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
	    	if (!isMusicPaused){
	    		playSong(getPrevSongId());
	    	}
	    	return true;
	    }
	    return super.onKeyLongPress(keyCode, event);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
	    if(!event.isCanceled()){
	        switch(keyCode){
		        case KeyEvent.KEYCODE_VOLUME_UP:
		        {
		        	AudioManager audioManager =  (AudioManager)getSystemService(Context.AUDIO_SERVICE);
	
		        	audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
		                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
		            break;
		        }
		        case KeyEvent.KEYCODE_VOLUME_DOWN:
		        {
		        	AudioManager audioManager =  (AudioManager)getSystemService(Context.AUDIO_SERVICE);
	
		        	audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
		                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
		        	break;
		        }
	        }
	    }
	    return super.onKeyUp(keyCode, event);
	}
}