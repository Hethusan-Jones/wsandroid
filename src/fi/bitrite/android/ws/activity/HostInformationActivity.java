package fi.bitrite.android.ws.activity;

import roboguice.activity.RoboActivity;
import roboguice.inject.InjectView;
import roboguice.util.Strings;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.inject.Inject;

import fi.bitrite.android.ws.R;
import fi.bitrite.android.ws.auth.http.HttpAuthenticationService;
import fi.bitrite.android.ws.auth.http.HttpSessionContainer;
import fi.bitrite.android.ws.host.impl.HttpHostInformation;
import fi.bitrite.android.ws.model.Host;
import fi.bitrite.android.ws.persistence.StarredHostDao;

public class HostInformationActivity extends RoboActivity {

	public static final int RESULT_SHOW_HOST_ON_MAP = RESULT_FIRST_USER + 1;
	
	private static final int NO_ID = 0;

	@InjectView(R.id.layoutHostDetails)
	LinearLayout hostDetails;

	@InjectView(R.id.btnHostStar) ImageView star;
	@InjectView(R.id.txtHostFullname) TextView fullname;
	@InjectView(R.id.txtHostComments) TextView comments;
	@InjectView(R.id.txtHostLocation) TextView location;
	@InjectView(R.id.txtHostMobilePhone) TextView mobilePhone;
	@InjectView(R.id.txtHostHomePhone) TextView homePhone;
	@InjectView(R.id.txtHostWorkPhone) TextView workPhone;
	@InjectView(R.id.txtPreferredNotice) TextView preferredNotice;
	@InjectView(R.id.txtMaxGuests) TextView maxGuests;
	@InjectView(R.id.txtNearestAccomodation) TextView nearestAccomodation;
	@InjectView(R.id.txtCampground) TextView campground;
	@InjectView(R.id.txtBikeShop) TextView bikeShop;
	@InjectView(R.id.txtServices) TextView services;

	@Inject HttpAuthenticationService authenticationService;
	@Inject HttpSessionContainer sessionContainer;

	@Inject StarredHostDao starredHostDao;

	private DialogHandler dialogHandler;
	
	private Host host;
	private int id;
	private boolean starred;
	private boolean forceUpdate;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.host_information);
		dialogHandler = new DialogHandler(this);
		starredHostDao.open();

		if (savedInstanceState != null) {
			host = savedInstanceState.getParcelable("host");
			id = savedInstanceState.getInt("id");
			setViewContentFromHost();
		} else {
			Intent i = getIntent();
			host = (Host) i.getParcelableExtra("host");
			id = i.getIntExtra("id", NO_ID);
			forceUpdate = i.getBooleanExtra("update", false);
			
			starred = starredHostDao.isHostStarred(id, host.getName());
			setupStar();
			
			if (!starred || forceUpdate) {
				dialogHandler.showDialog(DialogHandler.HOST_INFORMATION);
				getHostInformationAsync();
			} else {
				host = starredHostDao.get(id, host.getName());
				setViewContentFromHost();
			}
		}
		
		fullname.setText(host.getFullname());
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// TODO: save dialog state?
		outState.putParcelable("host", host);
		outState.putInt("id", id);
		super.onSaveInstanceState(outState);
	}

	private void setupStar() {
		int drawable = starred ? R.drawable.starred_on : R.drawable.starred_off;
		star.setImageDrawable(getResources().getDrawable(drawable));
		star.setVisibility(View.VISIBLE);
	}

	public void showStarHostDialog(View view) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(starred ? "Un-star this host?" : "Star this host?")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                HostInformationActivity.this.toggleHostStarred();
		                dialog.dismiss();
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   dialog.cancel();
		           }
		       });
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	protected void toggleHostStarred() {
		if (starredHostDao.isHostStarred(id, host.getName())) {
			starredHostDao.delete(id, host.getName());
		} else {
			starredHostDao.insert(id, host.getName(), host);
		}
		
		starred = !starred;
		setupStar();
	}
	
	public void contactHost(View view) {
		Intent i = new Intent(HostInformationActivity.this, HostContactActivity.class);
		i.putExtra("host", host);
		i.putExtra("id", id);
		startActivity(i);
	}
	
	public void showHostOnMap(View view) {
		// We need to finish the host info dialog, switch to the map tab and 
		// zoom/scroll to the location of the host
		Intent resultIntent = new Intent();
		
		if (!Strings.isEmpty(host.getLatitude()) && !Strings.isEmpty(host.getLongitude())) {
			int lat = (int) Math.round(Float.parseFloat(host.getLatitude()) * 1.0e6);
			int lon = (int) Math.round(Float.parseFloat(host.getLongitude()) * 1.0e6);
			resultIntent.putExtra("lat", lat);
			resultIntent.putExtra("lon", lon);
		}
		
		setResult(RESULT_SHOW_HOST_ON_MAP, resultIntent);
		finish();
	}

	@Override
	protected Dialog onCreateDialog(int id, Bundle args) {
		return dialogHandler.createDialog(id, "Retrieving host information ...");
	}

	private void getHostInformationAsync() {
		new HostInformationThread(handler, id).start();
	}

	final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			dialogHandler.dismiss();

			Object obj = msg.obj;

			if (obj instanceof Exception || msg.arg1 == NO_ID) {
				dialogHandler.alert(
						"Could not retrieve host information. Check your credentials and internet connection.");
				return;
			}

			id = msg.arg1;
			host = (Host) obj;
			
			setViewContentFromHost();
			
			if (starred && forceUpdate) {
				starredHostDao.update(id, host.getName(), host);
				dialogHandler.alert(getResources().getString(R.string.host_updated));
			}

			if (host.isNotCurrentlyAvailable()) {
				dialogHandler.alert(getResources().getString(R.string.host_not_available));
			}
		}

	};

	private void setViewContentFromHost() {
		comments.setText(host.getComments());
		location.setText(host.getLocation());
		mobilePhone.setText(host.getMobilePhone());
		homePhone.setText(host.getHomePhone());
		workPhone.setText(host.getWorkPhone());
		preferredNotice.setText(host.getPreferredNotice());
		maxGuests.setText(host.getMaxCyclists());
		nearestAccomodation.setText(host.getMotel());
		campground.setText(host.getCampground());
		bikeShop.setText(host.getBikeshop());
		services.setText(host.getServices());

		hostDetails.setVisibility(View.VISIBLE);
	}

	private class HostInformationThread extends Thread {
		Handler handler;
		int id;
		
		public HostInformationThread(Handler handler, int id) {
			this.handler = handler;
			this.id = id;
		}

		public void run() {
			Message msg = handler.obtainMessage();

			try {
				HttpHostInformation hostInfo = new HttpHostInformation(authenticationService, sessionContainer);

				if (id == NO_ID) {
					id = hostInfo.getHostId(host.getName());
				}

				msg.arg1 = id;
				msg.obj = hostInfo.getHostInformation(id);
			}

			catch (Exception e) {
				Log.e("WSAndroid", e.getMessage(), e);
				msg.obj = e;
			}

			handler.sendMessage(msg);
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.host_information_menu, menu);
	    return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// we only have one option so keep it simple
		Intent i = new Intent();
		i.putExtra("host", host);
		i.putExtra("id", "id");
		i.putExtra("update", true);
		setIntent(i);
		onCreate(null);
		return true;
	}	
	
	@Override
	protected void onResume() {
		super.onResume();
		starredHostDao.open();
	}

	@Override
	protected void onPause() {
		super.onPause();
		starredHostDao.close();
	}	

}
