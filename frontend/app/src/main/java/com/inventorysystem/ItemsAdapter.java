package com.inventorysystem;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.inventorysystem.ConnectivityandService.AuthenticatedImageUrl;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.ItemModel;
import com.inventorysystem.Model.ItemRemarkIssue;

import java.util.ArrayList;
import java.util.List;

public class ItemsAdapter extends RecyclerView.Adapter<ItemsAdapter.ItemViewHolder> {
    private List<ItemModel> fullList = new ArrayList<>();
    private List<ItemModel> filteredList = new ArrayList<>();
    private boolean remarksMode;

    public ItemsAdapter() {
        setHasStableIds(true);
    }

    public ItemModel getItemAt(int position) {
        return position >= 0 && position < filteredList.size() ? filteredList.get(position) : null;
    }

    public void setRemarksMode(boolean remarksMode) {
        this.remarksMode = remarksMode;
        notifyDataSetChanged();
    }

    // =========================
    // Set items from API
    // =========================
    public void setItems(List<ItemModel> items) {
        this.fullList = items;
        this.filteredList = new ArrayList<>(items);
        notifyDataSetChanged();
    }

    // =========================
    // Filter items
    // =========================
    public void filter(String categoryFilter, String searchQuery) {

        filteredList = new ArrayList<>();

        for (ItemModel item : fullList) {

            boolean matchesCategory =
                    categoryFilter == null
                            || categoryFilter.equalsIgnoreCase("All")
                            || (item.getCategory() != null
                            && item.getCategory().getCategoryName() != null
                            && item.getCategory()
                            .getCategoryName()
                            .equalsIgnoreCase(categoryFilter));

            boolean matchesSearch =
                    searchQuery == null
                            || searchQuery.isEmpty()
                            || (item.getItemName() != null
                            && item.getItemName()
                            .toLowerCase()
                            .contains(searchQuery.toLowerCase()));

            if (matchesCategory && matchesSearch) {
                filteredList.add(item);
            }
        }

        notifyDataSetChanged();
    }

