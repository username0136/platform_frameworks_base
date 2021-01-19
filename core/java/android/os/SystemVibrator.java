/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.media.AudioAttributes;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Vibrator implementation that controls the main system vibrator.
 *
 * @hide
 */
public class SystemVibrator extends Vibrator {
    private static final String TAG = "Vibrator";

    private static final int VIBRATOR_PRESENT_UNKNOWN = 0;
    private static final int VIBRATOR_PRESENT_YES = 1;
    private static final int VIBRATOR_PRESENT_NO = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({VIBRATOR_PRESENT_UNKNOWN, VIBRATOR_PRESENT_YES, VIBRATOR_PRESENT_NO})
    private @interface VibratorPresent {}

    private final IVibratorService mService;
    private final IVibratorManagerService mManagerService;
    private final Object mLock = new Object();
    private final Binder mToken = new Binder();
    private final Context mContext;
    @GuardedBy("mLock")
    private VibratorInfo mVibratorInfo;
    @GuardedBy("mLock")
    @VibratorPresent
    private int mVibratorPresent;

    @GuardedBy("mDelegates")
    private final ArrayMap<OnVibratorStateChangedListener,
            OnVibratorStateChangedListenerDelegate> mDelegates = new ArrayMap<>();

    @UnsupportedAppUsage
    public SystemVibrator() {
        mContext = null;
        mService = IVibratorService.Stub.asInterface(ServiceManager.getService("vibrator"));
        mManagerService = IVibratorManagerService.Stub.asInterface(
                ServiceManager.getService("vibrator_manager"));
    }

    @UnsupportedAppUsage
    public SystemVibrator(Context context) {
        super(context);
        mContext = context;
        mService = IVibratorService.Stub.asInterface(ServiceManager.getService("vibrator"));
        mManagerService = IVibratorManagerService.Stub.asInterface(
                ServiceManager.getService("vibrator_manager"));
    }

