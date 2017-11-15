package me.calebjones.spacelaunchnow.ui.launchdetail.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.hdodenhof.circleimageview.CircleImageView;
import io.realm.Realm;
import io.realm.RealmList;
import me.calebjones.spacelaunchnow.R;
import me.calebjones.spacelaunchnow.common.BaseActivity;
import me.calebjones.spacelaunchnow.content.database.ListPreferences;
import me.calebjones.spacelaunchnow.data.models.Launch;
import me.calebjones.spacelaunchnow.data.models.RocketDetails;
import me.calebjones.spacelaunchnow.data.networking.DataClient;
import me.calebjones.spacelaunchnow.data.networking.responses.launchlibrary.LaunchResponse;
import me.calebjones.spacelaunchnow.ui.launchdetail.TabsAdapter;
import me.calebjones.spacelaunchnow.ui.main.MainActivity;
import me.calebjones.spacelaunchnow.ui.supporter.SupporterHelper;
import me.calebjones.spacelaunchnow.utils.analytics.Analytics;
import me.calebjones.spacelaunchnow.utils.customtab.CustomTabActivityHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class LaunchDetailActivity extends BaseActivity
        implements AppBarLayout.OnOffsetChangedListener {

    private static final int PERCENTAGE_TO_ANIMATE_AVATAR = 20;
    @BindView(R.id.fab_share)
    FloatingActionButton fabShare;
    @BindView(R.id.adView)
    AdView adView;
    private boolean mIsAvatarShown = true;

    private TabLayout tabLayout;
    private ViewPager viewPager;
    private AppBarLayout appBarLayout;
    private ImageView detail_profile_backdrop;
    private CircleImageView detail_profile_image;
    private TextView detail_rocket, detail_mission_location;
    private int mMaxScrollSize;
    private SharedPreferences sharedPref;
    private ListPreferences sharedPreference;
    private CustomTabActivityHelper customTabActivityHelper;
    private Context context;
    private TabsAdapter tabAdapter;
    private int statusColor;

    public String response;
    public Launch launch;

    public LaunchDetailActivity() {
        super("Launch Detail Activity");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }

        int m_theme;

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        context = getApplicationContext();
        customTabActivityHelper = new CustomTabActivityHelper();
        sharedPreference = ListPreferences.getInstance(context);

        if (sharedPreference.isNightModeActive(this)) {
            statusColor = ContextCompat.getColor(context, R.color.darkPrimary_dark);
        } else {
            statusColor = ContextCompat.getColor(context, R.color.colorPrimaryDark);
        }
        m_theme = R.style.BaseAppTheme;

        if (getSharedPreferences("theme_changed", 0).getBoolean("recreate", false)) {
            SharedPreferences.Editor editor = getSharedPreferences("theme_changed", 0).edit();
            editor.putBoolean("recreate", false);
            editor.apply();
            recreate();
        }

        setTheme(m_theme);
        setContentView(R.layout.activity_launch_detail);
        ButterKnife.bind(this);

        if (!SupporterHelper.isSupporter()) {
            AdRequest adRequest = new AdRequest.Builder().build();
            adView.setVisibility(View.VISIBLE);
            adView.loadAd(adRequest);
        } else {
            adView.setVisibility(View.GONE);
        }

        //Setup Views
        tabLayout = (TabLayout) findViewById(R.id.detail_tabs);
        viewPager = (ViewPager) findViewById(R.id.detail_viewpager);
        appBarLayout = (AppBarLayout) findViewById(R.id.detail_appbar);
        detail_profile_image = (CircleImageView) findViewById(R.id.detail_profile_image);
        detail_profile_backdrop = (ImageView) findViewById(R.id.detail_profile_backdrop);
        detail_rocket = (TextView) findViewById(R.id.detail_rocket);
        detail_mission_location = (TextView) findViewById(R.id.detail_mission_location);

        //Grab information from Intent
        Intent mIntent = getIntent();
        String type = mIntent.getStringExtra("TYPE");

        if (type != null && type.equals("launch")) {
            final int id = mIntent.getIntExtra("launchID", 0);
            DataClient.getInstance().getLaunchById(id, true, new Callback<LaunchResponse>() {
                @Override
                public void onResponse(Call<LaunchResponse> call, Response<LaunchResponse> response) {
                    Realm realm = Realm.getDefaultInstance();
                    if (response.isSuccessful()) {
                        RealmList<Launch> items = new RealmList<>(response.body().getLaunches());
                        for (Launch item : items) {
                            Launch previous = realm.where(Launch.class)
                                    .equalTo("id", item.getId())
                                    .findFirst();
                            if (previous != null) {
                                item.setEventID(previous.getEventID());
                                item.setSyncCalendar(previous.syncCalendar());
                                item.setLaunchTimeStamp(previous.getLaunchTimeStamp());
                                item.setIsNotifiedDay(previous.getIsNotifiedDay());
                                item.setIsNotifiedHour(previous.getIsNotifiedHour());
                                item.setIsNotifiedTenMinute(previous.getIsNotifiedTenMinute());
                                item.setNotifiable(previous.isNotifiable());
                            }
                            realm.beginTransaction();
                            item.getLocation().setPrimaryID();
                            realm.copyToRealmOrUpdate(item);
                            realm.commitTransaction();
                            updateViews(item);
                            Timber.v("Updated detailLaunch: %s", item.getId());
                        }
                    } else {
                        Launch item = realm.where(Launch.class)
                                .equalTo("id", id)
                                .findFirst();
                        updateViews(item);
                    }
                    realm.close();
                }

                @Override
                public void onFailure(Call<LaunchResponse> call, Throwable t) {
                    Realm realm = Realm.getDefaultInstance();
                    Launch item = realm.where(Launch.class)
                            .equalTo("id", id)
                            .findFirst();
                    updateViews(item);
                    realm.close();
                }
            });

        }

        final Toolbar toolbar = (Toolbar) findViewById(R.id.detail_toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(context, MainActivity.class);
                            context.startActivity(intent);

                        }
                    }

            );
        }

        appBarLayout.addOnOffsetChangedListener(this);
        mMaxScrollSize = appBarLayout.getTotalScrollRange();

        tabAdapter = new TabsAdapter(getSupportFragmentManager());

        viewPager.setAdapter(tabAdapter);

        tabLayout.setupWithViewPager(viewPager);
    }

    private void updateViews(Launch launch) {
        try {
            this.launch = launch;

            tabAdapter.updateAllViews(launch);
            if (!this.isDestroyed() && launch != null && launch.getRocket() != null) {
                Timber.v("Loading detailLaunch %s", launch.getId());
                findProfileLogo();
                if (launch.getRocket().getName() != null) {
                    if (launch.getRocket().getImageURL() != null && launch.getRocket().getImageURL().length() > 0) {

                        Glide.with(this)
                                .load(launch.getRocket().getImageURL())
                                .centerCrop()
                                .placeholder(R.drawable.placeholder)
                                .crossFade()
                                .into(detail_profile_backdrop);
                        getLaunchVehicle(launch, false);
                    } else {
                        getLaunchVehicle(launch, true);
                    }
                }
            } else if (this.isDestroyed()) {
                Timber.v("DetailLaunch is destroyed, stopping loading data.");
            }

            //Assign the title and mission location data
            detail_rocket.setText(launch.getName());
        } catch (NoSuchMethodError e) {
            Timber.e(e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public int reverseNumber(int num, int min, int max) {
        int number = (max + min) - num;
        return number;
    }

    private void findProfileLogo() {

        //Default location, mission is unknown.
        String location = "Unknown Location";
        String mission = "Unknown Mission";
        String locationCountryCode = null;
        String rocketAgency = "";

        if (launch.getRocket().getAgencies().size() > 0) {
            for (int i = 0; i < launch.getRocket().getAgencies().size(); i++) {
                rocketAgency = rocketAgency + launch.getRocket().getAgencies().get(i).getAbbrev() + " ";
            }
        }

        //This checks to see if a location is available
        if (launch.getLocation().getName() != null) {

            //Check to see if a countrycode is available
            if (launch.getLocation().getPads().size() > 0 && launch.getLocation().getPads().
                    get(0).getAgencies().size() > 0) {
                locationCountryCode = launch.getLocation().getPads().
                        get(0).getAgencies().get(0).getCountryCode();

                Timber.v(
                        "LaunchDetailActivity - CountryCode length: %s",
                        String.valueOf(locationCountryCode.length())
                );

                //Go through various CountryCodes and assign flag.
                if (locationCountryCode.length() == 3) {

                    if (locationCountryCode.contains("USA")) {
                        //Check for SpaceX/Boeing/ULA/NASA
                        if (launch.getRocket().getAgencies().size() > 0) {
                            if (launch.getLocation().getPads().
                                    get(0).getAgencies().get(0).getAbbrev().contains("SpX") && launch.getRocket().getAgencies().get(0).getAbbrev().contains("SpX")) {
                                //Apply SpaceX Logo
                                applyProfileLogo(getString(R.string.spacex_logo));
                            }
                        }
                        if (launch.getLocation().getPads().
                                get(0).getAgencies().get(0).getAbbrev() == "BA" && launch.getRocket().getAgencies().get(0).getCountryCode() == "UKR") {
                            //Apply Yuzhnoye Logo
                            applyProfileLogo(getString(R.string.Yuzhnoye_logo));
                        } else if (rocketAgency.contains("ULA")) {
                            //Apply ULA Logo
                            applyProfileLogo(getString(R.string.ula_logo));
                        } else {
                            //Else Apply USA flag
                            applyProfileLogo(getString(R.string.usa_flag));
                        }
                    } else if (locationCountryCode.contains("RUS")) {
                        //Apply Russia Logo
                        applyProfileLogo(getString(R.string.rus_logo));
                    } else if (locationCountryCode.contains("CHN")) {
                        applyProfileLogo(getString(R.string.chn_logo));
                    } else if (locationCountryCode.contains("IND")) {
                        applyProfileLogo(getString(R.string.ind_logo));
                    } else if (locationCountryCode.contains("JPN")) {
                        applyProfileLogo(getString(R.string.jpn_logo));
                    }

                } else if (launch.getLocation().getPads().
                        get(0).getAgencies().get(0).getAbbrev() == "ASA") {
                    //Apply Arianespace Logo
                    applyProfileLogo(getString(R.string.ariane_logo));
                }
                location = (launch.getLocation().getPads().get(0).getName());
            }
        }
        //Assigns the result of the two above checks.
        detail_mission_location.setText(location);
    }

    private void applyProfileLogo(String url) {
        Timber.d("LaunchDetailActivity - Loading Profile Image url: %s ", url);

        Glide.with(this)
                .load(url)
                .centerCrop()
                .placeholder(R.drawable.icon_international)
                .error(R.drawable.icon_international)
                .into(detail_profile_image);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void getLaunchVehicle(Launch result, boolean setImage) {
        String query;
        if (result.getRocket().getName().contains("Space Shuttle")) {
            query = "Space Shuttle";
        } else {
            query = result.getRocket().getName();
        }
        RocketDetails launchVehicle = getRealm().where(RocketDetails.class)
                .contains("name", query)
                .findFirst();
        if (setImage) {
            if (launchVehicle != null && launchVehicle.getImageURL().length() > 0) {
                Glide.with(this)
                        .load(launchVehicle
                                .getImageURL())
                        .centerCrop()
                        .placeholder(R.drawable.placeholder)
                        .crossFade()
                        .into(detail_profile_backdrop);
                Timber.d("Glide Loading: %s %s", launchVehicle.getLV_Name(), launchVehicle.getImageURL());
            }
        }
    }

    public void setData(String data) {
        response = data;
        Timber.v("LaunchDetailActivity - %s", response);
        Scanner scanner = new Scanner(response);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            // process the line
            Timber.v("setData - %s ", line);
        }
        scanner.close();
    }

    public Launch getLaunch() {
        return launch;
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
        int totalScroll = appBarLayout.getTotalScrollRange();
        int currentScroll = totalScroll + verticalOffset;

        int color = statusColor;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = (color >> 0) & 0xFF;

        if (mMaxScrollSize == 0) {
            mMaxScrollSize = appBarLayout.getTotalScrollRange();
        }

        int percentage = (Math.abs(verticalOffset)) * 100 / mMaxScrollSize;

        if (percentage >= PERCENTAGE_TO_ANIMATE_AVATAR && mIsAvatarShown) {
            mIsAvatarShown = false;
            detail_profile_image.animate().scaleY(0).scaleX(0).setDuration(200).start();
            fabShare.hide();
        }

        if (percentage <= PERCENTAGE_TO_ANIMATE_AVATAR && !mIsAvatarShown) {
            mIsAvatarShown = true;

            detail_profile_image.animate()
                    .scaleY(1).scaleX(1)
                    .start();

            fabShare.show();
        }

        Timber.v("Total: %s Current: %s RGB: %d %d %d", totalScroll, currentScroll, r, g, b);
        if ((currentScroll) < 200) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                Window window = getWindow();
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(Color.argb(reverseNumber(currentScroll, 0, 255), r, g, b));
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Window window = getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            }
        }
    }

    public void onStart() {
        super.onStart();
        Timber.v("LaunchDetailActivity onStart!");
        customTabActivityHelper.bindCustomTabsService(this);
    }

    public void onStop() {
        super.onStop();
        Timber.v("LaunchDetailActivity onStop!");
        customTabActivityHelper.unbindCustomTabsService(this);
    }

    public void mayLaunchUrl(Uri parse) {
        if (customTabActivityHelper.mayLaunchUrl(parse, null, null)) {
            Timber.v("mayLaunchURL Accepted - %s", parse.toString());
        } else {
            Timber.v("mayLaunchURL Denied - %s", parse.toString());
        }
    }

    @OnClick(R.id.fab_share)
    public void onViewClicked() {
        Date date = launch.getNet();
        SimpleDateFormat df = new SimpleDateFormat("EEEE, MMMM dd, yyyy - hh:mm a zzz");
        df.toLocalizedPattern();
        String launchDate = df.format(date);
        String message;
        if (launch.getVidURLs().size() > 0) {
            if (launch.getLocation() != null && launch.getLocation().getPads().size() > 0 && launch.getLocation().getPads().
                    get(0).getAgencies().size() > 0) {

                message = launch.getName() + " launching from "
                        + launch.getLocation().getName() + "\n\n"
                        + launchDate;
            } else if (launch.getLocation() != null) {
                message = launch.getName() + " launching from "
                        + launch.getLocation().getName() + "\n\n"
                        + launchDate;
            } else {
                message = launch.getName()
                        + "\n\n"
                        + launchDate;
            }
        } else {
            if (launch.getLocation() != null && launch.getLocation().getPads().size() > 0 && launch.getLocation().getPads().
                    get(0).getAgencies().size() > 0) {

                message = launch.getName() + " launching from "
                        + launch.getLocation().getName() + "\n\n"
                        + launchDate;
            } else if (launch.getLocation() != null) {
                message = launch.getName() + " launching from "
                        + launch.getLocation().getName() + "\n\n"
                        + launchDate;
            } else {
                message = launch.getName()
                        + "\n\n"
                        + launchDate;
            }
        }
        ShareCompat.IntentBuilder.from(this)
                .setType("text/plain")
                .setChooserTitle("Share: " + launch.getName())
                .setText(String.format("%s\n\nWatch Live: %s", message, launch.getUrl()))
                .startChooser();
        Analytics.from(context).sendLaunchShared("Share FAB", launch.getName() + "-" + launch.getId().toString());
    }
}
