package me.calebjones.spacelaunchnow.news.data;

import android.content.Context;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import androidx.annotation.UiThread;
import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import io.realm.Sort;
import me.calebjones.spacelaunchnow.data.models.main.news.NewsItem;
import timber.log.Timber;

/**
 * Responsible for retrieving data from the Realm cache.
 */

public class NewsDataRepository {

    private NewsDataLoader dataLoader;
    private Realm realm;
    private final Context context;
    private boolean moreDataAvailable;

    public NewsDataRepository(Context context, Realm realm) {
        this.context = context;
        this.dataLoader = new NewsDataLoader(context);
        this.realm = realm;
    }

    @UiThread
    public void getNews(int limit, boolean forceUpdate, Callbacks.NewsListCallback callback) {

        final Date now = Calendar.getInstance().getTime();

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.DAY_OF_WEEK, -1);
        Date old = calendar.getTime();

        final RealmResults<NewsItem> newsList = getNewsFromRealm();
        Timber.v("Current count in DB: %s", newsList.size());
        for (NewsItem news : newsList) {
            if (news.getLastUpdate() != null && news.getLastUpdate().before(old)) {
                forceUpdate = true;
            }
        }

        Timber.v("Limit: %s ", limit);
        if (forceUpdate || newsList.size() == 0 ) {
            if (forceUpdate) {
                //delete cache first
                realm.executeTransaction(realm -> newsList.deleteAllFromRealm());
            }
            Timber.v("Getting from network!");
            getNewsFromNetwork(limit, callback);
        } else {
            callback.onNewsLoaded(newsList);
        }
    }

    //TODO fix query
    public RealmResults<NewsItem> getNewsFromRealm() {
        RealmQuery<NewsItem> query = realm.where(NewsItem.class).isNotNull("id");
        return query.sort("datePublished", Sort.DESCENDING).findAll();
    }

    public NewsItem getNewsByIdFromRealm(String id) {
        return realm.where(NewsItem.class).equalTo("id", id).findFirst();
    }


    private void getNewsFromNetwork(int limit, final Callbacks.NewsListCallback callback) {

        callback.onNetworkStateChanged(true);
        dataLoader.getNewsList(limit, new Callbacks.NewsListNetworkCallback() {
            @Override
            public void onSuccess(List<NewsItem> news) {
                addNewsToRealm(news);
                callback.onNetworkStateChanged(false);
                callback.onNewsLoaded(getNewsFromRealm());
            }

            @Override
            public void onNetworkFailure(int code) {
                callback.onNetworkStateChanged(false);
                callback.onError("Unable to load launch data.", null);
            }

            @Override
            public void onFailure(Throwable throwable) {
                callback.onNetworkStateChanged(false);
                callback.onError("An error has occurred! Uh oh.", throwable);
            }
        });
    }

    @UiThread
    public void getNewsById(String id, Callbacks.NewsCallback callback) {
        callback.onNewsLoaded(getNewsByIdFromRealm(id));
        getNewsByIdFromNetwork(id, callback);
    }

    private void getNewsByIdFromNetwork(String id, final Callbacks.NewsCallback callback) {

        callback.onNetworkStateChanged(true);
        dataLoader.getNewsById(id, new Callbacks.NewsNetworkCallback() {
            @Override
            public void onSuccess(NewsItem event) {

                callback.onNetworkStateChanged(false);
                callback.onNewsLoaded(event);
            }

            @Override
            public void onNetworkFailure(int code) {
                callback.onNetworkStateChanged(false);
                callback.onError("Unable to load launch data.", null);
            }

            @Override
            public void onFailure(Throwable throwable) {
                callback.onNetworkStateChanged(false);
                callback.onError("An error has occurred! Uh oh.", throwable);
            }
        });
    }

    public void addNewsToRealm(final List<NewsItem> news) {
        realm.executeTransaction(realm -> realm.copyToRealmOrUpdate(news));
    }
}