    @Override
    public boolean hasVibrator() {
        try {
            synchronized (mLock) {
                if (mVibratorPresent == VIBRATOR_PRESENT_UNKNOWN && mService != null) {
                    mVibratorPresent =
                            mService.hasVibrator() ? VIBRATOR_PRESENT_YES : VIBRATOR_PRESENT_NO;
                }
                return mVibratorPresent == VIBRATOR_PRESENT_YES;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to query vibrator presence", e);
            return false;
        }
    }

    /**
     * Check whether the vibrator is vibrating.
     *
     * @return True if the hardware is vibrating, otherwise false.
     */
    @Override
    public boolean isVibrating() {
        if (mService == null) {
            Log.w(TAG, "Failed to vibrate; no vibrator service.");
            return false;
        }
        try {
            return mService.isVibrating();
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
        return false;
    }

    private class OnVibratorStateChangedListenerDelegate extends
            IVibratorStateListener.Stub {
        private final Executor mExecutor;
        private final OnVibratorStateChangedListener mListener;

        OnVibratorStateChangedListenerDelegate(@NonNull OnVibratorStateChangedListener listener,
                @NonNull Executor executor) {
            mExecutor = executor;
            mListener = listener;
        }

        @Override
        public void onVibrating(boolean isVibrating) {
            mExecutor.execute(() -> mListener.onVibratorStateChanged(isVibrating));
        }
    }

    /**
     * Adds a listener for vibrator state change. If the listener was previously added and not
     * removed, this call will be ignored.
     *
     * @param listener Listener to be added.
     * @param executor The {@link Executor} on which the listener's callbacks will be executed on.
     */
    @Override
    public void addVibratorStateListener(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OnVibratorStateChangedListener listener) {
        Objects.requireNonNull(listener);
        Objects.requireNonNull(executor);
        if (mService == null) {
            Log.w(TAG, "Failed to add vibrate state listener; no vibrator service.");
            return;
        }

        synchronized (mDelegates) {
            // If listener is already registered, reject and return.
            if (mDelegates.containsKey(listener)) {
                Log.w(TAG, "Listener already registered.");
                return;
            }
            try {
                final OnVibratorStateChangedListenerDelegate delegate =
                        new OnVibratorStateChangedListenerDelegate(listener, executor);
                if (!mService.registerVibratorStateListener(delegate)) {
                    Log.w(TAG, "Failed to register vibrate state listener");
                    return;
                }
                mDelegates.put(listener, delegate);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Adds a listener for vibrator state changes. Callbacks will be executed on the main thread.
     * If the listener was previously added and not removed, this call will be ignored.
     *
     * @param listener listener to be added
     */
    @Override
    public void addVibratorStateListener(@NonNull OnVibratorStateChangedListener listener) {
        Objects.requireNonNull(listener);
        if (mContext == null) {
            Log.w(TAG, "Failed to add vibrate state listener; no vibrator context.");
            return;
        }
        addVibratorStateListener(mContext.getMainExecutor(), listener);
    }

    /**
     * Removes the listener for vibrator state changes. If the listener was not previously
     * registered, this call will do nothing.
     *
     * @param listener Listener to be removed.
     */
    @Override
    public void removeVibratorStateListener(@NonNull OnVibratorStateChangedListener listener) {
        Objects.requireNonNull(listener);
        if (mService == null) {
            Log.w(TAG, "Failed to remove vibrate state listener; no vibrator service.");
            return;
        }
        synchronized (mDelegates) {
            // Check if the listener is registered, otherwise will return.
            if (mDelegates.containsKey(listener)) {
                final OnVibratorStateChangedListenerDelegate delegate = mDelegates.get(listener);
                try {
                    if (!mService.unregisterVibratorStateListener(delegate)) {
                        Log.w(TAG, "Failed to unregister vibrate state listener");
                        return;
                    }
                    mDelegates.remove(listener);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        }
    }

    @Override
    public boolean hasAmplitudeControl() {
        if (mService == null) {
            Log.w(TAG, "Failed to check amplitude control; no vibrator service.");
            return false;
        }
        try {
            return mService.hasAmplitudeControl();
        } catch (RemoteException e) {
        }
        return false;
    }

    @Override
    public boolean setAlwaysOnEffect(int uid, String opPkg, int alwaysOnId, VibrationEffect effect,
            AudioAttributes attributes) {
        if (mManagerService == null) {
            Log.w(TAG, "Failed to set always-on effect; no vibrator service.");
            return false;
        }
        try {
            VibrationAttributes atr = new VibrationAttributes.Builder(attributes, effect).build();
            CombinedVibrationEffect combinedEffect = CombinedVibrationEffect.createSynced(effect);
            return mManagerService.setAlwaysOnEffect(uid, opPkg, alwaysOnId, combinedEffect, atr);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to set always-on effect.", e);
        }
        return false;
    }

    @Override
    public void vibrate(int uid, String opPkg, @NonNull VibrationEffect effect,
            String reason, @NonNull VibrationAttributes attributes) {
        if (mService == null) {
            Log.w(TAG, "Failed to vibrate; no vibrator service.");
            return;
        }
        try {
            mService.vibrate(uid, opPkg, effect, attributes, reason, mToken);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to vibrate.", e);
        }
    }

    @Override
    public int[] areEffectsSupported(@VibrationEffect.EffectType int... effectIds) {
        VibratorInfo vibratorInfo = getVibratorInfo();
        int[] supported = new int[effectIds.length];
        for (int i = 0; i < effectIds.length; i++) {
            supported[i] = vibratorInfo == null
                    ? Vibrator.VIBRATION_EFFECT_SUPPORT_UNKNOWN
                    : vibratorInfo.isEffectSupported(effectIds[i]);
        }
        return supported;
    }

    @Override
    public boolean[] arePrimitivesSupported(
            @NonNull @VibrationEffect.Composition.Primitive int... primitiveIds) {
        VibratorInfo vibratorInfo = getVibratorInfo();
        boolean[] supported = new boolean[primitiveIds.length];
        for (int i = 0; i < primitiveIds.length; i++) {
            supported[i] = vibratorInfo == null
                    ? false : vibratorInfo.isPrimitiveSupported(primitiveIds[i]);
        }
        return supported;
    }

    @Override
    public void cancel() {
        if (mService == null) {
            return;
        }
        try {
            mService.cancelVibrate(mToken);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to cancel vibration.", e);
        }
    }

    @Nullable
    private VibratorInfo getVibratorInfo() {
        try {
            synchronized (mLock) {
                if (mVibratorInfo != null) {
                    return mVibratorInfo;
                }
                if (mService == null) {
                    return null;
                }
                return mVibratorInfo = mService.getVibratorInfo();
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to query vibrator info");
            throw e.rethrowFromSystemServer();
        }
    }
}
