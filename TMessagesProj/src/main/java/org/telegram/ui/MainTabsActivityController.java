package org.telegram.ui;

import android.view.View;

public interface MainTabsActivityController {
    void setTabsVisible(boolean visible);

    boolean openAccountSelector(View button, View touchRelayView);
}
