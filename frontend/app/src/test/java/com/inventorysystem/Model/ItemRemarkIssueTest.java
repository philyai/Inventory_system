package com.inventorysystem.Model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

public class ItemRemarkIssueTest {
    private final Gson gson = new Gson();

    @Test
    public void itemResponseAcceptsNullRemarkIssue() {
        ItemResponse response = gson.fromJson(
                "{\"item_id\":87,\"item_code\":\"LAP-0005\",\"remark_issue\":null}",
                ItemResponse.class);

        assertEquals(87, response.getItemId());
        assertEquals("LAP-0005", response.getItemCode());
        assertNull(response.getRemarkIssue());
    }

    @Test
    public void itemResponseMapsPermanentRemarkIssue() {
        String json = "{\"item_id\":87,\"item_code\":\"LAP-0005\","
                + "\"remark_issue\":{\"issue_id\":1,\"issue_code\":\"ISS-000001\","
                + "\"remarks\":\"Used for accounting reports\",\"created_by\":10,"
                + "\"created_date\":\"2026-07-30T08:30:00.000Z\","
                + "\"updated_date\":\"2026-07-30T08:30:00.000Z\"}}";

        ItemResponse response = gson.fromJson(json, ItemResponse.class);
        ItemRemarkIssue issue = response.getRemarkIssue();

        assertNotNull(issue);
        assertEquals(1, issue.getIssueId());
        assertEquals("ISS-000001", issue.getIssueCode());
        assertEquals("Used for accounting reports", issue.getRemarks());
        assertEquals(10, issue.getCreatedBy());
        assertNotNull(issue.getCreatedDate());
        assertNotNull(issue.getUpdatedDate());
    }

    @Test
    public void itemModelMapsRemarkIssueFromGetItems() {
        ItemModel item = gson.fromJson(
                "{\"item_id\":87,\"remark_issue\":{\"issue_id\":1,"
                        + "\"issue_code\":\"ISS-000001\",\"remarks\":\"Accounting\"}}",
                ItemModel.class);

        assertNotNull(item.getRemarkIssue());
        assertEquals("ISS-000001", item.getRemarkIssue().getIssueCode());
    }

    @Test
    public void offlineRequestIncludesRemarksAndOmitsNullRemarks() {
        OfflineItemRequest request = new OfflineItemRequest();
        request.itemName = "Laptop";
        request.remarks = "Used for accounting reports";

        JsonObject withRemarks = JsonParser.parseString(gson.toJson(request)).getAsJsonObject();
        assertEquals("Used for accounting reports", withRemarks.get("remarks").getAsString());

        request.remarks = null;
        JsonObject withoutRemarks = JsonParser.parseString(gson.toJson(request)).getAsJsonObject();
        assertFalse(withoutRemarks.has("remarks"));
    }
}