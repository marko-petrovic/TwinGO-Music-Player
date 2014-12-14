/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.dualquo.te.carplayer;

import java.math.BigDecimal;
import java.math.RoundingMode;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The primary playback screen with playback controls and large cover display.
 */
public class FullPlaybackActivity extends PlaybackActivity
	implements SeekBar.OnSeekBarChangeListener, View.OnLongClickListener, GPSCallback
{
	public static final int DISPLAY_INFO_OVERLAP = 0;
	public static final int DISPLAY_INFO_BELOW = 1;
	public static final int DISPLAY_INFO_WIDGETS = 2;
	public static final int DISPLAY_INFO_WIDGETS_ZOOMED = 3;

	private TextView mOverlayText;
	private View mControlsBottom;

	public Typeface font;
	
	protected final static int CHOICE_APP_LEAVE = 1438;
	
	private SeekBar mSeekBar;
	private TextView mElapsedView;
	private TextView mDurationView;

	private TextView mTitle;
//	private TextView mAlbum;
	private TextView mArtist;
	private TextView mWarning;

	/**
	 * True if the controls are visible (play, next, seek bar, etc).
	 */
	private boolean mControlsVisible;

	/**
	 * Current song duration in milliseconds.
	 */
	private long mDuration;
	private boolean mSeekBarTracking;
	private boolean mPaused;

	/**
	 * The current display mode, which determines layout and cover render style.
	 */
	private int mDisplayMode;

	private Action mCoverPressAction;
	private Action mCoverLongPressAction;

	/**
	 * Cached StringBuilder for formatting track position.
	 */
	private final StringBuilder mTimeBuilder = new StringBuilder();
	
	//transplantacija organa:
	private boolean single = false;
	public static boolean master = false;
	private static boolean D = true;
	
	protected final static int CHOICE_MASTER_SLAVE = 1;
	
	PhoneStateListener phoneStateListenerMaster = new PhoneStateListener() {
	    @Override
	    public void onCallStateChanged(int state, String incomingNumber) 
	    {
	        	if (PlaybackService.mMediaPlayer != null && PlaybackService.mMediaPlayer.isPlaying())
	        	{
	        		if (state == TelephonyManager.CALL_STATE_RINGING) 
	        		{
		        		//mutes master media player
	        			PlaybackService.mMediaPlayer.setVolume(0.0f, 0.0f);

			        	if (D) Log.d("Telephony","CALL STATE IS: RINGING, set MUTE masterMediaPlayer");
	        		}
	        		else if(state == TelephonyManager.CALL_STATE_IDLE) 
	        		{
			        	//unmute master media player:	        	
	        			PlaybackService.mMediaPlayer.setVolume(1.0f, 1.0f);
			        	
			        	if (D) Log.d("Telephony","//Not in call: Play music, set UNMUTE masterMediaPlayer");
	        		}
	        		else if(state == TelephonyManager.CALL_STATE_OFFHOOK) 
	        		{
		        		//mutes master media player
	        			PlaybackService.mMediaPlayer.setVolume(0.0f, 0.0f);
		        		
			        	if (D) Log.d("Telephony","//A call is dialing, active or on hold");
		        	}
	        	}
	        	else if
	        	((PlaybackService.mMediaPlayer != null && !PlaybackService.mMediaPlayer.isPlaying()) 
	        			||
	        			PlaybackService.mMediaPlayer == null)
	        	{
	        		//do nothing
	        	}
        
	        super.onCallStateChanged(state, incomingNumber);
	    }
	};
	
	//GPS
	private GPSManager gpsManager = null;
    private double speed = 0.0;
    
    private int TEXT_SIZE_SMALL = 15;
    private int TEXT_SIZE_LARGE = 80;
    private final int INDEX_KM = 1;
    private final int INDEX_MILES = 1;
    private int DEFAULT_SPEED_LIMIT = 80;
    private int HOUR_MULTIPLIER = 3600;
    private double UNIT_MULTIPLIERS[] = { 0.001, 0.000621371192 };
    
    private int measurement_index = INDEX_KM;
    public int ifGPSIsSet = 0;
	public boolean gpsWasSet = false;
	
	private Location previousLocation, currentLocation;
	private long previousTime, currentTime;
	private boolean firstTimeOnGPSUpdate = true;
	private boolean updateWarningText = false;
	
	private SharedPreferences settings = null;
	private float speedFromPrefs = 50.0f;
	
	//incrementing int, to provide some sort of tolerance to speeding
	private int incrementingInt = 0;
	
//	private Camera mCam;
//	private MirrorView mCamPreview;
//	private int mCameraId = 0;
//	private FrameLayout mPreviewLayout;
    
    @Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		
		font = Typeface.createFromAsset(getAssets(), "fonts/dsdigit.ttf");
		
//		gs = (GlobalState) getApplication();

		settings = PlaybackService.getSettings(this);
		
		//see if gps speed parameter is set (1 if set, 0 if not set)
		ifGPSIsSet = Integer.parseInt(settings.getString("gps_speed", "0"));
		
		//po difoltu vadi br.2:
		int displayMode = Integer.parseInt(settings.getString("display_mode", "2"));
		mDisplayMode = displayMode;

		int layout = R.layout.full_playback;
		int coverStyle;

		switch (displayMode) 
		{
			default:
				Log.w("VanillaMusic", "Invalid display mode given. Defaulting to overlap.");
				// fall through
			case DISPLAY_INFO_OVERLAP:
				coverStyle = CoverBitmap.STYLE_OVERLAPPING_BOX;
				break;
			case DISPLAY_INFO_BELOW:
				coverStyle = CoverBitmap.STYLE_INFO_BELOW;
				break;
			case DISPLAY_INFO_WIDGETS:
				coverStyle = CoverBitmap.STYLE_NO_INFO;
				layout = R.layout.full_playback_alt;
				break;
			case DISPLAY_INFO_WIDGETS_ZOOMED:
				coverStyle = CoverBitmap.STYLE_NO_INFO_ZOOMED;
				layout = R.layout.full_playback_alt;
				break;
		}

		setContentView(layout);

		CoverView coverView = (CoverView)findViewById(R.id.cover_view);
		coverView.setup(mLooper, this, coverStyle);
		coverView.setOnClickListener(this);
		coverView.setOnLongClickListener(this);
		mCoverView = coverView;

		mControlsBottom = findViewById(R.id.controls_bottom);
		View previousButton = findViewById(R.id.previous);
		previousButton.setOnClickListener(this);
		mPlayPauseButton = (ImageButton)findViewById(R.id.play_pause);
		mPlayPauseButton.setOnClickListener(this);
		View nextButton = findViewById(R.id.next);
		nextButton.setOnClickListener(this);

		mTitle = (TextView)findViewById(R.id.title);
//		mAlbum = (TextView)findViewById(R.id.album);
		mArtist = (TextView)findViewById(R.id.artist);
		
		mTitle.setTypeface(font);
		mTitle.setTextSize(86);
		mTitle.setTextColor(Color.parseColor("#93b35e"));
//		mAlbum.setTypeface(font);
//		mAlbum.setTextColor(Color.parseColor("#93b35e"));
//		mAlbum.setTextSize(20);
		mArtist.setTypeface(font);
		mArtist.setTextSize(24);
		mArtist.setTextColor(Color.parseColor("#93b35e"));
		
		mWarning = (TextView)findViewById(R.id.warning_text);
		mWarning.setTypeface(font);
		mWarning.setTextSize(140);
		mWarning.setTextColor(Color.RED);
		mWarning.setText("");
		warningWillBlink();

		mElapsedView = (TextView)findViewById(R.id.elapsed);
		mElapsedView.setTypeface(font);
		mElapsedView.setTextColor(Color.parseColor("#93b35e"));
		mElapsedView.setTextSize(34);
		
		mDurationView = (TextView)findViewById(R.id.duration);
		mDurationView.setTypeface(font);
		mDurationView.setTextColor(Color.parseColor("#93b35e"));
		mDurationView.setTextSize(34);
		
		mSeekBar = (SeekBar)findViewById(R.id.seek_bar);
		mSeekBar.setMax(1000);
		mSeekBar.setOnSeekBarChangeListener(this);
		mSeekBar.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(Color.parseColor("#93b35e"), PorterDuff.Mode.SRC_OUT));
