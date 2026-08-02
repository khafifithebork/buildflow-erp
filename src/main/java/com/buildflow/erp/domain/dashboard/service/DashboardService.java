package com.buildflow.erp.domain.dashboard.service;

import com.buildflow.erp.domain.dashboard.dto.response.DashboardKpisResponse;

public interface DashboardService {

    /**
     * @param month optional "YYYY-MM" filter for the period-scoped (flow) KPIs.
     *              Null/blank aggregates flows over all time. Balance KPIs
     *              (debts, stock value, attachements en cours) are always
     *              as-of-now regardless of this filter.
     */
    DashboardKpisResponse getKpis(String month);
}
