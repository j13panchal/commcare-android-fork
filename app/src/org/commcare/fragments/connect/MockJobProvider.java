package org.commcare.fragments.connect;

import java.util.Date;

/**
 * Temporary class for creating mock ConnectJobs
 *
 * @author dviggiano
 */
public class MockJobProvider {
    private static final ConnectJob[] mockAvailableJobs = {
            new ConnectJob("Infant Vaccine Check",
                    "You will conduct 100 home visits to assess if children below 2 years are up to date with their vaccine shots.",
                    true,
                    0, 100, 5,
                    new Date("8/27/2023"),
                    new Date("9/10/2023"),
                    new Date("11/1/2023"),
                    null,
                    new ConnectJobLearningModule[] {
                            new ConnectJobLearningModule("How to check the vaccine booklet of a child in the household", 2, new Date("8/10/2023")),
                            new ConnectJobLearningModule("How to help the caregiver in the household get the next vaccine shot for their child", 2, null),
            }),
            new ConnectJob("Vitamin A Delivery",
                    "You will deliver Vitamin A supplements to 50 homes.",
                    false,
                    0,100, 5,
                    new Date("9/15/2023"),
                    new Date("10/1/2023"),
                    new Date("11/1/2023"),
                    null,
                    new ConnectJobLearningModule[] {
                            new ConnectJobLearningModule("Sample learning module", 2, null),
                    }),
    };

    private static final ConnectJob[] mockClaimedJobs = {
            new ConnectJob("Mental Health Visits",
                    "",
                    false,
                    60,100, 5,
                    new Date("9/7/2023"),
                    new Date("9/10/2023"),
                    new Date("11/1/2023"),
                    null,
                    new ConnectJobLearningModule[]{}),
            new ConnectJob("TestA",
                    "",
                    false,
                    0,100, 5,
                    new Date("9/7/2023"),
                    new Date("9/10/2023"),
                    new Date("11/1/2023"),
                    null,
                    new ConnectJobLearningModule[]{}),
            new ConnectJob("Infant Health Check",
                    "",
                    false,
                    100,100, 5,
                    new Date("1/7/2023"),
                    new Date("5/10/2023"),
                    new Date("10/1/2023"),
                    new Date("4/7/2023"),
                    new ConnectJobLearningModule[]{}),
    };

    public static ConnectJob[] getAvailableJobs() {
        return mockAvailableJobs;
    }

    public static ConnectJob[] getTrainingJobs() {
        return mockAvailableJobs;
    }

    public static ConnectJob[] getClaimedJobs() {
        return mockClaimedJobs;
    }
}
