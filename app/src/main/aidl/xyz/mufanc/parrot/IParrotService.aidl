package xyz.mufanc.parrot;

import android.app.PendingIntent;
import android.os.Bundle;

interface IParrotService {
    Bundle getState() = 1;
    void selectUser(int userId) = 2;
    void startListening() = 3;
    void stopListening() = 4;
    void setNotificationSink(in PendingIntent sink) = 5;
    void destroy() = 16777114;
}
