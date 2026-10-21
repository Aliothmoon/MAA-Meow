package com.aliothmoon.maameow.third.wrappers;

import android.companion.AssociationInfo;
import android.os.IInterface;

import com.aliothmoon.maameow.constant.AndroidVersions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

@androidx.annotation.RequiresApi(AndroidVersions.API_33_ANDROID_13)
public final class CompanionDeviceManager {

    private final IInterface manager; // android.companion.ICompanionDeviceManager
    private Method getAssociationsMethod;

    private CompanionDeviceManager(IInterface manager) {
        this.manager = manager;
    }

    static CompanionDeviceManager create() {
        IInterface manager = ServiceManager.getService("companiondevice", "android.companion.ICompanionDeviceManager");
        return new CompanionDeviceManager(manager);
    }

    private Method getGetAssociationsMethod() throws NoSuchMethodException {
        if (getAssociationsMethod == null) {
            getAssociationsMethod = manager.getClass().getMethod("getAssociations", String.class, int.class);
        }
        return getAssociationsMethod;
    }

    @SuppressWarnings("unchecked")
    public List<AssociationInfo> getAssociations(String packageName, int userId) throws ReflectiveOperationException {
        try {
            return (List<AssociationInfo>) getGetAssociationsMethod().invoke(manager, packageName, userId);
        } catch (InvocationTargetException e) {
            throw ServiceManager.unwrap(e);
        }
    }
}
