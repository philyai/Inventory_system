package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response envelope returned by GET reports/disposal.
 */
public class DisposalReportResponse {

    @SerializedName("disposals")
    private List<DisposalRequestModel> disposals;

    public List<DisposalRequestModel> getDisposals() {
        return disposals;
    }
}
