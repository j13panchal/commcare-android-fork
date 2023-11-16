package org.commcare.android.database.connect.models;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;

@Table(ConnectJobDeliveryRecord.STORAGE_KEY)
public class ConnectJobDeliveryRecord extends Persisted implements Serializable {
    /**
     * Name of database that stores info for Connect deliveries
     */
    public static final String STORAGE_KEY = "connect_deliveries";

    public static final String META_JOB_ID = "job_id";
    public static final String META_ID = "id";
    public static final String META_STATUS = "status";
    public static final String META_DATE = "visit_date";
    public static final String META_UNIT_NAME = "deliver_unit_name";
    public static final String META_SLUG = "deliver_unit_slug";
    public static final String META_ENTITY_ID = "entity_id";
    public static final String META_ENTITY_NAME = "entity_name";

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;

    @Persisting(2)
    @MetaField(META_ID)
    private int deliveryId;
    @Persisting(3)
    @MetaField(META_DATE)
    private Date date;
    @Persisting(4)
    @MetaField(META_STATUS)
    private String status;
    @Persisting(5)
    @MetaField(META_UNIT_NAME)
    private String unitName;
    @Persisting(6)
    @MetaField(META_SLUG)
    private String slug;
    @Persisting(7)
    @MetaField(META_ENTITY_ID)
    private String entityId;
    @Persisting(8)
    @MetaField(META_ENTITY_NAME)
    private String entityname;
    @Persisting(9)
    private Date lastUpdate;

    public ConnectJobDeliveryRecord() {
    }

    public static ConnectJobDeliveryRecord fromJson(JSONObject json, int jobId) throws JSONException, ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        ConnectJobDeliveryRecord delivery = new ConnectJobDeliveryRecord();
        delivery.jobId = jobId;
        delivery.lastUpdate = new Date();

        delivery.deliveryId = json.getInt(META_ID);
        delivery.date = df.parse(json.getString(META_DATE));
        delivery.status = json.getString(META_STATUS);
        delivery.unitName = json.getString(META_UNIT_NAME);
        delivery.slug = json.getString(META_SLUG);
        delivery.entityId = json.getString(META_ENTITY_ID);
        delivery.entityname = json.getString(META_ENTITY_NAME);

        return delivery;
    }

    public int getDeliveryId() { return deliveryId; }
    public Date getDate() { return date; }
    public String getStatus() { return status; }
    public String getEntityName() { return entityname; }
    public void setLastUpdate(Date lastUpdate) { this.lastUpdate = lastUpdate; }
}
