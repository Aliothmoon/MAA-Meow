package com.aliothmoon.maameow.third.wrappers

import java.lang.reflect.InvocationTargetException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerManagerClientErrorTest {

    @Test
    fun acquireProviderAbstractMethodErrorIsClientSide() {
        val cause = AbstractMethodError(
            "abstract method \"android.content.IContentProvider " +
                "android.content.ContentResolver.acquireProvider" +
                "(android.content.Context, java.lang.String)\"",
        )
        assertTrue(PowerManager.isClientSideProviderError(cause))
        assertTrue(PowerManager.isClientSideProviderError(InvocationTargetException(cause)))
    }

    @Test
    fun otherFailuresAreNotClientSide() {
        assertFalse(PowerManager.isClientSideProviderError(null))
        assertFalse(PowerManager.isClientSideProviderError(IllegalStateException("no")))
        assertFalse(PowerManager.isClientSideProviderError(InvocationTargetException(RemoteException())))
    }

    private class RemoteException : Exception()
}
