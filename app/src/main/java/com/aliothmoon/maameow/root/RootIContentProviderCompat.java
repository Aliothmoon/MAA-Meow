package com.aliothmoon.maameow.root;

import android.content.AttributionSource;
import android.content.IContentProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.system.Os;

public final class RootIContentProviderCompat {

    private static final String SHELL_PACKAGE = "com.android.shell";

    private RootIContentProviderCompat() {
    }

    public static Bundle call(
            IContentProvider provider,
            String attributeTag,
            String callingPkg,
            String authority,
            String method,
            String arg,
            Bundle extras
    ) throws RemoteException {
        String pkg = callingPkg != null ? callingPkg : SHELL_PACKAGE;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                AttributionSource attributionSource = new AttributionSource.Builder(Os.getuid())
                        .setAttributionTag(attributeTag)
                        .setPackageName(pkg)
                        .build();
                return provider.call(attributionSource, authority, method, arg, extras);
            } catch (LinkageError | RuntimeException e) {
                return provider.call(pkg, attributeTag, authority, method, arg, extras);
            }
        } else if (Build.VERSION.SDK_INT == 30) {
            return provider.call(pkg, attributeTag, authority, method, arg, extras);
        } else if (Build.VERSION.SDK_INT == 29) {
            return provider.call(pkg, authority, method, arg, extras);
        } else {
            return provider.call(pkg, method, arg, extras);
        }
    }
}
