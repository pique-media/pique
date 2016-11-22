package controllers;

import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.oauth.OAuthException;
import org.joda.time.DateTime;
import play.Logger;
import play.inject.ApplicationLifecycle;
import services.ThreadNotification;
import services.content.DataCollectionRunner;
import services.content.JavaDataCollector;
import services.dataAccess.AbstractDataAccess;
import services.sorting.SortingNode;
import services.sources.TwitterSource;
import services.sources.RedditSource;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ContentController {

    private Thread sorter;
    private List<Thread> collectors;
    private ThreadNotification sortNotification;

    @Inject
    public ContentController(AbstractDataAccess access, ApplicationLifecycle appLifecycle) {
        Logger.info("ContentController: Starting application at " + DateTime.now().toString());
        sortNotification = new ThreadNotification();


        sorter = new Thread(new SortingNode(access, sortNotification));
        sorter.start();

        collectors = new ArrayList<>();
        Thread twitter = new Thread(new DataCollectionRunner(new JavaDataCollector(access, new
                TwitterSource()), sortNotification)); // TODO THIS IS AWFUL
        twitter.start();
        collectors.add(twitter);
        Thread reddit = new Thread(new DataCollectionRunner(new JavaDataCollector(access, new
                RedditSource()), sortNotification));
        reddit.start();
        collectors.add(reddit);


        // When the application starts, register a stop hook with the
        // ApplicationLifecycle object. The code inside the stop hook will
        // be run when the application stops.
        appLifecycle.addStopHook(() -> {

            sorter.interrupt();
            collectors.stream().forEach(Thread::interrupt);
            return CompletableFuture.completedFuture(null);
        });
    }
}
