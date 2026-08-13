package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

public class ItemRemarkIssue {
    @SerializedName("issue_id")
    private int issueId;

    @SerializedName("issue_code")
    private String issueCode;

    @SerializedName("remarks")
    private String remarks;

    @SerializedName("created_by")
    private int createdBy;

    @SerializedName("created_date")
    private Date createdDate;

    @SerializedName("updated_date")
    private Date updatedDate;

    public int getIssueId() { return issueId; }
    public String getIssueCode() { return issueCode; }
    public String getRemarks() { return remarks; }
    public int getCreatedBy() { return createdBy; }
    public Date getCreatedDate() { return createdDate; }
    public Date getUpdatedDate() { return updatedDate; }
}
