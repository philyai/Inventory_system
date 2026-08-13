package com.inventorysystem;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.inventorysystem.offline.CachedCategoryEntity;
import com.inventorysystem.offline.CachedLocationEntity;
import com.inventorysystem.offline.InventoryDatabase;
import com.inventorysystem.offline.OfflineItemSyncScheduler;
import com.inventorysystem.offline.PendingImageStore;
import com.inventorysystem.offline.PendingItemEntity;
import java.util.ArrayList;
import java.util.List;

public class PendingSyncActivity extends AppCompatActivity {
    private final List<PendingItemEntity> items = new ArrayList<>();
    private PendingAdapter adapter;
    private int userId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_pending_sync);
        userId = new SessionManager(this).getUserId();
        findViewById(R.id.btnPendingBack).setOnClickListener(v -> finish());
        RecyclerView list=findViewById(R.id.pendingList); list.setLayoutManager(new LinearLayoutManager(this));
        adapter=new PendingAdapter(); list.setAdapter(adapter);
        if(userId>0) InventoryDatabase.get(this).offlineDao().observeItems(userId).observe(this, values -> {
            items.clear(); if(values!=null) items.addAll(values); adapter.notifyDataSetChanged();
            findViewById(R.id.txtPendingEmpty).setVisibility(items.isEmpty()?View.VISIBLE:View.GONE);
        });
    }

    private void retry(PendingItemEntity item) {
        InventoryDatabase.IO.execute(() -> {
            item.syncStatus="PENDING";
            item.lastError=null;
            item.retryCount=0;
            item.retryAfterAt=null;
            InventoryDatabase.get(this).offlineDao().updatePending(item);
            OfflineItemSyncScheduler.retryNow(this);
        });
    }

    private void confirmDelete(PendingItemEntity item) {
        new AlertDialog.Builder(this).setTitle("Delete queued item?")
                .setMessage("This removes the local item and its copied image. This cannot be undone.")
                .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w) -> InventoryDatabase.IO.execute(() -> {
                    InventoryDatabase.get(this).offlineDao().deleteById(item.localId,userId);
                    PendingImageStore.delete(item.localImagePath);
                })).show();
    }

    private EditText field(LinearLayout box, String hint, String value, int type) {
        TextView label=new TextView(this); label.setText(hint+":");
        label.setTypeface(label.getTypeface(),android.graphics.Typeface.BOLD);
        int topMargin=(int)(12*getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin=topMargin; box.addView(label,labelParams);
        EditText input=new EditText(this); input.setHint(hint); input.setInputType(type); input.setText(value==null?"":value); box.addView(input); return input;
    }

    private EditText selectionField(LinearLayout box, String label, String value) {
        EditText input=field(box,label,value,InputType.TYPE_NULL);
        input.setFocusable(false); input.setCursorVisible(false); input.setClickable(true);
        return input;
    }

    private void edit(PendingItemEntity item) {
        InventoryDatabase.IO.execute(() -> {
            List<CachedCategoryEntity> categories=InventoryDatabase.get(this).offlineDao().getCategories();
            List<CachedLocationEntity> locations=InventoryDatabase.get(this).offlineDao().getLocations();
            runOnUiThread(() -> showEditDialog(item,categories,locations));
        });
    }

    private void showEditDialog(PendingItemEntity item,List<CachedCategoryEntity> categories,
                                List<CachedLocationEntity> locations) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int p=(int)(16*getResources().getDisplayMetrics().density); box.setPadding(p,0,p,0);
        EditText name=field(box,"Item name",item.itemName,InputType.TYPE_CLASS_TEXT);
        EditText brand=field(box,"Brand",item.brand,InputType.TYPE_CLASS_TEXT);
        EditText model=field(box,"Model",item.model,InputType.TYPE_CLASS_TEXT);
        EditText serial=field(box,"Serial number",item.serialNumber,InputType.TYPE_CLASS_TEXT);
        Integer[] selectedCategoryId={item.categoryId};
        int[] selectedLocationId={item.locationId};
        EditText category=selectionField(box,"Category",categoryLabel(item.categoryId,categories));
        EditText categoryName=field(box,"Others category name",item.categoryName,InputType.TYPE_CLASS_TEXT);
        categoryName.setVisibility(item.categoryId==null?View.VISIBLE:View.GONE);
        category.setOnClickListener(v -> showCategoryPicker(categories,selectedCategoryId,
                category,categoryName));
        EditText location=selectionField(box,"Location",locationLabel(item.locationId,locations));
        location.setOnClickListener(v -> showLocationPicker(locations,selectedLocationId,location));
        EditText quantity=field(box,"Quantity",String.valueOf(item.quantity),InputType.TYPE_CLASS_NUMBER);
        EditText reorder=field(box,"Reorder level",String.valueOf(item.reorderLevel),InputType.TYPE_CLASS_NUMBER);
        EditText cost=field(box,"Unit cost",item.unitCost,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText remarks=field(box,"Optional remarks",item.remarks,InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        remarks.setMinLines(3); remarks.setMaxLines(5);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("Edit queued item").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            try {
                if(name.getText().toString().trim().isEmpty()) throw new IllegalArgumentException("Item name is required");
                String remarksText=remarks.getText().toString().trim();
                if(remarksText.length()>500){Toast.makeText(this,"Remarks must not exceed 500 characters.",Toast.LENGTH_LONG).show();return;}
                String custom=categoryName.getText().toString().trim();
                if(selectedCategoryId[0]==null && custom.isEmpty()) throw new IllegalArgumentException("Select a category or enter an Others name");
                item.itemName=name.getText().toString().trim(); item.brand=blank(brand); item.model=blank(model); item.serialNumber=blank(serial);
                item.categoryId=selectedCategoryId[0]; item.categoryName=selectedCategoryId[0]==null?custom:null;
                item.locationId=selectedLocationId[0]; item.quantity=Integer.parseInt(quantity.getText().toString());
                item.reorderLevel=Integer.parseInt(reorder.getText().toString()); item.unitCost=cost.getText().toString().trim();
                item.remarks=remarksText.isEmpty()?null:remarksText;
                item.syncStatus="PENDING"; item.lastError=null; item.retryCount=0; item.retryAfterAt=null;
                InventoryDatabase.IO.execute(() -> {InventoryDatabase.get(this).offlineDao().updatePending(item); OfflineItemSyncScheduler.retryNow(this);});
            } catch(Exception e){Toast.makeText(this,"Invalid values: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }).show();
    }

    private String categoryLabel(Integer categoryId,List<CachedCategoryEntity> categories) {
        if(categoryId==null) return "Others";
        for(CachedCategoryEntity category:categories) if(category.categoryId==categoryId)
            return category.categoryId+" - "+category.categoryName;
        return String.valueOf(categoryId);
    }

    private String locationLabel(int locationId,List<CachedLocationEntity> locations) {
        for(CachedLocationEntity location:locations) if(location.locationId==locationId)
            return location.locationId+" - "+location.locationName;
        return String.valueOf(locationId);
    }

    private void showCategoryPicker(List<CachedCategoryEntity> categories,
                                    Integer[] selectedCategoryId,EditText categoryField,
                                    EditText categoryNameField) {
        String[] choices=new String[categories.size()+1];
        for(int i=0;i<categories.size();i++) choices[i]=categories.get(i).categoryId+" - "+categories.get(i).categoryName;
        choices[categories.size()]="Others";
        new AlertDialog.Builder(this).setTitle("Select Category").setItems(choices,(dialog,which)->{
            if(which==categories.size()) {
                selectedCategoryId[0]=null; categoryField.setText("Others");
                categoryNameField.setVisibility(View.VISIBLE); categoryNameField.requestFocus();
            } else {
                CachedCategoryEntity selected=categories.get(which);
                selectedCategoryId[0]=selected.categoryId; categoryField.setText(choices[which]);
                categoryNameField.setText(""); categoryNameField.setVisibility(View.GONE);
            }
        }).show();
    }

    private void showLocationPicker(List<CachedLocationEntity> locations,
                                    int[] selectedLocationId,EditText locationField) {
        if(locations.isEmpty()) {
            Toast.makeText(this,"No cached locations are available.",Toast.LENGTH_LONG).show();
            return;
        }
        String[] choices=new String[locations.size()];
        for(int i=0;i<locations.size();i++) choices[i]=locations.get(i).locationId+" - "+locations.get(i).locationName;
        new AlertDialog.Builder(this).setTitle("Select Location").setItems(choices,(dialog,which)->{
            CachedLocationEntity selected=locations.get(which);
            selectedLocationId[0]=selected.locationId; locationField.setText(choices[which]);
        }).show();
    }
    private String blank(EditText value){String text=value.getText().toString().trim();return text.isEmpty()?null:text;}

    private class PendingAdapter extends RecyclerView.Adapter<PendingAdapter.Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(getLayoutInflater().inflate(R.layout.item_pending_sync,parent,false));}
        @Override public void onBindViewHolder(@NonNull Holder h,int position){
            PendingItemEntity item=items.get(position);
            h.name.setText(item.itemName);
            h.status.setText(item.syncStatus+" - "+(item.lastError==null?"Waiting to synchronize":item.lastError));
            boolean hasRemarks=item.remarks!=null&&!item.remarks.trim().isEmpty();
            h.issue.setVisibility(hasRemarks?View.VISIBLE:View.GONE);
            h.remarks.setVisibility(hasRemarks?View.VISIBLE:View.GONE);
            if(hasRemarks){
                h.issue.setText("Issue ID: "+(item.issueCode==null?"Assigned after synchronization":item.issueCode));
                h.remarks.setText("Optional Remarks: "+item.remarks);
            }
            h.retry.setOnClickListener(v->retry(item));h.edit.setOnClickListener(v->edit(item));h.delete.setOnClickListener(v->confirmDelete(item));
        }
        @Override public int getItemCount(){return items.size();}
        class Holder extends RecyclerView.ViewHolder {TextView name,status,issue,remarks;MaterialButton edit,retry,delete;Holder(View v){super(v);name=v.findViewById(R.id.txtPendingName);status=v.findViewById(R.id.txtPendingStatus);issue=v.findViewById(R.id.txtPendingIssue);remarks=v.findViewById(R.id.txtPendingRemarks);edit=v.findViewById(R.id.btnPendingEdit);retry=v.findViewById(R.id.btnPendingRetry);delete=v.findViewById(R.id.btnPendingDelete);}}
    }
}
