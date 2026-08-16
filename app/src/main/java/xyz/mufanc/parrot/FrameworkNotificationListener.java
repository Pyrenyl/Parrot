package xyz.mufanc.parrot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationRule;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.notification.INotificationListener;
import android.service.notification.IDispatchCompletionListener;
import android.service.notification.IStatusBarNotificationHolder;
import android.service.notification.Adjustment;
import android.service.notification.NotificationRankingUpdate;
import android.service.notification.NotificationStats;
import android.service.notification.StatusBarNotification;

import java.util.List;

final class FrameworkNotificationListener extends INotificationListener.Stub {
    interface Callback {
        void onConnected();
        void onPosted(StatusBarNotification notification);
        void onRemoved(StatusBarNotification notification);
    }

    private final Callback callback;
    private IDispatchCompletionListener completionListener;

    FrameworkNotificationListener(Callback callback) {
        this.callback = callback;
    }

    @Override public void onListenerConnected(NotificationRankingUpdate update) { callback.onConnected(); }
    @Override public void onNotificationPosted(IStatusBarNotificationHolder holder, NotificationRankingUpdate update) throws android.os.RemoteException { callback.onPosted(holder.get()); }
    @Override public void onNotificationPostedFull(StatusBarNotification sbn, NotificationRankingUpdate update) { callback.onPosted(sbn); }
    @Override public void onNotificationRemoved(IStatusBarNotificationHolder holder, NotificationRankingUpdate update, NotificationStats stats, int reason) throws android.os.RemoteException { callback.onRemoved(holder.get()); }
    @Override public void onNotificationRemovedFull(StatusBarNotification sbn, NotificationRankingUpdate update, NotificationStats stats, int reason) { callback.onRemoved(sbn); }

    @Override public void onStatusBarIconsBehaviorChanged(boolean hideSilentStatusIcons) {}
    @Override public void onNotificationRankingUpdate(NotificationRankingUpdate update) {}
    @Override public void onListenerHintsChanged(int hints) {}
    @Override public void onInterruptionFilterChanged(int interruptionFilter) {}
    @Override public void onNotificationChannelModification(String pkgName, UserHandle user, NotificationChannel channel, int modificationType) {}
    @Override public void onNotificationChannelGroupModification(String pkgName, UserHandle user, NotificationChannelGroup group, int modificationType) {}
    @Override public void onNotificationEnqueuedWithChannel(IStatusBarNotificationHolder holder, NotificationChannel channel, NotificationRankingUpdate update) {}
    @Override public void onNotificationEnqueuedWithChannelFull(StatusBarNotification sbn, NotificationChannel channel, NotificationRankingUpdate update) {}
    @Override public void onNotificationSnoozedUntilContext(IStatusBarNotificationHolder holder, String snoozeCriterionId) {}
    @Override public void onNotificationSnoozedUntilContextFull(StatusBarNotification sbn, String snoozeCriterionId) {}
    @Override public void onNotificationsSeen(List<String> keys) {}
    @Override public void onPanelRevealed(int items) {}
    @Override public void onPanelHidden() {}
    @Override public void onNotificationVisibilityChanged(String key, boolean isVisible) {}
    @Override public void onNotificationExpansionChanged(String key, boolean userAction, boolean expanded) {}
    @Override public void onNotificationDirectReply(String key) {}
    @Override public void onSuggestedReplySent(String key, CharSequence reply, int source) {}
    @Override public void onActionClicked(String key, Notification.Action action, int source) {}
    @Override public void onNotificationClicked(String key) {}
    @Override public void onAllowedAdjustmentsChanged() {}
    @Override public void onNotificationFeedbackReceived(String key, NotificationRankingUpdate update, Bundle feedback) {}

    @Override public void onListenerConnected(NotificationRankingUpdate update, IDispatchCompletionListener completionListener, long dispatchToken) {
        this.completionListener = completionListener;
        callback.onConnected();
        complete(dispatchToken);
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn, NotificationRankingUpdate update, long dispatchToken) {
        callback.onPosted(sbn);
        complete(dispatchToken);
    }
    @Override public void onStatusBarIconsBehaviorChanged(boolean hideSilentStatusIcons, long dispatchToken) { complete(dispatchToken); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn, NotificationRankingUpdate update, NotificationStats stats, int reason, long dispatchToken) {
        callback.onRemoved(sbn);
        complete(dispatchToken);
    }
    @Override public void onNotificationRankingUpdate(NotificationRankingUpdate update, long dispatchToken) { complete(dispatchToken); }
    @Override public void onListenerHintsChanged(int hints, long dispatchToken) { complete(dispatchToken); }
    @Override public void onInterruptionFilterChanged(int interruptionFilter, long dispatchToken) { complete(dispatchToken); }
    @Override public void onNotificationChannelModification(String pkgName, UserHandle user, NotificationChannel channel, int modificationType, long dispatchToken) { complete(dispatchToken); }
    @Override public void onNotificationChannelGroupModification(String pkgName, UserHandle user, NotificationChannelGroup group, int modificationType, long dispatchToken) { complete(dispatchToken); }
    @Override public void onNotificationEnqueuedWithChannel(StatusBarNotification sbn, NotificationChannel channel, NotificationRankingUpdate update) {}
    @Override public void onNotificationSnoozedUntilContext(StatusBarNotification sbn, String snoozeCriterionId) {}
    @Override public void onSystemAdjustmentsReceived(List<Adjustment> adjustments) {}
    @Override public void onNotificationRuleAdded(NotificationRule rule) {}
    @Override public void onNotificationRuleModified(NotificationRule rule) {}
    @Override public void onNotificationRuleRemoved(int ruleId) {}

    private void complete(long dispatchToken) {
        if (completionListener == null) return;
        try {
            completionListener.notifyDispatchComplete(dispatchToken);
        } catch (android.os.RemoteException ignored) {
        }
    }
}
