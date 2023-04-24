package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;

@Table(org.commcare.android.database.connect.models.ConnectUserRecord.STORAGE_KEY)
public class ConnectLinkedAppRecord extends Persisted {
    /**
     * Name of database that stores Connect user records
     */
    public static final String STORAGE_KEY = "app_info";

    @Persisting(1)
    private String appID;

    @Persisting(2)
    private String userID;

    @Persisting(3)
    private String password;
}
