package org.commcare.models.database.connect;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.modern.database.TableBuilder;
import org.javarosa.core.services.storage.Persistable;

public class ConnectDatabaseUpgrader {
    private final Context c;

    public ConnectDatabaseUpgrader(Context c) {
        this.c = c;
    }

    public void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) {
            if (upgradeOneTwo(db)) {
                oldVersion = 2;
            }
        }
    }

    private boolean upgradeOneTwo(SQLiteDatabase db) {
        return addTableForNewModel(db, ConnectJobRecord.STORAGE_KEY, new ConnectJobRecord()) &&
                addTableForNewModel(db, ConnectAppRecord.STORAGE_KEY, new ConnectAppRecord()) &&
                addTableForNewModel(db, ConnectLearnModuleSummaryRecord.STORAGE_KEY, new ConnectLearnModuleSummaryRecord());
    }

    private static boolean addTableForNewModel(SQLiteDatabase db, String storageKey,
                                               Persistable modelToAdd) {
        db.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(storageKey);
            builder.addData(modelToAdd);
            db.execSQL(builder.getTableCreateString());

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }
}