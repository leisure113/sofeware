package com.coplay.governance.api;

import com.coplay.governance.dto.HandleReportCommand;
import com.coplay.governance.dto.ReportBrief;
import com.coplay.governance.dto.ReportDetail;
import com.coplay.governance.dto.StatsOverview;
import com.coplay.governance.dto.SubmitReportCommand;

import java.util.List;

/**
 * Public use cases of the governance module.
 */
public interface GovernanceService {

    ReportDetail submit(SubmitReportCommand command);

    ReportDetail handle(HandleReportCommand command);

    /**
     * Appends supporting evidence to a PENDING report. Only the original reporter may
     * add evidence, and the combined evidence list may not exceed three items.
     */
    ReportDetail addEvidence(long reportId, long reporterId, List<String> evidence);

    List<ReportBrief> listPending();

    StatsOverview stats();
}
