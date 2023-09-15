package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Data class for holding info relatde to a Connect job
 *
 * @author dviggiano
 */
@Table(ConnectJob.STORAGE_KEY)
public class ConnectJob extends Persisted implements Serializable {
    /**
     * Name of database that stores Connect jobs/opportunities
     */
    public static final String STORAGE_KEY = "connect_jobs";

    public static final int STATUS_AVAILABLE_NEW = 1;
    public static final int STATUS_AVAILABLE = 2;
    public static final int STATUS_LEARNING = 3;
    public static final int STATUS_DELIVERING = 4;
    public static final int STATUS_COMPLETE = 5;

    public static final String META_JOB_ID = "id";
    public static final String META_NAME = "name";
    public static final String META_DESCRIPTION = "description";
    public static final String META_DATE_CREATED = "date_created";
    public static final String META_DATE_MODIFIED = "date_modified";
    public static final String META_ORGANIZATION = "organization";
    public static final String META_END_DATE = "end_date";
    public static final String META_MAX_VISITS = "max_visits_per_user";
    public static final String META_MAX_DAILY_VISITS = "daily_max_visits_per_user";
    public static final String META_BUDGET_PER_VISIT = "budget_per_visit";
    public static final String META_BUDGET_TOTAL = "total_budget";
    public static final String META_LEARN_DEADLINE = "learn_deadline";
    public static final String META_COMPLETED_VISITS = "completed_visits";
    public static final String META_LAST_WORKED_DATE = "last_worked";
    public static final String META_STATUS = "status";

    public static final String META_LEARN_APP = "learn_app";
    public static final String META_DELIVER_APP = "deliver_app";

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;
    @Persisting(2)
    @MetaField(META_NAME)
    private String title;
    @Persisting(3)
    @MetaField(META_DESCRIPTION)
    private String description;
    @Persisting(value=4)
    @MetaField(META_LEARN_DEADLINE)
    private Date learnDeadline;
    @Persisting(value=5)
    @MetaField(META_DATE_CREATED)
    private Date beginDeadline;
    @Persisting(value=6)
    @MetaField(META_END_DATE)
    private Date projectEndDate;
    @Persisting(7)
    @MetaField(META_MAX_VISITS)
    private int maxVisits;
    @Persisting(8)
    @MetaField(META_MAX_DAILY_VISITS)
    private int maxDailyVisits;
    @Persisting(9)
    @MetaField(META_COMPLETED_VISITS)
    private int completedVisits;
    @Persisting(value=10)
    @MetaField(META_LAST_WORKED_DATE)
    private Date lastWorkedDate;
    @Persisting(11)
    @MetaField(META_STATUS)
    private int status;
    private ConnectJobLearningModule[] learningModules;
    private ConnectJobDelivery[] deliveries;
    private ConnectAppInfo learnAppInfo;
    private ConnectAppInfo deliveryAppInfo;

    public ConnectJob() {

    }

    public ConnectJob(int jobId, String title, String description, int status,
                      int completedVisits, int maxVisits, int maxDailyVisits,
                      Date learnDeadline, Date beginDeadline, Date projectEnd, Date lastWorkedDate,
                      ConnectJobLearningModule[] learningModules,
                      ConnectJobDelivery[] deliveries) {
        this.jobId = jobId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.completedVisits = completedVisits;
        this.maxDailyVisits = maxDailyVisits;
        this.maxVisits = maxVisits;
        this.learnDeadline = learnDeadline;
        this.beginDeadline = beginDeadline;
        this.projectEndDate = projectEnd;
        this.lastWorkedDate = lastWorkedDate;
        this.learningModules = learningModules;
        this.deliveries = deliveries;
    }

    public static ConnectJob fromJson(JSONObject json) throws JSONException, ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        ConnectJob job = new ConnectJob();

        job.jobId = json.has(META_JOB_ID) ? json.getInt(META_JOB_ID) : -1;
        job.title = json.has(META_NAME) ? json.getString(META_NAME) : null;
        job.description = json.has(META_DESCRIPTION) ? json.getString(META_DESCRIPTION) : null;
        job.status = STATUS_AVAILABLE_NEW;
        job.projectEndDate = json.has(META_END_DATE) ? df.parse(json.getString(META_END_DATE)) : new Date();
        job.maxVisits = json.has(META_MAX_VISITS) ? json.getInt(META_MAX_VISITS) : -1;
        job.maxDailyVisits = json.has(META_MAX_DAILY_VISITS) ? json.getInt(META_MAX_DAILY_VISITS) : -1;
        job.learningModules = new ConnectJobLearningModule[]{};
        job.deliveries = new ConnectJobDelivery[]{};

        job.learnAppInfo = ConnectAppInfo.fromJson(json.getJSONObject(META_LEARN_APP), job.jobId);
        job.deliveryAppInfo = ConnectAppInfo.fromJson(json.getJSONObject(META_DELIVER_APP), job.jobId);

        //In JSON but not in model
        //job.? = json.has(META_DATE_CREATED) ? df.parse(json.getString(META_DATE_CREATED)) : null;
        //job.? = json.has(META_DATE_MODIFIED) ? df.parse(json.getString(META_DATE_MODIFIED)) : null;
        //job.? = json.has(META_ORGANIZATION) ? df.parse(json.getString(META_ORGANIZATION)) : null;
        //job.? = json.has(META_BUDGET_PER_VISIT) ? json.getInt(META_BUDGET_PER_VISIT) : -1;
        //job.? = json.has(META_BUDGET_TOTAL) ? json.getInt(META_BUDGET_TOTAL) : -1;

        //In model but not in JSON
        job.learnDeadline = new Date();
        job.beginDeadline = new Date(); //Currently using META_DATE_CREATED
        //job.completedVisits = 0;
        job.lastWorkedDate = new Date();

        return job;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean getIsNew() { return status == STATUS_AVAILABLE_NEW; }
    public int getStatus() { return status; }
    public int getCompletedVisits() { return completedVisits; }
    public int getMaxVisits() { return maxVisits; }
    public int getMaxDailyVisits() { return maxDailyVisits; }
    public int getPercentComplete() { return maxVisits > 0 ? 100 * completedVisits / maxVisits : 0; }
    public Date getDateCompleted() { return lastWorkedDate; }
    public Date getLearnDeadline() { return learnDeadline; }
    public Date getBeginDeadline() { return beginDeadline; }
    public Date getProjectEndDate() { return projectEndDate; }
    public ConnectJobLearningModule[] getLearningModules() { return learningModules; }
    public ConnectJobDelivery[] getDeliveries() { return deliveries; }
}
