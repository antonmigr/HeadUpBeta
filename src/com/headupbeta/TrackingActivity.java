package com.headupbeta;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import android.view.MenuItem;
import android.app.Activity;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;

public class TrackingActivity extends Activity implements SensorEventListener {

	private Camera mCamera;
	private CameraTracker mPreview; // instance of the CameraTracker class
	private PictureCallback mPicture;
	private Button capture, switchCamera; // not used
	private Context myContext;
	private LinearLayout cameraPreview; // unseen view where the preview takes
										// place. moved to the left so that it's
										// not seen on the screen
	
	private boolean cameraFront = false; // not used
	public static int globalProgress = 0;
	public boolean getFront = false;
	public boolean getBack = false;
	public TextView middleFeedback = null; // the text label in the middle,
											// where feedback shows up
	
	//accelerometer stuff
	float[] mGravity = null;
	float[] mGeomagnetic = null;
	public SensorManager sensorManager;

	float Rot[] = null; // for gravity rotational data
	// don't use R because android uses that for other stuff
	float I[] = null; // for magnetic rotational data
	float accels[] = new float[3];
	float mags[] = new float[3];
	float[] values = new float[3];

	float azimuth;
	float pitch;
	float roll;
	public HandlerThread mCameraThread = null; // for doing face detection in a
												// separate thread	
	public Handler mCameraHandler = null; // for doing face detection in a
											// separate thread

	private void startCamera() {
		// create a new thread, by startingassinging the mCameraHandler to a
		// separate thread, all preview related stuff is done in parallel
		if (mCameraThread == null) {
			mCameraThread = new HandlerThread("camThread");
			mCameraThread.start();
			mCameraHandler = new Handler(mCameraThread.getLooper());
		}

		mCameraHandler.post(new Runnable() {

			@Override
			public void run() {
				try {
					mCamera = Camera.open(findFrontFacingCamera());
					Log.i("Face", "camera: " + mCamera);
				} catch (Exception e) {

				}
			}
		});
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// gets the readings from the accelerometer and converts them to the
		// angle under which the device is standing

		// below commented code - junk - unreliable is never populated
		// if sensor is unreliable, return void
		// if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
		// {
		// return;
		// }

		switch (event.sensor.getType()) {
		case Sensor.TYPE_MAGNETIC_FIELD:
			mags = event.values.clone();
			break;
		case Sensor.TYPE_ACCELEROMETER:
			accels = event.values.clone();
			break;
		}

		if (mags != null && accels != null) {
			Rot = new float[9];
			I = new float[9];
			SensorManager.getRotationMatrix(Rot, I, accels, mags);
			// Correct if screen is in Landscape

			float[] outR = new float[9];
			SensorManager.remapCoordinateSystem(Rot, SensorManager.AXIS_X,
					SensorManager.AXIS_Z, outR);
			SensorManager.getOrientation(outR, values);

			azimuth = values[0] * 57.2957795f; // looks like we don't need this
												// one
			pitch = values[1] * 57.2957795f;
			roll = values[2] * 57.2957795f;
			mags = null; // retrigger the loop when things are repopulated
			accels = null; // //retrigger the loop when things are repopulated
			// Log.i("Face","1: "+pitch+" 2: "+azimuth);
			mPreview.angle = pitch;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_tracking);
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

		// register listeners
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
				SensorManager.SENSOR_DELAY_NORMAL);

		setTheme(android.R.style.Theme_Holo);
		
		Toast toast = Toast.makeText(getBaseContext(),
				"Posture tracking started", Toast.LENGTH_LONG);
		
		LinearLayout toastLayout = (LinearLayout) toast.getView();

		// the toast that displays the "Posture tracking started" message when
		// the app is launched or when calibratin
		// is complete
		TextView toastTV = (TextView) toastLayout.getChildAt(0);
		toast.setGravity(Gravity.CENTER, 0, -17);
		toastTV.setTextSize(20);
		toast.show();
		
