# 只锁按名查找的入口

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# JNI RegisterNatives
-keep class com.aliothmoon.maameow.bridge.NativeBridgeLib {
    native <methods>;
}

# libbridge FindClass + GetStaticMethodID
-keep class com.aliothmoon.maameow.maa.DriverClass {
    public static boolean startApp(java.lang.String, int, boolean);
    public static boolean touchDown(int, int, int);
    public static boolean touchMove(int, int, int);
    public static boolean touchUp(int, int, int);
    public static boolean keyDown(int, int);
    public static boolean keyUp(int, int);
}

# JNA：方法名即 C 符号；嵌套 Callback 整包留
-keep class com.aliothmoon.maameow.maa.** { *; }
# libjnidispatch 只 FindClass 顶层；ptr/internal/win32 交给 R8
-keep class com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.Structure { <fields>; }
-keepclassmembers class * implements com.sun.jna.Callback { <methods>; }
-dontwarn java.awt.**

# Shizuku / Root 按类名拉起
-keep class com.aliothmoon.maameow.remote.RemoteServiceImpl { <init>(); }
-keep class com.aliothmoon.maameow.remote.LogcatCaptureServiceImpl { <init>(); }
-keep class com.aliothmoon.maameow.root.RootServiceStarter {
    public static void main(java.lang.String[]);
}
-keep class com.aliothmoon.maameow.root.RootUserService { *; }
-keep class com.aliothmoon.maameow.root.RootServiceBootstrapProvider { *; }

# AIDL
-keep class com.aliothmoon.maameow.RemoteService { *; }
-keep class com.aliothmoon.maameow.RemoteService$Stub { *; }
-keep class com.aliothmoon.maameow.MaaCoreService { *; }
-keep class com.aliothmoon.maameow.MaaCoreService$Stub { *; }
-keep class com.aliothmoon.maameow.MaaCoreCallback { *; }
-keep class com.aliothmoon.maameow.MaaCoreCallback$Stub { *; }
-keep class com.aliothmoon.maameow.ILogcatService { *; }
-keep class com.aliothmoon.maameow.ILogcatService$Stub { *; }
-keep class com.aliothmoon.maameow.ITouchEventCallback { *; }
-keep class com.aliothmoon.maameow.ITouchEventCallback$Stub { *; }
-keep class com.aliothmoon.maameow.remote.PermissionGrantRequest { *; }
-keep class com.aliothmoon.maameow.remote.PermissionStateInfo { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

# SMTP：ServiceLoader / mailcap 点名的实现，imap/pop3 缺一个就炸
-keep class org.eclipse.angus.mail.smtp.** { *; }
-keep class org.eclipse.angus.mail.imap.** { *; }
-keep class org.eclipse.angus.mail.pop3.** { *; }
-keep class org.eclipse.angus.mail.handlers.** { *; }
-keep class org.eclipse.angus.mail.util.MailStreamProvider { *; }
-keep class org.eclipse.angus.activation.*RegistryProviderImpl { *; }
-dontwarn org.eclipse.angus.**
-dontwarn jakarta.**
-dontwarn javax.**

# 任务覆盖编辑器：Gson 解 language-configuration；OnigRegExp 依赖 joni 静态初始化
-keep class org.eclipse.tm4e.** { *; }
-keep class io.github.rosemoe.sora.langs.textmate.** { *; }
-keep class org.joni.** { *; }
-keep class org.jcodings.** { *; }
-dontwarn org.eclipse.jdt.annotation.**
-dontwarn org.joni.**
-dontwarn org.jcodings.**

# FakeContext / ShellContentResolver：acquireProvider 等对编译期不可见，R8 当死代码删掉
-keep class com.aliothmoon.maameow.third.FakeContext { *; }
-keep class com.aliothmoon.maameow.third.FakeContext$* { *; }
-keepclassmembers class * extends android.content.ContentResolver {
    *** acquireProvider(...);
    *** acquireUnstableProvider(...);
    *** releaseProvider(...);
    *** releaseUnstableProvider(...);
    *** unstableProviderDied(...);
}

# 落盘 Enum.name / valueOf
-keepclassmembers enum com.aliothmoon.maameow.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
