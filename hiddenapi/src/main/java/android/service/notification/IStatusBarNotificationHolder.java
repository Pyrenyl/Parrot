package android.service.notification;

import android.os.IInterface;
import android.os.RemoteException;

public interface IStatusBarNotificationHolder extends IInterface {
    StatusBarNotification get() throws RemoteException;
}