//		mSeekBar.getThumb().setColorFilter(new PorterDuffColorFilter(Color.parseColor("#93b35e"), PorterDuff.Mode.SRC));

		mShuffleButton = (ImageButton)findViewById(R.id.shuffle);
		mShuffleButton.setOnClickListener(this);
		registerForContextMenu(mShuffleButton);
		mEndButton = (ImageButton)findViewById(R.id.end_action);
		mEndButton.setOnClickListener(this);
		registerForContextMenu(mEndButton);
		
		openLibraryButton = (ImageButton)findViewById(R.id.open_songs);
		openLibraryButton.setOnClickListener(this);
		registerForContextMenu(openLibraryButton);
		
		openSettingsButton = (ImageButton)findViewById(R.id.open_settings);
		openSettingsButton.setOnClickListener(this);
		registerForContextMenu(openSettingsButton);

		setControlsVisible(settings.getBoolean("visible_controls", true));
		setDuration(0);
		
//		mCameraId = findFirstFrontFacingCamera();
//		 
//	    mPreviewLayout = (FrameLayout) findViewById(R.id.camPreview);
//	    mPreviewLayout.removeAllViews();
//	 
//	    startCameraInLayout(mPreviewLayout, mCameraId);
	}
    
//    private int findFirstFrontFacingCamera() {
//        int foundId = -1;
//        int numCams = Camera.getNumberOfCameras();
//        for (int camId = 0; camId < numCams; camId++) {
//            CameraInfo info = new CameraInfo();
//            Camera.getCameraInfo(camId, info);
//            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
//                foundId = camId;
//                break;
//            }
//        }
//        return foundId;
//    }
//    
//    private void startCameraInLayout(FrameLayout layout, int cameraId) {
//        mCam = Camera.open(cameraId);
//        if (mCam != null) {
//            mCamPreview = new MirrorView(this, mCam);
//            layout.addView(mCamPreview);
//        }
//    }

	@Override
	public void onStart()
	{
		super.onStart();

		SharedPreferences settings = PlaybackService.getSettings(this);
		if (mDisplayMode != Integer.parseInt(settings.getString("display_mode", "2"))) 
		{
			finish();
			startActivity(new Intent(this, FullPlaybackActivity.class));
		}

		mCoverPressAction = getAction(settings, "cover_press_action", Action.ToggleControls);
		mCoverLongPressAction = getAction(settings, "cover_longpress_action", Action.PlayPause);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mPaused = false;
		updateProgress();
		
		speedFromPrefs = settings.getFloat("speed", 50.0f);
		System.out.println("got speed: " + speedFromPrefs);
		
		//GPS
		if (ifGPSIsSet == 0) 
		{
			gpsWasSet = true;
			
			gpsManager = new GPSManager();
		    
		    gpsManager.startListening(getApplicationContext());
		    gpsManager.setGPSCallback(this);
		}
	}
	
	@Override
    public void onGPSUpdate(Location location) 
    {
		//first going through is only to record previousTime and previousLocation
		if (firstTimeOnGPSUpdate) 
		{
			firstTimeOnGPSUpdate = false;
			
			//record values
			previousTime = System.currentTimeMillis();
			previousLocation = location;
		}
		else
		{
			currentTime = System.currentTimeMillis();
			currentLocation = location;
			
			float timeDifference = (currentTime - previousTime);
//			System.out.println("TimeDifference is " + timeDifference);
			
			double distance = Utils.haversineDistanceDouble
				(
					previousLocation.getLatitude(), 
					previousLocation.getLongitude(),
					currentLocation.getLatitude(),
					currentLocation.getLongitude()
				);
//			System.out.println("Distance is " + distance);
			
			if (distance != 0.0) 
			{
				speed = roundDecimal((distance/timeDifference)*1000*3600, 2);
				
	            String unitString = "km/h";
	            System.out.println("Speed is " + speed + " " + unitString);
	            
	            if (speed > speedFromPrefs) 
	            {
	            	if (updateWarningText) 
	            	{
	            		String speedingText = String.valueOf(speed).split("\\.")[0];
	            		mWarning.setText(speedingText);
					}
	            	incrementingInt++;
	            	
	            	if (incrementingInt > 2) 
	            	{
	            		String speedingText = String.valueOf(speed).split("\\.")[0];
	            		mWarning.setText(speedingText);
	            		updateWarningText = true;
	            		
	            		incrementingInt = 0;
					}
				}
	            else
	            {
	            	mWarning.setText("");
	            	updateWarningText = false;
	            	
	            	if (incrementingInt >= 0) 
	            	{
	            		incrementingInt--;
					}
	            }
	            
	            //set current values to be previous ones for the next iteration
	            previousLocation = currentLocation;
	            previousTime = currentTime;
			}
		}
    }
	
	private void warningWillBlink()
	{
		Animation anim = new AlphaAnimation(0.0f, 1.0f);
		anim.setDuration(50); 
		anim.setStartOffset(20);
		anim.setRepeatMode(Animation.REVERSE);
		anim.setRepeatCount(Animation.INFINITE);
		mWarning.startAnimation(anim);
	}

	private double roundDecimal(double value, final int decimalPlace) 
	{
		BigDecimal bd = new BigDecimal(value);

		bd = bd.setScale(decimalPlace, RoundingMode.HALF_UP);
		value = bd.doubleValue();

		return value;
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mPaused = true;
	}
	
	@Override
	public void onDestroy() 
	{
		if (gpsWasSet) 
		{
			gpsManager.stopListening();
	        gpsManager.setGPSCallback(null);
	        
	        gpsManager = null;
		}
        
		super.onDestroy();
	}

