package com.meridian.claims.job;

import com.meridian.claims.dao.DeductibleAccumulatorDAO;
import com.meridian.claims.dao.PlanDAO;
import com.meridian.claims.model.DeductibleAccumulator;
import com.meridian.claims.model.Plan;
import com.meridian.claims.service.ScheduledJobLogService;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Nightly check: verifies no accumulator row is being used past its benefit-year boundary.
 * Does NOT zero or delete accumulators — each plan year naturally gets a fresh accumulator row
 * on first claim (keyed by benefit_year_start). This job logs anomalies only.
 *
 * A new year starts when today >= plan.benefit_year_start (anniversary this calendar year).
 */
public class BenefitYearRolloverJob implements Job {

    private static final Logger LOG = Logger.getLogger(BenefitYearRolloverJob.class);

    @Autowired private PlanDAO planDAO;
    @Autowired private DeductibleAccumulatorDAO accumulatorDAO;
    @Autowired private ScheduledJobLogService jobLogService;

    @Override
    public void execute(JobExecutionContext context) {
        int logId = jobLogService.start("BenefitYearRolloverJob");
        int anomalies = 0;
        try {
            LOG.info("BenefitYearRolloverJob: starting");
            Date today = new Date();
            List<Plan> plans = planDAO.findAllActive();
            for (Plan plan : plans) {
                if (plan.getBenefitYearStart() == null) continue;
                Date currentBys = currentBenefitYearStart(plan.getBenefitYearStart(), today);
                // Check: are there accumulators for this plan keyed to a prior year?
                List<DeductibleAccumulator> accs = accumulatorDAO.findByPlanId(plan.getId());
                for (DeductibleAccumulator acc : accs) {
                    if (acc.getBenefitYearStart() != null &&
                            acc.getBenefitYearStart().before(currentBys)) {
                        LOG.warn("BenefitYearRolloverJob: plan id=" + plan.getId() +
                            " has prior-year accumulator id=" + acc.getId() +
                            " bys=" + acc.getBenefitYearStart() +
                            " — retained for audit; new claims will use bys=" + currentBys);
                        anomalies++;
                    }
                }
            }
            jobLogService.complete(logId, anomalies);
            LOG.info("BenefitYearRolloverJob: done — " + anomalies + " prior-year accumulator(s) logged");
        } catch (Exception e) {
            LOG.error("BenefitYearRolloverJob failed", e);
            jobLogService.fail(logId, e.getMessage());
        }
    }

    /** Returns the benefit_year_start anniversary for the current year, keyed to the plan's month/day. */
    private Date currentBenefitYearStart(Date templateBys, Date today) {
        Calendar bys = Calendar.getInstance();
        bys.setTime(templateBys);
        Calendar now = Calendar.getInstance();
        now.setTime(today);
        bys.set(Calendar.YEAR, now.get(Calendar.YEAR));
        // If anniversary hasn't happened yet this year, use last year
        if (bys.after(now)) {
            bys.add(Calendar.YEAR, -1);
        }
        bys.set(Calendar.HOUR_OF_DAY, 0);
        bys.set(Calendar.MINUTE, 0);
        bys.set(Calendar.SECOND, 0);
        bys.set(Calendar.MILLISECOND, 0);
        return bys.getTime();
    }
}