    // =========================
    // Create ViewHolder
    // =========================
    @NonNull
    @Override
    @SuppressLint("ResourceType")
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                             int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.xml.item_row, parent, false);

        return new ItemViewHolder(view);
    }

    // =========================
    // Bind data to row
    // =========================
    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder,
                                 int position) {

        ItemModel item = filteredList.get(position);

        // Basic item info
        holder.txtItemName.setText(item.getItemName());
        holder.txtItemCode.setText(item.getItemCode());
        holder.txtItemQty.setText("Qty: " + item.getQuantity());
        holder.txtItemValue.setText(
                String.format("₱%,.2f", item.getTotalValue())
        );

        ItemRemarkIssue remarkIssue = item.getRemarkIssue();
        boolean showRemarkFields = remarksMode && remarkIssue != null;
        holder.txtIssueCode.setVisibility(showRemarkFields ? View.VISIBLE : View.GONE);
        holder.txtBrandModel.setVisibility(showRemarkFields ? View.VISIBLE : View.GONE);
        holder.txtRemarks.setVisibility(showRemarkFields ? View.VISIBLE : View.GONE);
        if (showRemarkFields) {
            holder.txtIssueCode.setText("Issue ID: " + remarkIssue.getIssueCode());
            String brand = item.getBrand() == null ? "" : item.getBrand().trim();
            String model = item.getModel() == null ? "" : item.getModel().trim();
            String brandModel = (brand + " " + model).trim();
            holder.txtBrandModel.setText(brandModel.isEmpty() ? "Brand/model not specified" : brandModel);
            holder.txtRemarks.setText("Remarks: " + remarkIssue.getRemarks());
        }

        // =========================
        // Debug image path
        // =========================
        Log.d("ITEM_IMAGE",
                "item: " + item.getItemName()
                        + " | image field: " + item.getImage());

        // ==========================
// Load Item Thumbnail
// ==========================

        String imageUrl = RetrofitClient.resolveImageUrl(item.getImage());

        Log.d("ITEM_IMAGE",
                "Item: " + item.getItemName()
                        + " | Image: " + imageUrl);

        if (imageUrl != null && !imageUrl.trim().isEmpty()) {

            Log.d("ITEM_IMAGE", "Loading: " + imageUrl);

            Glide.with(holder.itemView.getContext())
                    .load(AuthenticatedImageUrl.from(holder.itemView.getContext(), imageUrl))
                    .placeholder(R.drawable.img_placeholder)
                    .error(R.drawable.img_placeholder)
                    .fallback(R.drawable.img_placeholder)
                    .centerCrop()
                    .into(holder.imgItemThumb);

        } else {

            holder.imgItemThumb.setImageResource(R.drawable.img_placeholder);

        }

        // =========================
        // Stock status
        // =========================
        int qty = item.getQuantity();
        int reorderLevel = item.getReorderLevel();

        if (qty <= 0) {

            holder.txtStockStatus.setText("Out of Stock");
            holder.txtStockStatus.setTextColor(
                    Color.parseColor("#D32F2F")
            );
            holder.txtStockStatus.setBackgroundResource(
                    R.drawable.bg_low_stock
            );

        } else if (qty <= reorderLevel) {

            holder.txtStockStatus.setText("Low Stock");
            holder.txtStockStatus.setTextColor(
                    Color.parseColor("#D32F2F")
            );
            holder.txtStockStatus.setBackgroundResource(
                    R.drawable.bg_low_stock
            );

        } else {

            holder.txtStockStatus.setText("In Stock");
            holder.txtStockStatus.setTextColor(
                    Color.parseColor("#2E7D32")
            );
            holder.txtStockStatus.setBackgroundResource(
                    R.drawable.bg_in_stock
            );
        }

        // =========================
        // Open Item Details
        // =========================
        holder.itemView.setOnClickListener(v -> {

            Intent intent = new Intent(
                    v.getContext(),
                    ItemDetailsActivity.class
            );

            // Pass item ID to details screen
            intent.putExtra("ITEM_ID", item.getItemId());

            v.getContext().startActivity(intent);
        });

        holder.txtDisposalState.setVisibility(View.GONE);
        ItemModel.ActiveDisposal activeDisposal = item.getActiveDisposal();
        String disposalStatus = activeDisposal != null
                ? activeDisposal.getDisposalStatus() : null;
        if ("Pending Approval".equals(disposalStatus)) {
            holder.txtDisposalState.setText("Pending for Disposal");
            holder.txtDisposalState.setVisibility(View.VISIBLE);
        } else if ("For Disposal".equals(disposalStatus)) {
            holder.txtDisposalState.setText("For Disposal");
            holder.txtDisposalState.setVisibility(View.VISIBLE);
        }
    }

    // =========================
    // Item count
    // =========================
    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    @Override
    public long getItemId(int position) {
        return filteredList.get(position).getItemId();
    }

    // =========================
    // ViewHolder
    // =========================
    static class ItemViewHolder extends RecyclerView.ViewHolder {

        TextView txtItemName;
        TextView txtItemCode;
        TextView txtItemQty;
        TextView txtStockStatus;
        TextView txtItemValue;
        TextView txtDisposalState;
        TextView txtIssueCode;
        TextView txtBrandModel;
        TextView txtRemarks;

        ImageView imgItemThumb;
        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);

            txtItemName = itemView.findViewById(R.id.txtItemName);
            txtItemCode = itemView.findViewById(R.id.txtItemCode);
            txtItemQty = itemView.findViewById(R.id.txtItemQty);
            txtStockStatus = itemView.findViewById(R.id.txtStockStatus);
            txtItemValue = itemView.findViewById(R.id.txtItemValue);
            txtDisposalState = itemView.findViewById(R.id.txtDisposalState);
            txtIssueCode = itemView.findViewById(R.id.txtIssueCode);
            txtBrandModel = itemView.findViewById(R.id.txtBrandModel);
            txtRemarks = itemView.findViewById(R.id.txtRemarks);

            // Thumbnail ImageView from item_row.xml
            imgItemThumb = itemView.findViewById(R.id.imgItemThumb);
        }
    }
}
