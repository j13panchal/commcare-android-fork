/**
 *
 */
package org.commcare.android.database.global;

import android.content.Context;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.ConcreteDbHelper;
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.global.models.ApplicationRecordV1;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.FormsProvider;
import org.javarosa.core.services.storage.Persistable;

import java.io.File;

/**
 * @author ctsims
 *
 */
public class GlobalDatabaseUpgrader {
    private Context c;

    public GlobalDatabaseUpgrader(Context c) {
        this.c = c;
    }

    public void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i("FormsProvider", "in upgrade()");
        if (oldVersion == 1) {
            if (upgradeOneTwo(db, oldVersion, newVersion)) {
                oldVersion = 2;
            }
        }
        if (oldVersion == 2) {
            if (upgradeTwoThree(db)) {
                oldVersion = 3;
            }
        }
    }

    private boolean upgradeOneTwo(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            DbUtil.createNumbersTable(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeTwoThree(SQLiteDatabase db) {
        Log.i("FormsProvider", "in upgradeTwoThree()");
        return upgradeAppRecords(db) && upgradeFormsDb(db);
    }

    /**
     * Migrate all old ApplicationRecords in storage to the new version being used for multiple apps
     */
    private boolean upgradeAppRecords(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            SqlStorage<Persistable> storage = new SqlStorage<Persistable>(
                    ApplicationRecord.STORAGE_KEY,
                    ApplicationRecordV1.class,
                    new ConcreteDbHelper(c, db));
            for (Persistable r : storage) {
                ApplicationRecordV1 oldRecord = (ApplicationRecordV1) r;
                ApplicationRecord newRecord =
                        new ApplicationRecord(oldRecord.getApplicationId(), oldRecord.getStatus());
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                //set default values for the new fields
                newRecord.setResourcesStatus(true);
                newRecord.setArchiveStatus(false);
                newRecord.setUniqueId("");
                newRecord.setDisplayName("");
                newRecord.setVersionNumber(-1);
                newRecord.setConvertedByDbUpgrader(true);
                newRecord.setPreMultipleAppsProfile(true);
                storage.write(newRecord);
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Prior to multiple application seating, the FormsProvider used one global database for all
     * forms. Now that we can have multiple apps installed at once, we have one forms db per app.
     * This method will perform the necessary one-time migration from the global forms db to the
     * new per-app system
     */
    private boolean upgradeFormsDb(SQLiteDatabase db) {
        Log.i("FormsProvider", "in upgradeFormsDb()");
        File oldDbFile = CommCareApplication._().getDatabasePath(FormsProvider.OLD_DATABASE_NAME);
        ApplicationRecord currentApp = getInstalledAppRecord(c, db);
        if (oldDbFile.exists()) {
            Log.i("FormsProvider", "performing forms db migration");
            File newDbFile = CommCareApplication._().getDatabasePath(
                    FormsProvider.getFormsDbNameForApp(currentApp.getApplicationId()));
            if (!oldDbFile.renameTo(newDbFile)) {
                // Big problem, should probably crash here
                return false;
            } else {
                Log.i("FormsProvider", "Successfully migrated old global db file to " +
                        newDbFile.getAbsolutePath());
                return true;
            }
        }
        return false;
    }

    private static ApplicationRecord getInstalledAppRecord(Context c, SQLiteDatabase db) {
        SqlStorage<Persistable> storage = new SqlStorage<Persistable>(
                ApplicationRecord.STORAGE_KEY,
                ApplicationRecord.class,
                new ConcreteDbHelper(c, db));
        for (Persistable p : storage) {
            ApplicationRecord r = (ApplicationRecord) p;
            if (r.getStatus() == ApplicationRecord.STATUS_INSTALLED) {
                Log.i("FormsProvider", "YAY getInstalledAppRecord in GlobalDatabaseUpgrader NOT returning null");
                return r;
            }
        }
        Log.i("FormsProvider", "BAD getInstalledAppRecord in GlobalDatabaseUpgrader IS returning null");
        return null;
    }
}
