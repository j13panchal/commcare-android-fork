package org.commcare.android.tests.application;

import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.commcare.android.mocks.ModernHttpRequesterMock;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.UpdateUtils;
import org.commcare.engine.resource.AndroidResourceManager;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.models.database.AndroidSandbox;
import org.commcare.resources.model.ResourceTable;
import org.commcare.suite.model.Profile;
import org.commcare.tasks.InstallStagedUpdateTask;
import org.commcare.update.UpdateTask;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class AppUpdateTest {
    private final static String TAG = AppUpdateTest.class.getSimpleName();
    private final static String REF_BASE_DIR = "jr://resource/commcare-apps/update_tests/";

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                UpdateUtils.buildResourceRef(REF_BASE_DIR, "base_app", "profile.ccpr"),
                "test", "123");

        Profile p = CommCareApplication.instance().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 6);
    }

    @Test
    public void testAppUpdate() {
        Log.d(TAG, "Applying a valid app update");

        String profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "valid_update", "profile.ccpr");
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpdateStaged,
                AppInstallStatus.Installed);
        checkUpdateComplete(9, true);
    }

    private void checkUpdateComplete(int expectedVersion, boolean expectedResetUpgrade) {
        Profile p = CommCareApplication.instance().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == expectedVersion);

        // check that update table has been cleared
        ResourceTable upgradeTable = CommCareApplication.instance().getCommCarePlatform().getUpgradeResourceTable();
        Assert.assertTrue(upgradeTable.isEmpty() == expectedResetUpgrade);
    }

    @Test
    public void testAppIsUpToDate() {
        Log.d(TAG, "Try updating to the same app.");

        String profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "base_app", "profile.ccpr");
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpToDate,
                AppInstallStatus.UnknownFailure);
        checkUpdateComplete(6, true);
    }

    @Test
    public void testAppUpdateWithoutLocalStorage() {
        Log.d(TAG, "Try updating after removing local filesystem temp dirs.");

        // nuke local folder that CommCare uses to stage updates.
        File dir = new File(CommCareApplication.instance().getAndroidFsTemp());
        Assert.assertTrue(dir.delete());

        String profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "valid_update", "profile.ccpr");
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.NoLocalStorage,
                AppInstallStatus.UnknownFailure);
        checkUpdateComplete(6, false);
    }

    @Test
    public void testUpdateToBrokenApp() {
        Log.d(TAG, "Applying a broken app update");

        String profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "invalid_update", "profile.ccpr");
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.InvalidResource,
                AppInstallStatus.UnknownFailure);
        checkUpdateComplete(6, true);
    }

    @Test
    public void testUpdateToAppWithMultimedia() {
        Log.d(TAG, "updating to an app that has multimedia present");

        String profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "valid_update_with_multimedia_present", "profile.ccpr");
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpdateStaged,
                AppInstallStatus.Installed);
        checkUpdateComplete(14, true);
    }

    @Test
    public void testUpdateToAppMissingMultimedia() {
        Log.d(TAG, "updating to an app that has missing multimedia");

        String profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "valid_update_without_multimedia_present", "profile.ccpr");
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.MissingResourcesWithMessage,
                AppInstallStatus.UnknownFailure);
        checkUpdateComplete(6, true);
    }

    @Test
    public void testUpdateToAppWithIncompatibleVersion() {
        Log.d(TAG, "updating to an app that requires an newer CommCare version");

        String profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "invalid_version", "profile.ccpr");
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.IncompatibleReqs,
                AppInstallStatus.UnknownFailure);
        checkUpdateComplete(6, true);
    }

    @Test
    public void testValidUpdateWithNetworkFailureInBetween() {
        ModernHttpRequesterMock.setRequestPayloads(new String[]{"null","null","null","null"});
        String profileRef = UpdateUtils.buildResourceRef(
                REF_BASE_DIR,
                "update_with_a_fake_http_resource",
                "profile.ccpr");
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.NetworkFailure,
                AppInstallStatus.UnknownFailure);
        checkUpdateComplete(6, false);

        // todo check for request age

        // Retry and return a valid response this time
        String formRef = UpdateUtils.buildResourceRef(
                REF_BASE_DIR,
                "update_with_a_fake_http_resource",
                "modules-0/forms-1.xml");
        ModernHttpRequesterMock.setRequestPayloads(new String[]{formRef});
        ModernHttpRequesterMock.setResponseCodes(new Integer[]{200});
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpdateStaged,
                AppInstallStatus.Installed);
        checkUpdateComplete(9, true);
    }

    @Test
    public void testAppUpdateWithSuiteFixture() {
        Log.d(TAG, "Applying a app update with a suite fixture");

        AndroidSandbox sandbox = new AndroidSandbox(CommCareApplication.instance());
        IStorageUtilityIndexed<FormInstance> appFixtureStorage = sandbox.getAppFixtureStorage();
        assertEquals(1, appFixtureStorage.getNumRecords());
        assertEquals(1, appFixtureStorage.read(1).getRoot().getNumChildren());

        String profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "update_with_suite_fixture", "profile.ccpr");
        UpdateTask updateTask = UpdateUtils.stageUpdate(profileRef, AppInstallStatus.UpdateStaged);

        // ensure suite fixture didn't change if you only staged an update but haven't applied it
        assertEquals(1, appFixtureStorage.getNumRecords());
        assertEquals(1, appFixtureStorage.read(1).getRoot().getNumChildren());

        assertEquals(AppInstallStatus.Installed,
                InstallStagedUpdateTask.installStagedUpdate());
        updateTask.clearTaskInstance();

        // ensure suite fixture updated after actually applying a staged update
        assertEquals(1, appFixtureStorage.getNumRecords());
        assertEquals(2, appFixtureStorage.read(1).getRoot().getNumChildren());

        checkUpdateComplete(9, true);
    }
}
