package com.ismet.usbterminal.updated.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.ismet.usbterminal.updated.EToCApplication;
import com.ismet.usbterminal.updated.data.PullState;
import com.ismet.usbterminal.updated.mainscreen.EToCMainActivity;
import com.ismet.usbterminal.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PullStateManagingService extends Service {

    public static final String IS_AUTO_PULL_ON = "is_auto_pull_on";
    public static final String IS_RECREATING = "is_recreating";

    private static final String CO2_REQUEST = "(FE-44-00-08-02-9F-25)";
    private static final int MAX_WAIT_TIME_FOR_CANCEL_EXECUTOR = 100;
    public static final int DELAY_ON_CHANGE_REQUEST = 1000;

    private ScheduledExecutorService mPullDataService;
    private EToCApplication eToCApplication;

    private AtomicBoolean mIsAutoHandling = new AtomicBoolean(true);

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        eToCApplication = EToCApplication.getInstance();

        mPullDataService = eToCApplication.getPullDataService();
    }

    public static Intent intentForService(Context context, boolean isAutoPull) {
        EToCApplication application = EToCApplication.getInstance();
        Intent intent = new Intent(application, PullStateManagingService.class);
        intent.putExtra(IS_AUTO_PULL_ON, isAutoPull);
        if (isAutoPull) {
            EToCApplication.getInstance().setStopPulling(false);
        }
        return intent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getExtras();
        if (extras != null && !eToCApplication.isPullingStopped()) {
            boolean isPull = extras.getBoolean(IS_AUTO_PULL_ON, true);
            if (isPull) {
                mIsAutoHandling.set(true);
                if (eToCApplication.getPullState() == PullState.NONE) {
                    eToCApplication.setPullState(PullState.TEMPERATURE);
                }

                if (mPullDataService == null) {
                    mPullDataService = Executors.newSingleThreadScheduledExecutor();
                    eToCApplication.initPullDataService(mPullDataService);
                    eToCApplication.refreshTimeOfRecreating();
                    eToCApplication.reNewTimeOfRecreating();
                } else {
                    eToCApplication.unScheduleTasks();
                    boolean isRenew = eToCApplication.reNewTimeOfRecreating();
                    if (isRenew) {
                        mPullDataService = Executors.newSingleThreadScheduledExecutor();
                        eToCApplication.clearPullDataService();
                        eToCApplication.initPullDataService(mPullDataService);
                    }
                }

                mPullDataService.schedule(new Runnable() {

                    @Override
                    public void run() {
                        if (!eToCApplication.isPullingStopped() && Utils.elapsedTimeForCacheFill
                                (System
                                .currentTimeMillis(), eToCApplication.getRenewTime())) {
                            startService(intentForService(PullStateManagingService.this, true));
                        }
                    }
                }, 1, TimeUnit.MINUTES);

                List<ScheduledFuture> scheduledFutures = new ArrayList<>(1);

                Runnable autoChangeRunnable = new Runnable() {
                    @Override
                    public void run() {
                        int pullState = eToCApplication.getPullState();

                        if (pullState == PullState.NONE || !mIsAutoHandling.get()) {
                            return;
                        }

                        switch (pullState) {
                            case PullState.TEMPERATURE:
                                pullState = PullState.CO2;
                                break;
                            case PullState.CO2:
                                pullState = PullState.TEMPERATURE;
                                break;
                        }

                        eToCApplication.setPullState(pullState);

                        boolean isTemperature = pullState == PullState.TEMPERATURE;
                        if (isTemperature) {
                            initAutoPullTemperatureRunnable().run();
                        } else {
                            initAutoPullCo2Runnable().run();
                        }
                    }
                };

                scheduledFutures.add(mPullDataService.scheduleWithFixedDelay(autoChangeRunnable,
                        0, 1, TimeUnit.SECONDS));

                eToCApplication.setScheduledFutures(scheduledFutures);
            } else {
                mIsAutoHandling.set(false);
                eToCApplication.unScheduleTasks();
                eToCApplication.setStopPulling(true);
            }
        }
        return START_STICKY;
    }

    private Runnable initAutoPullCo2Runnable() {
        return new Runnable() {
            @Override
            public void run() {
                if (eToCApplication.getPullState() == PullState.NONE) {
                    return;
                }
                EToCMainActivity.sendBroadCastWithData(PullStateManagingService.this,
                        CO2_REQUEST);
            }
        };
    }

    public Runnable initAutoPullTemperatureRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                if (eToCApplication.getPullState() == PullState.NONE) {
                    return;
                }
                EToCMainActivity.sendBroadCastWithData(PullStateManagingService.this,
                        eToCApplication.getCurrentTemperatureRequest());
            }
        };
    }
}
