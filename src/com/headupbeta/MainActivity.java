package com.headupbeta;


import java.io.File;

import android.app.Activity;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.os.Build;

public class MainActivity extends Activity {
	
	boolean doesTableExist(SQLiteDatabase db, String tableName)
	{
	    if (tableName == null || db == null || !db.isOpen())
	    {
	        return false;
	    }
	    Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = ? AND name = ?", new String[] {"table", tableName});
	    if (!cursor.moveToFirst())
	    {
	        return false;
	    }
	    int count = cursor.getInt(0);
	    cursor.close();
	    return count > 0;
	}
	
	@Override
	public void onBackPressed(){}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);
		setTheme( android.R.style.Theme_Holo );
		
		//open the database, and if our settings table exists, load the tracking activity right away, upon start of the app
		SQLiteDatabase db = openOrCreateDatabase("testdb",Context.MODE_WORLD_WRITEABLE,null);
		File ddb = getBaseContext().getDatabasePath("testdb");
		Log.i("Face",ddb.getPath());

		if(doesTableExist(db,"dataone")){
			db.close();
			Intent trackIntent = new Intent(this,TrackingActivity.class);
		  	startActivity(trackIntent);
		}
		db.close();
	
		
		//the "Calibrate" button
		final Intent intent=new Intent(this,CalibrationActivity.class);
		Button calibrate = (Button) findViewById(R.id.button_calibrate);
		calibrate.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				// start the calibration activity
				startActivity(intent);
				System.exit(0);
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
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_about) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("HeadUp Beta 1.0\nCopyright 2015 Anton Aleynikov")
			       .setCancelable(false)
			       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                //do things
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
			View rootView = inflater.inflate(R.layout.main_fragment, container,
					false);
			return rootView;
		}
	}

}
