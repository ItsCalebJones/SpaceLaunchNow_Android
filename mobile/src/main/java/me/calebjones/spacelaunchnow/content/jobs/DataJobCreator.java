package me.calebjones.spacelaunchnow.content.jobs;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;

import java.util.Date;
import java.util.Set;

import me.calebjones.spacelaunchnow.utils.Utils;
import timber.log.Timber;

public class DataJobCreator implements JobCreator {

    @Override
    public Job create(String tag) {
        switch (tag) {
            case UpdateJob.TAG:
                Timber.v(UpdateJob.TAG);
                return new UpdateJob();
            case UpdateWearJob.TAG:
                Timber.v(UpdateWearJob.TAG);
                return new UpdateWearJob();
            case SyncJob.TAG:
                Timber.v(SyncJob.TAG);
                return new SyncJob();
            case UpdateLaunchCardJob.TAG:
                Timber.v(UpdateLaunchCardJob.TAG);
                return new UpdateLaunchCardJob();
            case UpdateWordTimerJob.TAG:
                Timber.v(UpdateWordTimerJob.TAG);
                return new UpdateWordTimerJob();
            case SyncWidgetJob.TAG:
                Timber.v(SyncWidgetJob.TAG);
                return new SyncWidgetJob();
            default:
                return null;
        }
    }


}
