package me.calebjones.spacelaunchnow.content.jobs;

import android.app.PendingIntent;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.widget.RemoteViews;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import me.calebjones.spacelaunchnow.R;
import me.calebjones.spacelaunchnow.content.database.SwitchPreferences;
import me.calebjones.spacelaunchnow.content.util.QueryBuilder;
import me.calebjones.spacelaunchnow.data.models.Constants;
import me.calebjones.spacelaunchnow.data.models.Launch;
import me.calebjones.spacelaunchnow.ui.launchdetail.activity.LaunchDetailActivity;
import me.calebjones.spacelaunchnow.widget.WidgetBroadcastReceiver;
import me.calebjones.spacelaunchnow.widget.wordtimer.util.NumberToWords;
import me.calebjones.spacelaunchnow.utils.Utils;
import timber.log.Timber;


public class UpdateWordTimerJob extends Job {

    public static final String TAG = Constants.ACTION_UPDATE_WORD_TIMER;
    public SwitchPreferences switchPreferences;

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        Context context = getContext();
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        PersistableBundleCompat extras = params.getExtras();
        int appWidgetId = extras.getInt("appWidgetId", 0);
        if (appWidgetId != 0) {
            Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);

            int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            int maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
            int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            int maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);

            if (minWidth == 0 && maxWidth == 0 && minHeight == 0 && maxHeight == 0) {
                AppWidgetHost host = new AppWidgetHost(context, 0);
                host.deleteAppWidgetId(appWidgetId);
            }

            // Update The clock label using a shared method
            updateAppWidget(context, appWidgetManager, appWidgetId, options);
        }
        return Result.SUCCESS;
    }

    public static void runJobImmediately(int id) {
        PersistableBundleCompat extras = new PersistableBundleCompat();
        extras.putInt("appWidgetId", id);
        new JobRequest.Builder(UpdateWordTimerJob.TAG)
                .addExtras(extras)
                .startNow()
                .build()
                .schedule();
    }

    private Launch getLaunch(Context context) {
        Date date = new Date();

        switchPreferences = SwitchPreferences.getInstance(context);

        Realm mRealm = Realm.getDefaultInstance();

        RealmResults<Launch> launchRealms;
        if (switchPreferences.getAllSwitch()) {
            launchRealms = mRealm.where(Launch.class)
                    .greaterThanOrEqualTo("net", date)
                    .findAllSorted("net", Sort.ASCENDING);
            Timber.v("loadLaunches - Realm query created.");
        } else {
            launchRealms = QueryBuilder.buildSwitchQuery(context, mRealm);
            Timber.v("loadLaunches - Filtered Realm query created.");
        }

        for (Launch launch : launchRealms) {
            if (launch.getNetstamp() != null && launch.getNetstamp() != 0) {
                return launch;
            }
        }
        return null;
    }

    public void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle options) {
        Timber.v("UpdateAppWidget");
        RemoteViews remoteViews;
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        int maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);

        Timber.v("Size: [%s-%s] x [%s-%s]", minWidth, maxWidth, minHeight, maxHeight);

        Launch launch = getLaunch(context);

        if (minWidth <= 200 || minHeight <= 100) {
            remoteViews = new RemoteViews(context.getPackageName(),
                    R.layout.widget_launch_word_timer_small_dark
            );
        } else if (minWidth <= 320) {
            remoteViews = new RemoteViews(context.getPackageName(),
                    R.layout.widget_launch_word_timer_dark
            );
        } else {
            remoteViews = new RemoteViews(context.getPackageName(),
                    R.layout.widget_launch_word_timer_large_dark
            );
        }


        if (launch != null) {
            setLaunchName(context, launch, remoteViews, options);
            setMissionName(context, launch, remoteViews, options);
            setRefreshIntent(context, launch, remoteViews);
            setWidgetStyle(context, remoteViews);
            setLaunchTimer(context, launch, remoteViews, appWidgetManager, appWidgetId, options);
        } else {
            remoteViews.setTextViewText(R.id.widget_launch_name, "Unknown Launch");
            remoteViews.setTextViewText(R.id.widget_mission_name, "Unknown Mission");
        }
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews);

    }

    //TODO cannot call grom background thread - gotta figure this out
    private void setRefreshIntent(Context context, Launch launch, RemoteViews remoteViews) {
        Intent nextIntent = new Intent(context, WidgetBroadcastReceiver.class);
        PendingIntent refreshPending = PendingIntent.getBroadcast(context, 0, nextIntent, 0);
        remoteViews.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPending);

        Intent exploreIntent = new Intent(context, LaunchDetailActivity.class);
        exploreIntent.putExtra("TYPE", "launch");
        exploreIntent.putExtra("launchID", launch.getId());
        exploreIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent actionPendingIntent = PendingIntent.getActivity(context, 0, exploreIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        remoteViews.setOnClickPendingIntent(R.id.widget_countdown_timer_frame, actionPendingIntent);
    }

    private void setMissionName(Context context, Launch launchRealm, RemoteViews remoteViews, Bundle options) {
        String missionName = getMissionName(launchRealm);

        if (missionName != null) {
            remoteViews.setTextViewText(R.id.widget_mission_name, missionName);
        } else {
            remoteViews.setTextViewText(R.id.widget_mission_name, "Unknown Mission");
        }
    }

    private void setLaunchTimer(Context context, Launch launchRealm, final RemoteViews remoteViews, final AppWidgetManager appWidgetManager, final int widgetId, final Bundle options) {

        long millisUntilFinished = getFutureMilli(launchRealm) - System.currentTimeMillis();

        // Calculate the Days/Hours/Mins/Seconds numerically.
        long longDays = millisUntilFinished / 86400000;
        long longHours = (millisUntilFinished / 3600000) % 24;

        NumberToWords.DefaultProcessor processor = new NumberToWords.DefaultProcessor();

        // Update the views
        String days = processor.getName(longDays);
        remoteViews.setTextViewText(R.id.countdown_days, String.valueOf(longDays));

        String hours = processor.getName(longHours);
        remoteViews.setTextViewText(R.id.countdown_hours, String.valueOf(longHours));

    }



    private void setWidgetStyle(Context context, RemoteViews remoteViews) {
    }

    private void setLaunchName(Context context, Launch launchRealm, RemoteViews remoteViews, Bundle options) {
        String launchName = getLaunchName(launchRealm);

        if (launchName != null) {
            remoteViews.setTextViewText(R.id.widget_launch_name, launchName);
        } else {
            remoteViews.setTextViewText(R.id.widget_launch_name, "Unknown Launch");
        }
    }

    private void setLaunchDate(Context context, Launch launch, RemoteViews remoteViews) {
        SimpleDateFormat sdf;
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("24_hour_mode", false)) {
            sdf = new SimpleDateFormat("MMMM dd, yyyy");
        } else {
            sdf = new SimpleDateFormat("MMMM dd, yyyy");
        }
        sdf.toLocalizedPattern();
        if (launch.getNet() != null) {
            remoteViews.setTextViewText(R.id.widget_launch_date, sdf.format(launch.getNet()));
        } else {
            remoteViews.setTextViewText(R.id.widget_launch_date, "Unknown Launch Date");
        }
    }

    private String getLaunchName(Launch launchRealm) {
        //Replace with launch
        if (launchRealm.getRocket() != null && launchRealm.getRocket().getName() != null) {
            //Replace with mission name
            return launchRealm.getRocket().getName();
        } else {
            return null;
        }
    }

    private String getMissionName(Launch launchRealm) {

        if (launchRealm.getMissions().size() > 0) {
            //Replace with mission name
            return launchRealm.getMissions().get(0).getName();
        } else {
            return null;
        }
    }

    private long getFutureMilli(Launch launchRealm) {
        return getLaunchDate(launchRealm).getTimeInMillis();
    }

    private Calendar getLaunchDate(Launch launchRealm) {

        //Replace with launchData
        long longdate = launchRealm.getNetstamp();
        longdate = longdate * 1000;
        final Date date = new Date(longdate);
        return Utils.DateToCalendar(date);
    }
}
