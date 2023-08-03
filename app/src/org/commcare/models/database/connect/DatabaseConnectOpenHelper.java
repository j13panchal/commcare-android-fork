package org.commcare.models.database.connect;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.database.DbUtil;
import org.commcare.modern.database.TableBuilder;

/**
 * The helper for opening/updating the Connect (encrypted) db space for CommCare.
 *
 * @author ctsims
 */
public class DatabaseConnectOpenHelper extends SQLiteOpenHelper {
        /**
         * V.2 - (change log will go here)
         */
        private static final int CONNECT_DB_VERSION = 1;

        private static final String CONNECT_DB_LOCATOR = "database_connect";

        private final Context mContext;

        public DatabaseConnectOpenHelper(Context context) {
            super(context, CONNECT_DB_LOCATOR, null, CONNECT_DB_VERSION);
            this.mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.beginTransaction();
            try {
                TableBuilder builder = new TableBuilder(ConnectUserRecord.class);
                database.execSQL(builder.getTableCreateString());

                builder = new TableBuilder(ConnectLinkedAppRecord.class);
                database.execSQL(builder.getTableCreateString());

                //TODO: Shouldn't need this, remove once verified
                //DbUtil.createNumbersTable(database);

                database.setVersion(CONNECT_DB_VERSION);

                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        @Override
        public SQLiteDatabase getWritableDatabase(String key) {
            try {
                return super.getWritableDatabase(key);
            } catch (SQLiteException sqle) {
                DbUtil.trySqlCipherDbUpdate(key, mContext, CONNECT_DB_LOCATOR);
                return super.getWritableDatabase(key);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            DataChangeLogger.log(new DataChangeLog.DbUpgradeStart("Connect", oldVersion, newVersion));
            //TODO: Create upgrader once needed
            //new GlobalDatabaseUpgrader(mContext).upgrade(db, oldVersion, newVersion);
            DataChangeLogger.log(new DataChangeLog.DbUpgradeComplete("Connect", oldVersion, newVersion));
        }
    }

