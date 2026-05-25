package dev.zed.mobile;

import android.app.NativeActivity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;

/**
 * NativeActivity subclass that explicitly System.loadLibrary()s the Rust
 * shared library.
 *
 * NativeActivity itself dlopens the .so to find ANativeActivity_onCreate,
 * but that dlopen does NOT register the library with the JVM classloader.
 * As a result, JNI calls from Rust into Java cannot resolve symbols defined
 * in the .so. Calling System.loadLibrary() here fixes that.
 *
 * The library name is read from the AndroidManifest meta-data so it stays
 * in sync with the `nativeLibraryName` placeholder in build.gradle.kts.
 */
public class MainActivity extends NativeActivity {

    private static final String TAG = "ZedMobile";
    private static volatile boolean sNativeLibLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!sNativeLibLoaded) {
            try {
                ActivityInfo ai = getPackageManager().getActivityInfo(
                        getComponentName(), PackageManager.GET_META_DATA);
                String libName = ai.metaData.getString("android.app.lib_name");
                if (libName != null) {
                    try {
                        System.loadLibrary(libName);
                        sNativeLibLoaded = true;
                    } catch (UnsatisfiedLinkError e) {
                        // System.loadLibrary is called before
                        // super.onCreate(), so NativeActivity hasn't
                        // attempted to load anything yet. A failure here
                        // means the .so genuinely can't be found.
                        Log.w(TAG, "System.loadLibrary(" + libName + ") failed: "
                                + e.getMessage());
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Failed to resolve activity metadata", e);
            }
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        super.onCreate(savedInstanceState);
    }
}
