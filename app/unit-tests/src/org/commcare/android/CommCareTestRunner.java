package org.commcare.android;

import android.os.Environment;

import org.jetbrains.annotations.NotNull;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.robolectric.DefaultTestLifecycle;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.TestLifecycle;
import org.robolectric.internal.bytecode.InstrumentationConfiguration;
import org.robolectric.shadows.ShadowEnvironment;

import javax.annotation.Nonnull;

/**
 * Register sqlcipher SQLiteDatabase to be shadowed globally.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class CommCareTestRunner extends RobolectricTestRunner {
    public CommCareTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Nonnull
    @Override
    protected InstrumentationConfiguration createClassLoaderConfig(FrameworkMethod method) {
        InstrumentationConfiguration.Builder builder = new InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method));
        builder.addInstrumentedPackage("net.sqlcipher.database.SQLiteDatabase");
        builder.addInstrumentedPackage("org.commcare.models.encryption");
        return builder.build();
    }


    @NotNull
    @Override
    protected Class<? extends TestLifecycle> getTestLifecycleClass() {
        return CommCareTestLifeCycle.class;
    }

    public static class CommCareTestLifeCycle extends DefaultTestLifecycle {
        @Override
        public void prepareTest(Object test) {
            ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
        }
    }
}