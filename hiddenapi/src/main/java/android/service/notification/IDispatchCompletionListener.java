package android.service.notification;

import android.os.IInterface;
import android.os.RemoteException;

public interface IDispatchCompletionListener extends IInterface {
    void notifyDispatchComplete(long dispatchToken) throws RemoteException;
}
