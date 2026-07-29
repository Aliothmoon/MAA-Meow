package com.aliothmoon.maameow.root;

import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

import com.aliothmoon.maameow.RemoteService;
import com.aliothmoon.maameow.third.Ln;

public final class RootServiceStarter {

    private static final String TAG = "RootServiceStarter";
    private static final int DESTROY_TRANSACTION_CODE = 16777115;

    // linkToDeath 随 BinderProxy 被 GC 而失效，须持强引用保证死亡通知可送达
    private static IBinder appLifecycleBinder;
    private static IBinder.DeathRecipient appDeathRecipient;

    private RootServiceStarter() {
    }

    public static void main(String[] args) {
        System.err.println("[RootServiceStarter] main() entry");
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }

        RootUserService.CreatedService createdService = RootUserService.create(args);
        if (createdService == null) {
            System.err.println("[RootServiceStarter] RootUserService.create() returned null");
            System.exit(1);
            return;
        }
        System.err.println("[RootServiceStarter] RootUserService.create() ok, token=" + createdService.token());

        if (!sendBinder(createdService)) {
            System.err.println("[RootServiceStarter] sendBinder() failed");
            System.exit(1);
            return;
        }
        System.err.println("[RootServiceStarter] sendBinder() ok, entering Looper");

        Looper.loop();
        System.exit(0);
    }

    private static boolean sendBinder(RootUserService.CreatedService createdService) {
        RootServiceBootstrapClient.BootstrapResult result = RootServiceBootstrapClient.attachRemoteService(
                createdService.packageName(),
                createdService.userId(),
                createdService.token(),
                createdService.service()
        );
        if (result == null) {
            return false;
        }

        primeHeartbeat(createdService.service(), result.appPid());

        try {
            IBinder.DeathRecipient recipient = () -> {
                Ln.i(TAG + ": app process died, destroying root service");
                destroyService(createdService.service());
                Ln.i(TAG + ": root service destroy signal sent, exiting");
                System.exit(0);
            };
            IBinder lifecycleBinder = result.lifecycleBinder();
            lifecycleBinder.linkToDeath(recipient, 0);
            appLifecycleBinder = lifecycleBinder;
            appDeathRecipient = recipient;
            return true;
        } catch (Throwable tr) {
            Ln.e(TAG + ": failed to link app lifecycle binder", tr);
            return false;
        }
    }

    /**
     * 提前武装 RemoteServiceImpl 的 /proc 看门狗；
     * 本 Starter 也用于启动 logcat 服务，非 RemoteService 的本地 binder 跳过
     */
    private static void primeHeartbeat(IBinder service, int appPid) {
        if (appPid <= 0) {
            return;
        }
        try {
            if (service.queryLocalInterface(RemoteService.class.getName()) instanceof RemoteService remote) {
                remote.heartbeat(appPid);
            }
        } catch (Throwable tr) {
            Ln.w(TAG + ": prime heartbeat failed", tr);
        }
    }

    private static void destroyService(IBinder service) {
        if (service == null || !service.pingBinder()) {
            return;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            String descriptor = service.getInterfaceDescriptor();
            if (descriptor != null) {
                data.writeInterfaceToken(descriptor);
            }
            service.transact(DESTROY_TRANSACTION_CODE, data, reply, Binder.FLAG_ONEWAY);
        } catch (Throwable tr) {
            Ln.w(TAG + ": destroy root remote service failed", tr);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