		//the on - off switch
		Switch switchOne = (Switch) findViewById(R.id.switch1);
		switchOne.setChecked(true);		
		switchOne.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				mPreview.switchOn = !mPreview.switchOn;
				Log.i("Face", "SWITCH IS " + mPreview.switchOn);
				if (mPreview.switchOn == false) {
					// middleFeedback.setTextColor(Color.GRAY);
					// middleFeedback.setText("Not tracking");
				}

			}

		});

		Log.i("Face", "made it here" + findFrontFacingCamera());
		myContext = this;

		// the view which displayes the camera preview, push to the side so it's
		// invisible to the user
		cameraPreview = (LinearLayout) findViewById(R.id.camera_preview);
		mPreview = new CameraTracker(myContext, mCamera);
		mPreview.track = this;
		
		cameraPreview.addView(mPreview);
		Log.i("Face", "made it here3" + findFrontFacingCamera());

		// get the settings from the database
		SQLiteDatabase db = openOrCreateDatabase("testdb",
				Context.MODE_WORLD_WRITEABLE, null);
		Cursor resultSet = db.rawQuery("SELECT * FROM dataone", null);
		resultSet.moveToFirst();
		String firstelev = resultSet.getString(0);
		String firsteyes = resultSet.getString(1);
		String perspdrop = resultSet.getString(2);
		String dbangle = resultSet.getString(3);
		mPreview.firstElev = Integer.parseInt(firstelev);
		mPreview.firstEyes = Double.parseDouble(firsteyes);
		mPreview.perspDrop = Double.parseDouble(perspdrop);
		mPreview.dbAngle = Double.parseDouble(dbangle);

		// /flags that tell the cameraTracker object that we're tracking and not
		// calibraiton
		mPreview.second = false;
		mPreview.first = false;
		mPreview.ready = true;
	

		Log.i("Face", firstelev + " " + firsteyes + " " + perspdrop + " "
				+ dbangle);
		db.close();

		// the text view right in the middle of the screen
		middleFeedback = (TextView) findViewById(R.id.middlefeedback);
		mPreview.middleFeedback = middleFeedback;

		Button recalibrate = (Button) findViewById(R.id.recalibrate);

		// when the "Recalibrate" button gets pushed. if the user clicks Yes,
		// then the table gets deleted from the db
		final DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					// Yes button clicked
					SQLiteDatabase db = openOrCreateDatabase("testdb",
							Context.MODE_WORLD_WRITEABLE, null);
					db.execSQL("DROP TABLE dataone");

					db.close();
					Intent nintent = new Intent(getBaseContext(),
							MainActivity.class);
					startActivity(nintent);
					System.exit(0);
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					// No button clicked
					break;
				}
			}
		};

		recalibrate.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						TrackingActivity.this);
				builder.setMessage(
						"Are you sure you want to recalibrate? Your saved settings will be lost.")
						.setPositiveButton("Yes", dialogClickListener)
						.setNegativeButton("No", dialogClickListener).show();

			}

		});

		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.tracking, menu);
		return true;
	}

	// disable the back button
	@Override
	public void onBackPressed() {
	}

	//action bar menu items
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(
					"Your last saved calibration settings are used for posture tracking.\n\n"
							+ "The feedback will be displayed in the middle of the screen.  If you're too far, tracking will stop until"
							+ " you get close enough to the screen.\n\n"
							+ "If the tracking feedback is consistently incorrect, recalibrate your settings.")
					.setCancelable(false)
					.setPositiveButton("OK",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									// do things
								}
							});
			AlertDialog alert = builder.create();
			alert.show();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.tracking_fragment,
					container, false);
			return rootView;
		}
	}

	public void onResume() {
		super.onResume();

		if (mCamera == null) {
			// if the front facing camera does not exist
			if (findFrontFacingCamera() < 0) {
				Toast.makeText(this, "No front facing camera found.",
						Toast.LENGTH_LONG).show();
				switchCamera.setVisibility(View.GONE);
			}

			startCamera();

			while (mCamera == null) {
			}

			Log.i("Face", "after thread: " + mCamera);
			Camera.Parameters parameters = mCamera.getParameters();

			// smaller preview size provides much faster face detection
			parameters.setPreviewSize(800, 480);
			mCamera.setParameters(parameters);

			Parameters params = mCamera.getParameters();
			List<Camera.Size> sizes = params.getSupportedPictureSizes();

			for (int i = 0; i < sizes.size(); i++) {
				Camera.Size thesize = (Camera.Size) sizes.get(i);
				Log.i("Face", "Size " + (i + 1) + " Height: " + thesize.height
						+ " Width: " + thesize.width);
			}

			mPreview.refreshCamera(mCamera);
		}
	}

	private int findFrontFacingCamera() {
		int cameraId = -1;
		// Search for the front facing camera
		int numberOfCameras = Camera.getNumberOfCameras();
		for (int i = 0; i < numberOfCameras; i++) {
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(i, info);
			if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
				cameraId = i;
				cameraFront = true;
				break;
			}
		}
		return cameraId;
	}

	private void releaseCamera() {
		// stop and release camera
		if (mCamera != null) {
			mCamera.release();
			mCamera = null;
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

}