//	public void onBackPressed() 
//	{
////		showDialog(CHOICE_APP_LEAVE);
////		openLibrary();
//	}
	
	@Override
	protected Dialog onCreateDialog(int id)
	{
		Dialog dialog = null;
		switch (id)
		{
			case CHOICE_APP_LEAVE:
				dialog = createQUITDialog();
				break;
				
			case CHOICE_MASTER_SLAVE:
				dialog = createMasterSlaveDialog();
				break;
				
			default:
				dialog = super.onCreateDialog(id);
				break;
		}
		return dialog;
	}
	
	protected Dialog createQUITDialog()
	{
		Dialog toReturn;
				
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Quit from Bluetooth Music Player?");
		
		builder.setIcon(R.drawable.icon);
		
		builder.setTitle("TwinGO Music Player");
		
		builder.setPositiveButton("Yes, quit",
				new DialogInterface.OnClickListener() 
					{
						public void onClick(DialogInterface dialog, int which) 
							{
								System.exit(0);
							}
					});
		
		builder.setNegativeButton("No, stay",
				new DialogInterface.OnClickListener() 
					{
						public void onClick(DialogInterface dialog, int which) 
							{
								return;
							}
					});

		toReturn = builder.create();
		return toReturn;
	}
	
	protected Dialog createMasterSlaveDialog()
	{
		Dialog toReturn;

		final CharSequence[] items = {"shareMUSIC over Bluetooth", "listenMUSIC over Bluetooth", "use as standard player"};
		final int MASTER = 0;
		final int SLAVE = 1;
		final int SINGLE = 2;

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Choose mode:");
		builder.setIcon(R.drawable.icon);
		
		builder.setItems(items, new DialogInterface.OnClickListener() {
			 public void onClick(DialogInterface dialog, int item) {
			    	switch (item) {
			    	case MASTER:
			    		actionMaster();
			    		break;
			    	case SLAVE:
			    		actionSlave();
			    		break;
			    	case SINGLE:
			    		actionSingle();
			    		break;
			    	}
		        Toast.makeText(getApplicationContext(), items[item], Toast.LENGTH_SHORT).show();
		    }
		});
		
		builder.setCancelable(false);

		toReturn = builder.create();
		return toReturn;
	}
	
	/**
	 * Hide the message overlay, if it exists.
	 */
	private void hideMessageOverlay()
	{
		if (mOverlayText != null)
			mOverlayText.setVisibility(View.GONE);
	}

	/**
	 * Show some text in a message overlay.
	 *
	 * @param text Resource id of the text to show.
	 */
	private void showOverlayMessage(int text)
	{
		if (mOverlayText == null) {
			TextView view = new TextView(this);
			view.setBackgroundColor(Color.BLACK);
			view.setTextColor(Color.WHITE);
			view.setGravity(Gravity.CENTER);
			view.setPadding(25, 25, 25, 25);
			// Make the view clickable so it eats touch events
			view.setClickable(true);
			view.setOnClickListener(this);
			addContentView(view,
					new ViewGroup.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
							LinearLayout.LayoutParams.FILL_PARENT));
			mOverlayText = view;
		} else {
			mOverlayText.setVisibility(View.VISIBLE);
		}

		mOverlayText.setText(text);
	}

	@Override
	protected void onStateChange(int state, int toggled)
	{
		super.onStateChange(state, toggled);

		if ((toggled & (PlaybackService.FLAG_NO_MEDIA|PlaybackService.FLAG_EMPTY_QUEUE)) != 0) {
			if ((state & PlaybackService.FLAG_NO_MEDIA) != 0) {
				showOverlayMessage(R.string.no_songs);
			} else if ((state & PlaybackService.FLAG_EMPTY_QUEUE) != 0) {
				showOverlayMessage(R.string.empty_queue);
			} else {
				hideMessageOverlay();
			}
		}

		if ((state & PlaybackService.FLAG_PLAYING) != 0)
			updateProgress();
	}

	@Override
	protected void onSongChange(final Song song)
	{
		super.onSongChange(song);

		setDuration(song == null ? 0 : song.duration);

		if (mTitle != null) 
		{
			if (song == null) 
			{
				mTitle.setText(null);
//				mAlbum.setText(null);
				mArtist.setText(null);
			} 
			else 
			{
				mTitle.setText(song.title);
//				mAlbum.setText(song.album);
				mArtist.setText(song.artist);
			}
		}

		updateProgress();
	}

	/**
	 * Update the current song duration fields.
	 *
	 * @param duration The new duration, in milliseconds.
	 */
	private void setDuration(long duration)
	{
		mDuration = duration;
		mDurationView.setText(DateUtils.formatElapsedTime(mTimeBuilder, duration / 1000));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
//		menu.add(0, MENU_LIBRARY, 0, R.string.library).setIcon(R.drawable.ic_menu_music_library);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
		case MENU_LIBRARY:
			openLibrary();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onSearchRequested()
	{
		openLibrary();
		return false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			nextSong();
			findViewById(R.id.next).requestFocus();
			return true;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			previousSong();
			findViewById(R.id.previous).requestFocus();
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_ENTER:
			setControlsVisible(!mControlsVisible);
			mHandler.sendEmptyMessage(MSG_SAVE_CONTROLS);
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}

	/**
	 * Update seek bar progress and schedule another update in one second
	 */
	private void updateProgress()
	{
		int position = PlaybackService.hasInstance() ? PlaybackService.get(this).getPosition() : 0;

		if (!mSeekBarTracking) {
			long duration = mDuration;
			mSeekBar.setProgress(duration == 0 ? 0 : (int)(1000 * position / duration));
		}

		mElapsedView.setText(DateUtils.formatElapsedTime(mTimeBuilder, position / 1000));

		if (!mPaused && mControlsVisible && (mState & PlaybackService.FLAG_PLAYING) != 0) {
			// Try to update right when the duration increases by one second
			long next = 1000 - position % 1000;
			mUiHandler.removeMessages(MSG_UPDATE_PROGRESS);
			mUiHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, next);
		}
	}

	/**
	 * Toggles the visibility of the playback controls.
	 */
	private void setControlsVisible(boolean visible)
	{
		int mode = visible ? View.VISIBLE : View.GONE;
		mSeekBar.setVisibility(mode);
		mElapsedView.setVisibility(mode);
		mDurationView.setVisibility(mode);
		mControlsBottom.setVisibility(mode);
		mControlsVisible = visible;

		if (visible) {
			mPlayPauseButton.requestFocus();
			updateProgress();
		}
	}

	/**
	 * Update the seekbar progress with the current song progress. This must be
	 * called on the UI Handler.
	 */
	private static final int MSG_UPDATE_PROGRESS = 10;
	/**
	 * Save the hidden_controls preference to storage.
	 */
	private static final int MSG_SAVE_CONTROLS = 14;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_SAVE_CONTROLS: {
			SharedPreferences settings = PlaybackService.getSettings(this);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean("visible_controls", mControlsVisible);
			editor.commit();
			break;
		}
		case MSG_UPDATE_PROGRESS:
			updateProgress();
			break;
		default:
			return super.handleMessage(message);
		}

		return true;
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (fromUser)
			PlaybackService.get(this).seekToProgress(progress);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar)
	{
		mSeekBarTracking = true;
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar)
	{
		mSeekBarTracking = false;
	}

	@Override
	public void performAction(Action action)
	{
		if (action == Action.ToggleControls) {
			setControlsVisible(!mControlsVisible);
			mHandler.sendEmptyMessage(MSG_SAVE_CONTROLS);
		} else {
			super.performAction(action);
		}
	}

	@Override
	public void onClick(View view)
	{
		if (view == mOverlayText && (mState & PlaybackService.FLAG_EMPTY_QUEUE) != 0) 
		{
			setState(PlaybackService.get(this).setFinishAction(SongTimeline.FINISH_RANDOM));
		} 
		else if (view == mCoverView) 
			{
				performAction(mCoverPressAction);
			} 
			else 
				{
					super.onClick(view);
				}
	}

	@Override
	public boolean onLongClick(View view)
	{
		if (view.getId() == R.id.cover_view) {
			performAction(mCoverLongPressAction);
			return true;
		}

		return false;
	}
	
	//transplantacija organa:
	
	protected void actionSingle()
	{
		if (single == false) {
			single = true;
			master = false;
			
			//starts managing the phone for single, which is equal to master, through phoneStateListenerMaster
			TelephonyManager mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
			if(mgr != null) {
			    mgr.listen(phoneStateListenerMaster, PhoneStateListener.LISTEN_CALL_STATE);
			}
			
		}
//		seekBar.setOnSeekBarChangeListener(this);
	}
	
	protected void actionMaster()
	{
		if (master == false) {
			master = true;
			single = false;
			
			//starts managing the phone for master, through phoneStateListenerMaster
			TelephonyManager mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
			if(mgr != null) {
			    mgr.listen(phoneStateListenerMaster, PhoneStateListener.LISTEN_CALL_STATE);
			}
			// Launch server socket
			//startCanBeServiceOnCreate();
		}
	}
	
	protected void actionSlave()
	{
		master = false;
		single = false;

//		startCanBeServiceOnCreate();
		// Connect to master
//		Intent serverIntent = new Intent(this, DeviceListActivity.class);
//		startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
	}
}
