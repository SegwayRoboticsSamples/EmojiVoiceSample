package com.segway.robot.EmojiVoiceSample;

import android.content.Context;
import android.util.Log;

import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.emoji.BaseControlHandler;
import com.segway.robot.sdk.locomotion.sbv.Base;

/**
 * Created by laoxinqiang on 2017/4/19.
 */

public class BaseControlManager implements BaseControlHandler {
    private static final String TAG = "BaseControlManager";

    private Base mBase;
    private boolean mIsBindSuccess = false;

    public BaseControlManager(Context context) {
        Log.d(TAG, "BaseControlHandler() called");
        mBase = Base.getInstance();
        mBase.bindService(context.getApplicationContext(), mBindStateListener);
    }

    private ServiceBinder.BindStateListener mBindStateListener = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            Log.d(TAG, "onBind() called");
            mIsBindSuccess = true;
        }

        @Override
        public void onUnbind(String reason) {
            Log.d(TAG, "onUnbind() called with: reason = [" + reason + "]");
            mIsBindSuccess = false;
        }
    };

    @Override
    public void setLinearVelocity(float velocity) {
        if (mIsBindSuccess) {
            mBase.setLinearVelocity(velocity);
        }

    }

    @Override
    public void setAngularVelocity(float velocity) {
        if (mIsBindSuccess) {
            mBase.setAngularVelocity(velocity);
        }
    }

    @Override
    public void stop() {
        if (mIsBindSuccess) {
            mBase.stop();
        }
    }

    @Override
    public Ticks getTicks() {
        return null;
    }
}
