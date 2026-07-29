package com.aliothmoon.maameow.root;

import android.content.IContentProvider;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import com.aliothmoon.maameow.third.Ln;
import com.aliothmoon.maameow.third.wrappers.ServiceManager;

public final class RootServiceBootstrapClient {

    private RootServiceBootstrapClient() {
    }

    /** 握手结果：app 生命周期 binder 与 app 进程 pid */
    public record BootstrapResult(IBinder lifecycleBinder, int appPid) {
    }

    public static BootstrapResult attachRemoteService(String packageName, int userId, String token, IBinder serviceBinder) {
        String authority = packageName + RootServiceBootstrapRegistry.AUTHORITY_SUFFIX;
        IBinder providerToken = new Binder();
        IContentProvider provider = null;

        System.err.println("[BootstrapClient] attachRemoteService authority=" + authority + " userId=" + userId);
        try {
            provider = ServiceManager.getActivityManager()
                    .getContentProviderExternal(authority, userId, providerToken, authority);
            if (provider == null) {
                Ln.e("Root bootstrap provider is null: " + authority + " user=" + userId);
                System.err.println("[BootstrapClient] getContentProviderExternal returned null");
                return null;
            }
            if (!provider.asBinder().pingBinder()) {
                Ln.e("Root bootstrap provider is dead: " + authority + " user=" + userId);
                System.err.println("[BootstrapClient] provider binder is dead");
                return null;
            }
            System.err.println("[BootstrapClient] provider ok, calling METHOD_ATTACH_REMOTE_SERVICE");

            Bundle extras = new Bundle();
            extras.putString(RootServiceBootstrapRegistry.KEY_TOKEN, token);
            extras.putBinder(RootServiceBootstrapRegistry.KEY_SERVICE_BINDER, serviceBinder);

            Bundle reply = RootIContentProviderCompat.call(
                    provider,
                    null,
                    null,
                    authority,
                    RootServiceBootstrapRegistry.METHOD_ATTACH_REMOTE_SERVICE,
                    null,
                    extras
            );
            if (reply == null) {
                Ln.e("Root bootstrap provider returned null");
                System.err.println("[BootstrapClient] provider.call() returned null");
                return null;
            }

            IBinder lifecycleBinder = reply.getBinder(RootServiceBootstrapRegistry.KEY_APP_BINDER);
            if (lifecycleBinder == null || !lifecycleBinder.pingBinder()) {
                Ln.e("Root bootstrap app lifecycle binder missing");
                System.err.println("[BootstrapClient] app lifecycle binder missing or dead");
                return null;
            }
            int appPid = reply.getInt(RootServiceBootstrapRegistry.KEY_APP_PID, 0);
            System.err.println("[BootstrapClient] lifecycle binder received ok, appPid=" + appPid);
            return new BootstrapResult(lifecycleBinder, appPid);
        } catch (Throwable tr) {
            Ln.e("Failed to send binder back to app", tr);
            System.err.println("[BootstrapClient] exception: " + tr);
            tr.printStackTrace(System.err);
            return null;
        } finally {
            if (provider != null) {
                ServiceManager.getActivityManager().removeContentProviderExternal(authority, providerToken);
            }
        }
    }
}
