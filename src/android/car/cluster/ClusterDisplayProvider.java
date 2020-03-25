/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.car.cluster;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;

import com.android.internal.util.Preconditions;

import java.util.List;

/**
 * This class provides a display for instrument cluster renderer.
 * <p>
 * By default it will try to provide physical secondary display if it is connected, if secondary
 * display is not connected during creation of this class then it will start networked virtual
 * display and listens for incoming connections.
 *
 * @see {@link NetworkedVirtualDisplay}
 */
public class ClusterDisplayProvider {
    private static final String TAG = "Cluster.DisplayProvider";

    private static final int NETWORKED_DISPLAY_WIDTH = 1280;
    private static final int NETWORKED_DISPLAY_HEIGHT = 720;
    private static final int NETWORKED_DISPLAY_DPI = 320;

    private final DisplayListener mListener;
    private final DisplayManager mDisplayManager;

    private NetworkedVirtualDisplay mNetworkedVirtualDisplay;
    private int mClusterDisplayId = -1;

    ClusterDisplayProvider(Context context, DisplayListener clusterDisplayListener) {
        mListener = clusterDisplayListener;
        mDisplayManager = context.getSystemService(DisplayManager.class);
        Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    if (!ready) return;
                    initClusterDisplayProvider(context, (CarOccupantZoneManager) car.getCarManager(
                            Car.CAR_OCCUPANT_ZONE_SERVICE));
                });
    }

    private void initClusterDisplayProvider(
            Context context, CarOccupantZoneManager occupantZoneManager) {
        Preconditions.checkArgument(
                occupantZoneManager != null,"Can't get CarOccupantZoneManager");
        OccupantZoneInfo driverZone = getOccupantZoneForDriver(occupantZoneManager);
        Display clusterDisplay = occupantZoneManager.getDisplayForOccupant(
                driverZone, CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);
        if (clusterDisplay != null) {
            Log.i(TAG, String.format("Found display: %s (id: %d, owner: %s)",
                    clusterDisplay.getName(), clusterDisplay.getDisplayId(),
                    clusterDisplay.getOwnerPackageName()));
            mClusterDisplayId = clusterDisplay.getDisplayId();
            mListener.onDisplayAdded(clusterDisplay.getDisplayId());
            trackClusterDisplay(null /* no need to track display by name */);
        } else {
            Log.i(TAG, "No physical cluster display found, starting network display");
            setupNetworkDisplay(context);
        }
    }

    private static @NonNull OccupantZoneInfo getOccupantZoneForDriver(
            @NonNull CarOccupantZoneManager occupantZoneManager) {
        List<OccupantZoneInfo> zones = occupantZoneManager.getAllOccupantZones();
        int zones_size = zones.size();
        for (int i = 0; i < zones_size; ++i) {
            OccupantZoneInfo zone = zones.get(i);
            // Assumes that a Car has only one driver.
            if (zone.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                return zone;
            }
        }
        throw new IllegalStateException("Can't find the OccupantZoneInfo for driver");
    }

    private void setupNetworkDisplay(Context context) {
        mNetworkedVirtualDisplay = new NetworkedVirtualDisplay(context,
                NETWORKED_DISPLAY_WIDTH, NETWORKED_DISPLAY_HEIGHT, NETWORKED_DISPLAY_DPI);
        String displayName = mNetworkedVirtualDisplay.start();
        trackClusterDisplay(displayName);
    }

    private void trackClusterDisplay(@Nullable String displayName) {
        mDisplayManager.registerDisplayListener(new DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                boolean clusterDisplayAdded = false;

                if (displayName == null && mClusterDisplayId == -1) {
                    mClusterDisplayId = displayId;
                    clusterDisplayAdded = true;
                } else {
                    Display display = mDisplayManager.getDisplay(displayId);
                    if (display != null && TextUtils.equals(display.getName(), displayName)) {
                        mClusterDisplayId = displayId;
                        clusterDisplayAdded = true;
                    }
                }

                if (clusterDisplayAdded) {
                    mListener.onDisplayAdded(displayId);
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                if (displayId == mClusterDisplayId) {
                    mClusterDisplayId = -1;
                    mListener.onDisplayRemoved(displayId);
                }
            }

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId == mClusterDisplayId) {
                    mListener.onDisplayChanged(displayId);
                }
            }

        }, null);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{"
                + " clusterDisplayId = " + mClusterDisplayId
                + "}";
    }
}
