package com.headupbeta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.FaceDetector;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class CameraTracker extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
	
	
	public TrackingActivity track=null;//for controlling the tracking activity when tracking posture	
	public static CalibrationActivity calib=null;//for controlling the calibration activity during the calibration phase	
	public static boolean switchOn=true;//determines whether the on and off switch is on or off
	
	//determines whether the angle of the device does not match the angle in the database,
	//in the tracking activity
	public static boolean wrongPosition=false;
	
	
	public static int sensitivity=11;//used in the posture detecting algorithm	
	public static TextView calibmessage=null;//the "Calibrating your settings" message	
	public static TextView middleFeedback=null;//the text label in the middle of the screen in TrackingActivity	
	public static boolean getFront=false;//flag determines whether we're getting the first reading	
	public static boolean getBack=false;//flag determines whether we're getting the second reading	
	public static double angle=0;//stores the device's angle
	
	//the angle of the device that's going to be stored in the database
	//after each of two measurements that are recorded, the dbAngle will 
	//become the average of the two angles recorded upon each of the successful readings
	public static double dbAngle=0;
	public static Context caContext=null;
	private SurfaceHolder mHolder;
	private Camera mCamera;
	public Bitmap mWorkBitmap;
	public FaceDetector mFaceDetector;
	
	//we're finding 2 faces maximum. should really be 1
	public int NUM_FACES = 2;	
	public int previewCounter=0;//used for processing only one face every four, otherwise two of the same eye distances get taken too quickly	
	public static boolean sitBackPressed=false;//flag which determines if the second measurement is being taken
	public boolean findingFaces=false;//flag that determines if a face is supposed to be detected	
	public static boolean first=false;//flag that determines if we're in the process of taking the first measurement	
	public  boolean second=false;//flag that determines if we're in the process of taking the second measurement	
	public boolean ready=false;	//flag that tells the preview callback that we're tracking posture in real time
	public double firstEyes=0;//the variable that stores the distacne between the eyes after the first measurement is taken	
	public int firstElev=0;//firstElev stores the eye level in regards to the center of the screen after the first reading is taken	
	public double secondEyes=0;//the variable that stores the distacne between the eyes after the second measurement is taken	
	public int secondElev=0;//stores the eye level in regards to the center of the screen after the second reading is taken
	
	//number of successive eye distance readings that are the same, for the first and second measurements, respectively
	public int firstTimes=1;
	public int secondTimes=1;	
	
	public double perspDrop=0;//a variablke used in the algorithm when determining whether posture is good
	public double farthestRatio=.75;//ratio used for determining when the user is too far for his posture to be tracked
	public double eyePostLevel=0;
	public double eyePostRatio=.2;
	public static boolean calibrationPause=false;//flag for determining if a pop up is active	
	public static boolean faceNeeded=true;	//flag that tells if  a face is supposed to be detected
	
	public static ArrayList<String> log = null;
	public static Context cont=null;	
	
	public static int globalHeight=800;//1280 //the height of the picture taken, in pixels
	public static int globalWidth=480;//720 //width of the picture taken, in pixels
	public static Bitmap result=null;
	public static Thread th1=null;
	private FaceDetector.Face[] mFaces = new FaceDetector.Face[NUM_FACES];
	private FaceDetector.Face face = null;      // refactor this to the callback

	private PointF eyesMidPts[] = new PointF[NUM_FACES];
	private float  eyesDistance[] = new float[NUM_FACES];
	
	//called from the CalibrationActivity
	public static void startCalibration() {
		
		//set the flag that lets ther preview callback know we're in the process of getting the first reading
		first = true;
		
		//calibrationPause is true while the pop up is being displayed, until OK is pressed
		calibrationPause = true;

		//show the pop up with instruction for getting the first reading
		calib.runOnUiThread(new Runnable() {
			@Override
			public void run() {

				// stuff that updates ui
				Builder alert = new AlertDialog.Builder(cont);
				alert.setTitle("Calibration");
				alert.setMessage("Sit straight up with your arm's length away from the screen.");
				alert.setPositiveButton("OK",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
									//upon clicking OK, let the preview callback know to detect the face, in order to get the measurements
									calibrationPause = false;
									calibmessage.setText("Calibrating your settings...");
								}
						});
				alert.show();
			}
		});

		CameraTracker.getBack = true;

	}

	public CameraTracker(Context context, Camera camera) {
		super(context);
		cont = context;
		mCamera = camera;
		mHolder = getHolder();
		mHolder.addCallback(this);
		// deprecated setting, but required on Android versions prior to 3.0
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		//
	}

	public void surfaceCreated(SurfaceHolder holder) {
		
		//initialize the log ArrayList , to store posture tracking feedback which is used later for displaying alerts
		if (log == null) {
			log = new ArrayList<String>();
		}
		try {
			// create the surface and start camera preview
			if (mCamera == null) {
				SurfaceView dummy = new SurfaceView(this.getContext());
				Parameters params = mCamera.getParameters();
				mCamera.setParameters(params);
				mCamera.setPreviewDisplay(holder);
				mCamera.startPreview();

			}
		} catch (IOException e) {
			Log.d(VIEW_LOG_TAG,
					"Error setting camera preview: " + e.getMessage());
		}
	}

	public void refreshCamera(Camera camera) {

		if (mHolder.getSurface() == null) {
			// preview surface does not exist
			return;
		}
		// stop preview before making changes
		try {
			mCamera.stopPreview();
		} catch (Exception e) {
			// ignore: tried to stop a non-existent preview
		}
		// set preview size and make any resize, rotate or
		// reformatting changes here
		// start preview with new settings
		setCamera(camera);
		try {
			
			//if we're supposed to be detecting a face (for example the pop up is not currently displayed),
			//copy the image from the preview into a buffer
			if (faceNeeded) {

				Thread thr = new Thread() {
					public void run() {

					}
				};

				mCamera.setPreviewDisplay(mHolder);
				// Setting the camera's aspect ratio
				Camera.Parameters parameters = mCamera.getParameters();

				mWorkBitmap = Bitmap.createBitmap(globalHeight, globalHeight,
						Bitmap.Config.RGB_565);

				mFaceDetector = new FaceDetector(
						(int) (mWorkBitmap.getWidth()),
						(int) (mWorkBitmap.getWidth()), 2);

				mCamera.setParameters(parameters);
			
				int bufSize = globalHeight
						* globalWidth
						* ImageFormat.getBitsPerPixel(parameters
								.getPreviewFormat()) / 8;
				byte[] cbBuffer = new byte[bufSize];
				mCamera.setPreviewCallbackWithBuffer(this);
				mCamera.addCallbackBuffer(cbBuffer);
				mCamera.startPreview();

				int thew = parameters.getPreviewSize().width;
				int theh = parameters.getPreviewSize().height;

				this.setX(-800);
			}
		} catch (Exception e) {
			Log.d(VIEW_LOG_TAG,
					"Error starting camera preview: " + e.getMessage());
		}
	}


	   /* Camera.PreviewCallback implementation */
	public void onPreviewFrame(final byte[] data, Camera camera) {
		
		//if the switch is in the ON position, set the middle label to blank and wait for toast feedback
		if (switchOn == false) {
			track.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					middleFeedback.setText("");
				}
			});

		}
		
		//if the middleFeedback is initialized (therefore the the application is tracking posture)
		if (middleFeedback != null) {
			
			//if the current angle is less than 2 degrees off of what's in the database, let the user know
			//to place the device on the dock
			if (Math.abs(dbAngle - angle) > 2) {
				
				//only display the message if the switch is turned on
				if (switchOn == true) {
					
					//set the wrongPosition flag to true, indicating that the angle is not right
					wrongPosition = true;
					track.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							middleFeedback.setText("");
							// stuff that updates ui
							middleFeedback.setText("Place your device on the dock.");
							middleFeedback.setTextColor(Color.RED);
						}
					});

				}
			} else {
				//the angle of the device is good, wrongPosition is false
				track.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						String mfText = (String) middleFeedback.getText();
						
						//if "Not in front of the screen" isn't already appearing in the middle, set the middle
						//label to blank, otherwise "Not in front of the screen" will be flashing in the middle
						//of the screen
						if (!mfText.equals("Not in front of the screen.")) {
							middleFeedback.setText("");
							middleFeedback.setTextColor(Color.GRAY);

						}
					}
				});

				wrongPosition = false;
			}
		}
		if (sitBackPressed == true) {
			try {
				Thread.sleep(10000);

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			sitBackPressed = false;
		}
		// Log.i("Face","counter: "+previewCounter);
		previewCounter++;

		if (faceNeeded) {

			Thread thr = new Thread() {
				public void run() {
					// face detection: first convert the image from NV21 to
					// RGB_565
					YuvImage yuv = new YuvImage(data, ImageFormat.NV21,
							mWorkBitmap.getWidth(), mWorkBitmap.getHeight(),
							null);

					Rect rect = new Rect(0, 0, mWorkBitmap.getWidth(),
							mWorkBitmap.getHeight()); // TODO: make rect a
														// member and use it for
														// width and height
														// values above

					// TODO: use a threaded option or a circular buffer for
					// converting streams? see
					// http://ostermiller.org/convert_java_outputstream_inputstream.html
					ByteArrayOutputStream baout = new ByteArrayOutputStream();

					if (!yuv.compressToJpeg(rect, 100, baout)) {
						// Log.e(TAG, "compressToJpeg failed");
					}

					BitmapFactory.Options bfo = new BitmapFactory.Options();
					bfo.inPreferredConfig = Bitmap.Config.RGB_565;
					mWorkBitmap = BitmapFactory.decodeStream(
							new ByteArrayInputStream(baout.toByteArray()),
							null, bfo);

					Matrix matrix = new Matrix();

					matrix.postRotate(270);
					mWorkBitmap = Bitmap.createBitmap(mWorkBitmap, 0, 0,
							mWorkBitmap.getWidth(), mWorkBitmap.getWidth(),
							matrix, false);

					Arrays.fill(mFaces, null); // use arraycopy instead?
					Arrays.fill(eyesMidPts, null); // use arraycopy instead?
					result = mWorkBitmap.createScaledBitmap(mWorkBitmap,
							(int) (mWorkBitmap.getWidth()),
							(int) (mWorkBitmap.getWidth()), false);

				}
			};
			thr.start();
			try {
				thr.join();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			Thread th = new Thread() {
				public void run() {
					findingFaces = true;

					findingFaces = false;
				}
			};
			th.start();
			int counter = previewCounter % 4;
			if (counter == 2) {
				mFaceDetector.findFaces(result, mFaces);
			}
			
			Parameters params = mCamera.getParameters();
			List<Camera.Size> sizes = params.getSupportedPictureSizes();
			/*
			 * for(int i=0;i<sizes.size();i++){ Camera.Size thesize =
			 * (Camera.Size)sizes.get(i);
			 * Log.i("Face","Size "+(i+1)+" Height: "+
			 * thesize.height+" Width: "+thesize.width); }
			 */
			// //
			if (1 == 1) {
				
				//get the face detection information
				for (int i = 0; i < mFaces.length; i++) {
					
					//we're really only going to find one face
					face = mFaces[i];
					try {
						PointF eyesMP = new PointF();
						face.getMidPoint(eyesMP);
						eyesDistance[i] = face.eyesDistance();
						eyesMidPts[i] = eyesMP;
						double eyeMidPt = globalHeight / 2 - eyesMP.y;
						if (true) {
							Log.i("Face", face.eyesDistance() + " "
									+ "Eyes Midpoint: (" + eyesMidPts[i].x
									+ "," + eyesMidPts[i].y + ")"
									+ "Eyes Distance: (" + eyesDistance[i]
									+ ")");
						}

						////////////////////////
						
						//calibrationPause = when the popup appears with instructions.  false when the popup is not active
						//true when we're waiting for the user to click "OK"
						if (calibrationPause == false) {
							
							//if this is the first calibration
							if (first == true) {
								
								//if the distance between the eyes that we just got equals to the previous saved eye distance
								if (face.eyesDistance() == firstEyes) {
									//firstTimes = number of measurements taken during the first calibration
									//only 2 measurements total are taken
									firstTimes++;
									firstElev += (eyeMidPt);
									
									//if we have two eye distances that are the same and therefore that's what we want to use
									if (firstTimes == 2) {
										firstElev = firstElev / 3;
										firstEyes = face.eyesDistance();
										dbAngle = angle;
										
										
										//set calibraitonPause to true because we're about to show a popup box
										//once the user clicks "OK" calibrationPause will be set back to false
										calibrationPause = true;
										calib.runOnUiThread(new Runnable() {
											@Override
											public void run() {

												Builder alert = new AlertDialog.Builder(
														cont);
												alert.setTitle("Calibration");
												alert.setMessage("Move back about a foot and sit up straight.");
												alert.setPositiveButton(
														"OK",
														new DialogInterface.OnClickListener() {
															public void onClick(
																	DialogInterface dialog,
																	int which) {
																
																//if the user clicks OK, then face detectio should be restarted
																calibrationPause = false;
																
																//start the second calibration step.  set the flags
																sitBackPressed = true;
																CameraTracker.getBack = true;
																second = true;
															}
														});
												alert.show();
												calibmessage
														.setText("Calibrating your settings...");
											}
										});

										//done with taking the first reading
										first = false;
									}
								} else {
									//if we didn't get two of the same eye distances in a row, restart the procedure
									//and set firstEyes to the eyeDistance, so that the next reading might match up
									firstEyes = face.eyesDistance();
									firstElev = (int) eyeMidPt;
									
									//only one equal reading (the only reading) 
									firstTimes = 1;
								}
							}
							
							//if the second reading is being taken
							if (second == true) {
								
								//if the eye distance matches the last recorded eye distance
								if (face.eyesDistance() == secondEyes) {
									Log.i("Face", "made it to calib");
									
									//increment the number of times we got the same reading in a row
									secondTimes++;
									
									//add the cumulative eye level
									secondElev += (eyeMidPt);
									
									//if we got two of the same eye distance readings in a row
									if (secondTimes == 2) {
										
										//set the eye level equal to the total 
										secondElev = secondElev / 3;
										
										//set the official eye distance of the second reading to the current eye distance
										secondEyes = face.eyesDistance();
										
										
										perspDrop = (firstElev - secondElev)
												/ (firstEyes - secondEyes);
										
										//set the angle to be put into the database as the average between the two angle readings
										//during successive eye distance readings
										dbAngle = (dbAngle + angle) / 2;
										
										//done with taking the second reading
										second = false;
										
										//both measurement steps are taken,  posture is ready to be tracked
										ready = true;
										
										//for displaying the pop up that tells the user the readings have been taken
										calibrationPause = true;
										
										
										//display the popup explained above
										calib.runOnUiThread(new Runnable() {
											@Override
											public void run() {

												// stuff that updates ui
												calibmessage.setText("");
												Builder alert = new AlertDialog.Builder(
														cont);
												alert.setTitle("Calibration");
												alert.setMessage("Calibration is complete! Your settings have been saved.");
												alert.setPositiveButton(
														"OK",
														new DialogInterface.OnClickListener() {
															public void onClick(
																	DialogInterface dialog,
																	int which) {
																
																//when the user clicks OK,
																//let the callback know that it can resume processing images,
																//and create the table and insert the settings into it for later use
																calibrationPause = false;
																SQLiteDatabase db = SQLiteDatabase
																		.openOrCreateDatabase(
																				cont.getDatabasePath("testdb"),
																				null);
																db.execSQL("CREATE TABLE IF NOT EXISTS dataone(FIRSTELEV VARCHAR,PERSPDROP VARCHAR,FIRSTEYES VARCHAR,ANGLE VARCHAR);");
																String firstelev = Integer
																		.toString((int) firstElev);
																String firsteyes = Double
																		.toString(firstEyes);
																String perspdrop = Double
																		.toString(perspDrop);
																String dbangle = Double
																		.toString(dbAngle);
																db.execSQL("INSERT INTO dataone VALUES('"
																		+ firstelev
																		+ "','"
																		+ firsteyes
																		+ "','"
																		+ perspdrop
																		+ "','"
																		+ dbangle
																		+ "');");
																db.close();
																final Intent intent = new Intent(
																		cont,
																		TrackingActivity.class);
																
																//start the tracking activity and exit the calibration activity
																cont.startActivity(intent);
																System.exit(0);
															}
														});
												alert.show();
											}
										});

										CameraTracker.getBack = true;
										Log.i("Face", "firstEyes1: "
												+ firstEyes + "secondEyes:"
												+ secondEyes + "firstElev:"
												+ firstElev + "secondElev:"
												+ secondElev + "perspDrop:"
												+ perspDrop + " angle: "
												+ angle + " dbangle:" + dbAngle);

									}
								} else {
									
									//if we didn't get two same eye distances in a row,
									//set secondEyes to current eye distance in order to compare with the next reading
									secondEyes = face.eyesDistance();
									secondElev = (int) eyeMidPt;
									
									//1 successive reading in a row
									secondTimes = 1;
								}
							}
							
							//for tracking activity.  if we're "ready" to start tacking, and the device is
							//on the dock (comparing the angle with the db angle) and the switch is on, then track posture
							if (ready == true && wrongPosition == false
									&& switchOn == true) {
								
								//if the device is on the dock
								if (wrongPosition == false) {
									
									//if the face is too far according to the algorithm 
									//(the farther the face is, the more unreliable eye distance readings become)
									if (face.eyesDistance() < firstEyes
											* farthestRatio) {
										track.runOnUiThread(new Runnable() {
											@Override
											public void run() {
												middleFeedback
														.setText("Not in front of the screen.");
											}
										});

									} else {
										//if the face is not too far, set the feedback to blank and wait for 
										//feedback toasts
										track.runOnUiThread(new Runnable() {
											@Override
											public void run() {
												middleFeedback.setText("");
												// stuff that updates ui

											}
										});

									}
								}
								Log.i("Face",
										"ready" + Math.abs(dbAngle - angle));
								/*
								 * if(Math.abs(dbAngle-angle)>2){ //
								 * middleFeedback.setText("WRONG POSITION");
								 * }else{
								 */

								
								//if the current eye distance is greater than the first reading's eye distance,
								//let the user know that they're too close to the screen
								if (face.eyesDistance() > firstEyes) {
									// middleFeedback.setText("Not in front of the screen");

									log.add("TC");
									int numOfTC = 0;
									//if we got at least 5 readings since the start of tracking
									if (log.size() >= 5) {
										
										
										for (int j = log.size() - 5; j < log
												.size(); j++) {
											
											// look at the last five log list entries.  incrememnt the number of TC (too close)
											if (log.get(j).equals("TC")) {
												
												//incrememnt if we find TC in the last 5 log list entries. the total
												//number of TC therefore gets counted
												numOfTC++;
											}
										}
									}
									
									//if 3 or more of the last 5 readings were too close to the screen
									if (numOfTC >= 3) {
										track.runOnUiThread(new Runnable() {
											@Override
											public void run() {
												Toast toast = Toast.makeText(
														getContext(),
														"Too close!",
														Toast.LENGTH_LONG);
												LinearLayout toastLayout = (LinearLayout) toast
														.getView();
												TextView toastTV = (TextView) toastLayout
														.getChildAt(0);
												toast.setGravity(
														Gravity.CENTER, 0, 0);
												toastTV.setTextSize(34);
												toastTV.setTextColor(Color.RED);

												toast.show();
												// stuff that updates ui

											}
										});
										
										//reset the log List
										log.set(log.size() - 5, "NA");
										log.set(log.size() - 4, "NA");
										log.set(log.size() - 3, "NA");
										log.set(log.size() - 2, "NA");
										log.set(log.size() - 1, "NA");
									}

									// too close							

									Log.i("Face", "TOOCLOSE" + firstElev + " "
											+ firstEyes + " " + perspDrop);
								} else if (face.eyesDistance() < firstEyes
										* farthestRatio) {
									// too far
								
									Log.i("Face", "TOOFAR");
								} else {

									// middleFeedback.setText(""); too far
									Log.i("Face", "in the else");
									Log.i("Face", "elsefirstEyes1: "
											+ firstEyes + "secondEyes:"
											+ secondEyes + "firstElev:"
											+ firstElev + "secondElev:"
											+ secondElev + "perspDrop:"
											+ perspDrop + " angle: " + angle
											+ " dbangle:" + dbAngle);
									//the pixel level of good posture, according to the algorithm
									//good posture level = 
									int goodPostLevel = (int) ((int) firstElev - ((firstEyes - face
											.eyesDistance()) * perspDrop));
									Log.i("Face",
											goodPostLevel
													+ " "
													+ (goodPostLevel - face
															.eyesDistance()
															/ sensitivity)
													+ " " + eyeMidPt);
									
									//if the eye level is below the good posture level
									
									//bad posture
									if (eyeMidPt < (goodPostLevel - face
											.eyesDistance() / sensitivity)) {

										// bad posture
										
										//look at TC as example
										log.add("BP");
										int numOfBP = 0;
										if (log.size() >= 5) {
											for (int j = log.size() - 5; j < log
													.size(); j++) {
												if (log.get(j).equals("BP")) {
													numOfBP++;
												}
											}
										}
										if (numOfBP >= 3) {
											track.runOnUiThread(new Runnable() {
												@Override
												public void run() {
													Toast toast = Toast
															.makeText(
																	getContext(),
																	"Head up!",
																	Toast.LENGTH_LONG);
													LinearLayout toastLayout = (LinearLayout) toast
															.getView();
													TextView toastTV = (TextView) toastLayout
															.getChildAt(0);
													toast.setGravity(
															Gravity.CENTER, 0,
															0);
													toastTV.setTextSize(34);
													toastTV.setTextColor(Color.RED);

													toast.show();
													// stuff that updates ui

												}
											});

											log.set(log.size() - 5, "NA");
											log.set(log.size() - 4, "NA");
											log.set(log.size() - 3, "NA");
											log.set(log.size() - 2, "NA");
											log.set(log.size() - 1, "NA");
										}

										Log.i("Face", "BADPOSTURE");
									} else {
										// good posture - 0

										log.add("GP");
										int numOfGP = 0;
										if (log.size() >= 5) {
											for (int j = log.size() - 5; j < log
													.size(); j++) {
												if (log.get(j).equals("GP")) {
													numOfGP++;
												}
											}
										}
										if (numOfGP >= 3) {

										}
									
										Log.i("Face", "GOODPOSTURE");
									}
								}						

							}// ready=true
							

						}// calibrationpause

						
						// ///////////////////////
					} catch (Exception e) {}
				}
			}// if finding faces=true
			
			if (track != null) {
				track.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						invalidate();
					}
				});
			} else if (calib != null) {
				calib.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						invalidate();
					}
				});
			} else {
				invalidate();
			}

			mCamera.addCallbackBuffer(data);

		}// IFFACENEEDED

	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {

		// If your preview can change or rotate, take care of those events here.
		// Make sure to stop the preview before resizing or reformatting it.
		refreshCamera(mCamera);

		Log.d("MFACE", String.format("surfaceChanged: format=%d, w=%d, h=%d",
				format, w, h));

		if (mCamera != null) {

			Camera.Parameters parameters = mCamera.getParameters();
			mCamera.setParameters(parameters);
			mCamera.startPreview();
		}

	}

	public void setCamera(Camera camera) { 
		//method to set a camera instance
		mCamera = camera;
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {}
}