package com.sacco.dashboard.dto;

import com.sacco.account.dto.AccountResponse;
import com.sacco.audit.dto.AuditEventResponse;
import com.sacco.member.dto.MemberResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardSummaryResponse {
    private long totalMembers;
    private long activeMembers;
    private long pendingMembers;
    private long suspendedMembers;

    private long totalAccounts;
    private long activeAccounts;
    private long inactiveAccounts;
    private Map<String, Long> accountStatusBreakdown;

    private BigDecimal totalSavings;
    private BigDecimal totalShareCapital;

    private BigDecimal totalLoansOutstanding;
    private long activeLoans;
    private long loansInArrears;

    private long collateralHeldCount;
    private BigDecimal collateralHeldValue;

    private List<MonthlySavingsPoint> monthlySavings;
    private List<AuditEventResponse> recentActivities;
    private List<MemberResponse> recentMembers;
    private List<AccountResponse> recentAccounts;
}
