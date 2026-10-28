package com.aliothmoon.maameow.root;

import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;

import com.aliothmoon.maameow.third.Ln;

import java.io.IOException;
import java.io.InputStream;

/** Shizuku / Root 共用的提权服务入口 */
public final class RemoteServiceStarter {

    private static final String TAG = "RemoteServiceStarter";
    private static final int DESTROY_TRANSACTION_CODE = 16777115;

    // linkToDeath 随 BinderProxy 被 GC 而失效，须持强引用保证死亡通知可送达
    private static IBinder appLifecycleBinder;
    private static IBinder.DeathRecipient appDeathRecipient;

    private RemoteServiceStarter() {
    }

    // app_process 进程没有系统建好的主 Looper，只能自己建
    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        System.err.println("[" + TAG + "] main() entry");
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }

        RootUserService.CreatedService createdService = RootUserService.create(args);
        if (createdService == null) {
            System.err.println("[" + TAG + "] service creation returned null");
            System.exit(1);
            return;
        }
        System.err.println("[" + TAG + "] service created, token=" + createdService.token());

        if (!sendBinder(createdService)) {
            System.err.println("[" + TAG + "] sendBinder() failed");
            System.exit(1);
            return;
        }
        System.err.println("[" + TAG + "] sendBinder() ok, entering Looper");

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

        IBinder service = createdService.service();
        try {
            IBinder.DeathRecipient recipient = () -> onAppGone("app binder died", service);
            IBinder lifecycleBinder = result.lifecycleBinder();
            lifecycleBinder.linkToDeath(recipient, 0);
            appLifecycleBinder = lifecycleBinder;
            appDeathRecipient = recipient;
        } catch (Throwable tr) {
            Ln.e(TAG + ": failed to link app lifecycle binder", tr);
            return false;
        }
        watchLifeline(result.lifeline(), service);
        return true;
    }

    /** 两路死讯先到先走：binder 通知要等线程池，被卡住的调用占满时送不到，lifeline 独占线程不受影响 */
    private static void onAppGone(String reason, IBinder service) {
        ProcessExit.exit(reason, () -> destroyService(service));
    }

    private static void watchLifeline(ParcelFileDescriptor lifeline, IBinder service) {
        if (lifeline == null) {
            Ln.w(TAG + ": no lifeline, relying on binder death only");
            return;
        }
        Thread watcher = new Thread(() -> {
            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(lifeline)) {
                // App 从不写，阻塞到它退出、内核关掉写端时的 EOF
                while (in.read() >= 0) {
                }
            } catch (IOException e) {
                Ln.w(TAG + ": lifeline read failed, relying on binder death only", e);
                return;
            }
            onAppGone("app lifeline closed", service);
        }, "app-lifeline");
        watcher.setDaemon(true);
        watcher.start();
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
            Ln.w(TAG + ": destroy remote service failed", tr);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
