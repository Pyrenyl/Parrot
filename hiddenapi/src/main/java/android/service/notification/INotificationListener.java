package android.service.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationRule;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.os.UserHandle;

import java.util.List;

public interface INotificationListener extends IInterface {
    void onListenerConnected(NotificationRankingUpdate update) throws RemoteException;
    void onNotificationPosted(IStatusBarNotificationHolder holder, NotificationRankingUpdate update) throws RemoteException;
    void onNotificationPostedFull(StatusBarNotification sbn, NotificationRankingUpdate update) throws RemoteException;
    void onStatusBarIconsBehaviorChanged(boolean hideSilentStatusIcons) throws RemoteException;
    void onNotificationRemoved(IStatusBarNotificationHolder holder, NotificationRankingUpdate update, NotificationStats stats, int reason) throws RemoteException;
    void onNotificationRemovedFull(StatusBarNotification sbn, NotificationRankingUpdate update, NotificationStats stats, int reason) throws RemoteException;
    void onNotificationRankingUpdate(NotificationRankingUpdate update) throws RemoteException;
    void onListenerHintsChanged(int hints) throws RemoteException;
    void onInterruptionFilterChanged(int interruptionFilter) throws RemoteException;
    void onNotificationChannelModification(String pkgName, UserHandle user, NotificationChannel channel, int modificationType) throws RemoteException;
    void onNotificationChannelGroupModification(String pkgName, UserHandle user, NotificationChannelGroup group, int modificationType) throws RemoteException;
    void onNotificationEnqueuedWithChannel(IStatusBarNotificationHolder holder, NotificationChannel channel, NotificationRankingUpdate update) throws RemoteException;
    void onNotificationEnqueuedWithChannelFull(StatusBarNotification sbn, NotificationChannel channel, NotificationRankingUpdate update) throws RemoteException;
    void onNotificationSnoozedUntilContext(IStatusBarNotificationHolder holder, String snoozeCriterionId) throws RemoteException;
    void onNotificationSnoozedUntilContextFull(StatusBarNotification sbn, String snoozeCriterionId) throws RemoteException;
    void onNotificationsSeen(List<String> keys) throws RemoteException;
    void onPanelRevealed(int items) throws RemoteException;
    void onPanelHidden() throws RemoteException;
    void onNotificationVisibilityChanged(String key, boolean isVisible) throws RemoteException;
    void onNotificationExpansionChanged(String key, boolean userAction, boolean expanded) throws RemoteException;
    void onNotificationDirectReply(String key) throws RemoteException;
    void onSuggestedReplySent(String key, CharSequence reply, int source) throws RemoteException;
    void onActionClicked(String key, Notification.Action action, int source) throws RemoteException;
    void onNotificationClicked(String key) throws RemoteException;
    void onAllowedAdjustmentsChanged() throws RemoteException;
    void onNotificationFeedbackReceived(String key, NotificationRankingUpdate update, Bundle feedback) throws RemoteException;

    void onListenerConnected(NotificationRankingUpdate update, IDispatchCompletionListener completionListener, long dispatchToken) throws RemoteException;
    void onNotificationPosted(StatusBarNotification sbn, NotificationRankingUpdate update, long dispatchToken) throws RemoteException;
    void onStatusBarIconsBehaviorChanged(boolean hideSilentStatusIcons, long dispatchToken) throws RemoteException;
    void onNotificationRemoved(StatusBarNotification sbn, NotificationRankingUpdate update, NotificationStats stats, int reason, long dispatchToken) throws RemoteException;
    void onNotificationRankingUpdate(NotificationRankingUpdate update, long dispatchToken) throws RemoteException;
    void onListenerHintsChanged(int hints, long dispatchToken) throws RemoteException;
    void onInterruptionFilterChanged(int interruptionFilter, long dispatchToken) throws RemoteException;
    void onNotificationChannelModification(String pkgName, UserHandle user, NotificationChannel channel, int modificationType, long dispatchToken) throws RemoteException;
    void onNotificationChannelGroupModification(String pkgName, UserHandle user, NotificationChannelGroup group, int modificationType, long dispatchToken) throws RemoteException;
    void onNotificationEnqueuedWithChannel(StatusBarNotification sbn, NotificationChannel channel, NotificationRankingUpdate update) throws RemoteException;
    void onNotificationSnoozedUntilContext(StatusBarNotification sbn, String snoozeCriterionId) throws RemoteException;
    void onSystemAdjustmentsReceived(List<Adjustment> adjustments) throws RemoteException;
    void onNotificationRuleAdded(NotificationRule rule) throws RemoteException;
    void onNotificationRuleModified(NotificationRule rule) throws RemoteException;
    void onNotificationRuleRemoved(int ruleId) throws RemoteException;

    abstract class Stub extends Binder implements INotificationListener {
        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
