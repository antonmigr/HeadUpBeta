package com.headupbeta;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;

//the activity that records calibration settings
public class CalibrationActivity extends Activity implements
		SensorEventListener {

	private Camera mCamera;
	private CameraTracker mPreview;
	private PictureCallback mPicture;
	private Button capture, switchCamera; // not used
	private Context myContext;
	private LinearLayout cameraPreview; // the view where the camera preview is
										// displayed, moved out of the screen to
										// the side
	
	private boolean cameraFront = false;
	public boolean getFront = false;
	public boolean getBack = false;
	public HandlerThread mCameraThread = null;
	public Handler mCameraHandler = null;

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

	private void startCamera() {
		// make all camera-related calculations happen on a different thread
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

	boolean doesTableExist(SQLiteDatabase db, String tableName) {
		// if empty arguments are supplied, return
		if (tableName == null || db == null || !db.isOpen()) {
			return false;
		}
		// SQL used to determine if our settings table exists
		Cursor cursor = db
				.rawQuery(
						"SELECT COUNT(*) FROM sqlite_master WHERE type = ? AND name = ?",
						new String[] { "table", tableName });
		// if there are no rows, therefore the cursor can't move to the first
		// row, return false
		if (!cursor.moveToFirst()) {
			return false;
		}
		int count = cursor.getInt(0);
		cursor.close();

		// if there are rows, it returns true
		return count > 0;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {

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
		setContentView(R.layout.activity_calibration);
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

		// register accelerometer and magnetic field sensors
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
				SensorManager.SENSOR_DELAY_NORMAL);

		getBaseContext();

		Log.i("Face", "made it here" + findFrontFacingCamera()); // for
																	// debugging

		myContext = this;

		// initialize ther camera preview view , and set the static calibration
		// activity
		// inside cameraTracker to this activity
		cameraPreview = (LinearLayout) findViewById(R.id.camera_preview);
		mPreview = new CameraTracker(myContext, mCamera);
		mPreview.calib = this;
		cameraPreview.addView(mPreview);
		Log.i("Face", "made it here3" + findFrontFacingCamera());
		CameraTracker.caContext = this.getApplicationContext();

		TextView calibMsg = (TextView) findViewById(R.id.calibmessage);
		final Intent intent = new Intent(this, TrackingActivity.class);

		calibMsg.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				startActivity(intent);
				System.exit(0);
				return false;
			}

		});
		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}

		// the "Calibrating your settings" text label will be blank while the
		// pop up is active
		CameraTracker.calibmessage = (TextView) findViewById(R.id.calibmessage);
		CameraTracker.calibmessage.setText("");
		CameraTracker.startCalibration();
	}

	// disable the back button
	@Override
	public void onBackPressed() {
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
			View rootView = inflater.inflate(R.layout.calibration_fragment,
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
			
			//wait until mCamera gets initialized in a separate thread, 
			//otherwise we'll get null pointer exception
			while (mCamera == null) {}
		
			Camera.Parameters parameters = mCamera.getParameters();
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
